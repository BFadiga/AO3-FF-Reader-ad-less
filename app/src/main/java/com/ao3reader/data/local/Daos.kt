package com.ao3reader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.ao3reader.data.model.Site
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library WHERE followed = 1 OR downloadedChapters > 0 OR liked = 1 ORDER BY newChapters > 0 DESC, addedAt DESC")
    fun observeAll(): Flow<List<LibraryWork>>

    @Query("SELECT * FROM library WHERE id = :id")
    fun observe(id: Long): Flow<LibraryWork?>

    @Query("SELECT * FROM library WHERE id = :id")
    suspend fun get(id: Long): LibraryWork?

    @Query("SELECT * FROM library WHERE followed = 1")
    suspend fun followed(): List<LibraryWork>

    @Query("SELECT * FROM library")
    suspend fun all(): List<LibraryWork>

    @Upsert
    suspend fun upsert(work: LibraryWork)

    @Query("DELETE FROM library WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE library SET lastReadChapter = :chapter, lastReadProgress = :progress, newChapters = 0 WHERE id = :id")
    suspend fun saveProgress(id: Long, chapter: Int, progress: Float)

    @Query("UPDATE library SET downloadedChapters = 0, downloadedAt = NULL")
    suspend fun clearAllDownloads()

    @Query("DELETE FROM library WHERE followed = 0 AND downloadedChapters = 0 AND liked = 0 AND lastReadChapter = 0")
    suspend fun pruneOrphans()
}

@Dao
interface FavoriteTagDao {
    @Query("SELECT * FROM favorite_tags ORDER BY section, name COLLATE NOCASE")
    fun observeAll(): Flow<List<FavoriteTag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: FavoriteTag)

    @Query("DELETE FROM favorite_tags WHERE site = :site AND name = :name")
    suspend fun delete(site: Site, name: String)

    @Query("SELECT COUNT(*) FROM favorite_tags WHERE site = :site")
    suspend fun count(site: Site): Int
}

@Dao
interface BlockedDao {
    @Query("SELECT * FROM blocked ORDER BY kind, value COLLATE NOCASE")
    fun observeAll(): Flow<List<Blocked>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: Blocked)

    @Query("DELETE FROM blocked WHERE site = :site AND kind = :kind AND value = :value")
    suspend fun delete(site: Site, kind: BlockKind, value: String)
}
