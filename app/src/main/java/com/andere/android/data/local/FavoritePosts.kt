package com.andere.android.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "favorite_posts")
data class FavoritePostEntity(
    @PrimaryKey val postId: Long,
    val previewUrl: String?,
    val width: Int,
    val height: Int,
    val tags: String,
    val author: String,
    val createdAtEpochSeconds: Long,
    val savedAtMillis: Long,
)

@Dao
interface FavoritePostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FavoritePostEntity): Long

    @Query("DELETE FROM favorite_posts WHERE postId = :postId")
    suspend fun deleteByPostId(postId: Long)

    @Query("SELECT * FROM favorite_posts WHERE postId = :postId LIMIT 1")
    suspend fun findByPostId(postId: Long): FavoritePostEntity?

    @Query("SELECT * FROM favorite_posts ORDER BY savedAtMillis DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<FavoritePostEntity>
}
