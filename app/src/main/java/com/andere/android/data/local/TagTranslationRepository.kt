package com.andere.android.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

data class TagEntry(
    val name: String,
    val type: Int,
    val zhName: String,
)

/**
 * Stores 180K+ tag translations using parallel arrays + binary cache
 * to minimise heap overhead and startup parse time.
 */
class TagTranslationRepository(private val context: Context) {
    private val mutex = Mutex()

    private var names: Array<String>? = null
    private var types: IntArray? = null
    private var zhNames: Array<String>? = null

    private var nameToIdx: HashMap<String, Int>? = null
    private var sortedIndices: IntArray? = null

    @Volatile private var zhCharIdx: HashMap<Char, IntArray>? = null

    private var userOverrides: MutableMap<String, TagEntry> = mutableMapOf()

    private val userFile: File get() = File(context.filesDir, USER_OVERRIDES_FILE)
    private val syncedFile: File get() = File(context.filesDir, SYNCED_FILE)
    private val cacheFile: File get() = File(context.filesDir, CACHE_FILE)

    private suspend fun ensureLoaded() {
        mutex.withLock {
            if (names != null) return@withLock
            withContext(Dispatchers.IO) {
                val cached = loadBinaryCache()
                if (cached) {
                    buildLookupIndex()
                } else {
                    val synced = loadSyncedFromDisk()
                    val entries = synced ?: loadBundledFromAssets()
                    populateFromEntries(entries)
                    saveBinaryCache()
                }
                userOverrides = loadUserOverridesFromDisk()
            }
        }
    }

    private fun populateFromEntries(entries: List<TagEntry>) {
        val size = entries.size
        val n = Array(size) { entries[it].name }
        val t = IntArray(size) { entries[it].type }
        val z = Array(size) { entries[it].zhName }
        names = n; types = t; zhNames = z
        buildLookupIndex()
    }

    private fun buildLookupIndex() {
        val n = names ?: return
        val size = n.size
        val idx = HashMap<String, Int>(size * 4 / 3 + 1)
        for (i in 0 until size) idx[n[i]] = i
        nameToIdx = idx

        val sorted = IntArray(size) { it }
        val comparator = Comparator<Int> { a, b -> n[a].compareTo(n[b]) }
        sortedIndices = sorted.also { arr ->
            val boxed = arr.toTypedArray()
            boxed.sortWith(comparator)
            boxed.forEachIndexed { i, v -> arr[i] = v }
        }
    }

    private fun ensureZhIndex() {
        if (zhCharIdx != null) return
        val z = zhNames ?: return
        val temp = HashMap<Char, MutableList<Int>>(4096)
        for (i in z.indices) {
            for (ch in z[i]) {
                if (ch.code > 0x2E7F) {
                    temp.getOrPut(ch) { mutableListOf() }.add(i)
                }
            }
        }
        val compact = HashMap<Char, IntArray>(temp.size)
        for ((ch, list) in temp) compact[ch] = list.toIntArray()
        zhCharIdx = compact
    }

    private fun loadBinaryCache(): Boolean {
        val f = cacheFile
        if (!f.exists()) return false
        return try {
            DataInputStream(BufferedInputStream(f.inputStream(), 1 shl 16)).use { dis ->
                if (dis.readInt() != CACHE_VERSION) return false
                val size = dis.readInt()
                val n = Array(size) { dis.readUTF() }
                val t = IntArray(size)
                for (i in 0 until size) t[i] = dis.readByte().toInt()
                val z = Array(size) { dis.readUTF() }
                names = n; types = t; zhNames = z
            }
            true
        } catch (_: Exception) {
            f.delete(); false
        }
    }

    private fun saveBinaryCache() {
        val n = names ?: return
        val t = types ?: return
        val z = zhNames ?: return
        try {
            DataOutputStream(cacheFile.outputStream().buffered(1 shl 16)).use { dos ->
                dos.writeInt(CACHE_VERSION)
                dos.writeInt(n.size)
                for (i in n.indices) dos.writeUTF(n[i])
                for (i in t.indices) dos.writeByte(t[i])
                for (i in z.indices) dos.writeUTF(z[i])
            }
        } catch (_: Exception) {
            cacheFile.delete()
        }
    }

