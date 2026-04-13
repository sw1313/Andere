package com.andere.android.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wallpaper_records")
data class WallpaperRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postId: Long,
    val previewUrl: String?,
    val createdAtMillis: Long,
    val target: String,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val updatedAtMillis: Long,
)

@Dao
interface WallpaperRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WallpaperRecordEntity): Long

    @Query("SELECT * FROM wallpaper_records ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<WallpaperRecordEntity>

    @Query("SELECT * FROM wallpaper_records ORDER BY createdAtMillis DESC LIMIT :limit")
    fun latestFlow(limit: Int): Flow<List<WallpaperRecordEntity>>

    @Query("SELECT * FROM wallpaper_records WHERE target = :target ORDER BY createdAtMillis DESC, id DESC LIMIT :limit")
    fun latestByTargetFlow(target: String, limit: Int): Flow<List<WallpaperRecordEntity>>

    @Query("SELECT * FROM wallpaper_records WHERE target = :target ORDER BY createdAtMillis DESC, id DESC LIMIT :limit")
    suspend fun latestByTarget(target: String, limit: Int): List<WallpaperRecordEntity>

    @Query("SELECT postId FROM wallpaper_records WHERE createdAtMillis >= :sinceMillis")
    suspend fun postIdsSince(sinceMillis: Long): List<Long>

    @Query("SELECT postId FROM wallpaper_records WHERE target = :target AND createdAtMillis >= :sinceMillis")
    suspend fun postIdsSinceByTarget(target: String, sinceMillis: Long): List<Long>

    @Query("DELETE FROM wallpaper_records WHERE createdAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)

    @Query("DELETE FROM wallpaper_records")
    suspend fun deleteAll()

    @Query("DELETE FROM wallpaper_records WHERE target = :target")
    suspend fun deleteByTarget(target: String)
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SearchHistoryEntity): Long

    @Query("SELECT * FROM search_history ORDER BY updatedAtMillis DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SearchHistoryEntity>
}

@Database(
    entities = [WallpaperRecordEntity::class, SearchHistoryEntity::class, FavoritePostEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wallpaperRecordDao(): WallpaperRecordDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun favoritePostDao(): FavoritePostDao

    companion object {
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorite_posts (
                        postId INTEGER NOT NULL PRIMARY KEY,
                        previewUrl TEXT,
                        tags TEXT NOT NULL,
                        author TEXT NOT NULL,
                        createdAtEpochSeconds INTEGER NOT NULL,
                        savedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE favorite_posts ADD COLUMN width INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE favorite_posts ADD COLUMN height INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        fun build(context: Context): AppDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "prpr_android.db",
        ).addMigrations(migration1To2, migration2To3).build()
    }
}
