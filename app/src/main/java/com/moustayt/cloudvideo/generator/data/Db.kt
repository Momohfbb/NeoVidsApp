package com.moustayt.cloudvideo.generator.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LibraryItem>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): LibraryItem?

    @Query("SELECT * FROM items WHERE status IN ('QUEUED', 'RUNNING') AND is_local = 0")
    suspend fun active(): List<LibraryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LibraryItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LibraryItem>)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int
}

@Database(entities = [LibraryItem::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun items(): ItemDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "neovids.db")
                .build().also { instance = it }
        }
    }
}