    private fun loadBundledFromAssets(): List<TagEntry> =
        context.assets.open("tag_translations.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.split('\t', limit = 3)
                if (parts.size >= 3) TagEntry(parts[0], parts[1].toIntOrNull() ?: 0, parts[2])
                else null
            }.toList()
        }

    private fun loadSyncedFromDisk(): List<TagEntry>? {
        val f = syncedFile
        if (!f.exists()) return null
        return f.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split('\t', limit = 3)
                if (parts.size >= 3) TagEntry(parts[0], parts[1].toIntOrNull() ?: 0, parts[2])
                else null
            }.toList()
        }
    }

    private fun loadUserOverridesFromDisk(): MutableMap<String, TagEntry> {
        val f = userFile
        if (!f.exists()) return mutableMapOf()
        return f.readLines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split('\t', limit = 3)
            if (parts.size >= 3) TagEntry(parts[0].trim(), parts[1].toIntOrNull() ?: 0, parts[2])
            else null
        }.associateByTo(mutableMapOf()) { it.name }
    }

    private fun entryAt(i: Int): TagEntry =
        TagEntry(names!![i], types!![i], zhNames!![i])

    suspend fun lookup(tagName: String): TagEntry? {
        ensureLoaded()
        userOverrides[tagName]?.let { return it }
        val i = nameToIdx?.get(tagName) ?: return null
        return entryAt(i)
    }

    suspend fun search(keyword: String, limit: Int = 20): List<TagEntry> {
        ensureLoaded()
        if (keyword.isBlank()) return emptyList()
        return if (keyword.all { it.code < 128 }) searchEnglish(keyword.lowercase(), limit)
        else searchChinese(keyword, limit)
    }

    private fun searchEnglish(lower: String, limit: Int): List<TagEntry> {
        val result = mutableListOf<TagEntry>()
        val seen = HashSet<String>()
        for (e in userOverrides.values) {
            if (result.size >= limit) return result
            if (e.name.contains(lower, ignoreCase = true)) { result.add(e); seen.add(e.name) }
        }
        val n = names ?: return result
        val si = sortedIndices ?: return result

        var lo = 0; var hi = si.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (n[si[mid]] < lower) lo = mid + 1 else hi = mid
        }
        var pos = lo
        while (pos < si.size && result.size < limit) {
            val i = si[pos]
            val name = n[i]
            if (!name.startsWith(lower)) break
            if (name !in seen) { result.add(entryAt(i)); seen.add(name) }
            pos++
        }

        if (result.size < limit) {
            for (idx in si) {
                if (result.size >= limit) break
                val name = n[idx]
                if (name in seen) continue
                if (name.contains(lower)) { result.add(entryAt(idx)); seen.add(name) }
            }
        }
        return result
    }

    private fun searchChinese(keyword: String, limit: Int): List<TagEntry> {
        val result = mutableListOf<TagEntry>()
        val seen = HashSet<String>()
        for (e in userOverrides.values) {
            if (result.size >= limit) return result
            if (e.zhName.contains(keyword)) { result.add(e); seen.add(e.name) }
        }

        ensureZhIndex()
        val n = names ?: return result
        val z = zhNames ?: return result
        val idx = zhCharIdx ?: return result

        val cjkChars = keyword.filter { it.code > 0x2E7F }
        if (cjkChars.isEmpty()) return result

        val lists = cjkChars.map { ch -> idx[ch] ?: return result }
        val smallest = lists.minBy { it.size }
        val otherChars = cjkChars.filterIndexed { i, _ -> lists[i] !== smallest }

        for (arrIdx in smallest) {
            if (result.size >= limit) break
            val name = n[arrIdx]
            if (name in seen) continue
            val zh = z[arrIdx]
            if (otherChars.isNotEmpty() && otherChars.any { it !in zh }) continue
            if (zh.contains(keyword)) { result.add(entryAt(arrIdx)); seen.add(name) }
        }
        return result
    }

    suspend fun saveUserOverride(tagName: String, zhName: String) {
        val cleanTag = tagName.trim()
        val cleanZh = zhName.trim().replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
        if (cleanTag.isEmpty()) return
        ensureLoaded()
        mutex.withLock {
            val type = userOverrides[cleanTag]?.type ?: nameToIdx?.get(cleanTag)?.let { types!![it] } ?: 0
            userOverrides[cleanTag] = TagEntry(cleanTag, type, cleanZh)
            withContext(Dispatchers.IO) {
                val sb = StringBuilder(userOverrides.size * 40)
                for (e in userOverrides.values) sb.append(e.name).append('\t').append(e.type).append('\t').append(e.zhName).append('\n')
                userFile.writeText(sb.toString())
            }
        }
    }

    suspend fun replaceAllBundled(newEntries: List<TagEntry>) {
        mutex.withLock {
            populateFromEntries(newEntries)
            zhCharIdx = null
            withContext(Dispatchers.IO) {
                saveBinaryCache()
                val sb = StringBuilder(newEntries.size * 40)
                for (e in newEntries) sb.append(e.name).append('\t').append(e.type).append('\t').append(e.zhName).append('\n')
                syncedFile.writeText(sb.toString())
            }
        }
    }

    suspend fun clearUserOverrides() {
        mutex.withLock {
            userOverrides.clear()
            withContext(Dispatchers.IO) { if (userFile.exists()) userFile.delete() }
        }
    }

    suspend fun exportAll(): List<TagEntry> {
        ensureLoaded()
        return mutex.withLock {
            val n = names ?: return@withLock emptyList()
            val t = types ?: return@withLock emptyList()
            val z = zhNames ?: return@withLock emptyList()
            val merged = LinkedHashMap<String, TagEntry>(n.size + userOverrides.size)
            for (i in n.indices) merged[n[i]] = TagEntry(n[i], t[i], z[i])
            for ((k, v) in userOverrides) merged[k] = v
            merged.values.toList()
        }
    }

    companion object {
        private const val USER_OVERRIDES_FILE = "tag_user_overrides.tsv"
        private const val SYNCED_FILE = "tag_translations_synced.tsv"
        private const val CACHE_FILE = "tag_cache.bin"
        private const val CACHE_VERSION = 1
    }
}
