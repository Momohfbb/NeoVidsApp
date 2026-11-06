package com.moustayt.cloudvideo.generator.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LibraryItem>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): LibraryItem?

    @Query("SELECT * FROM items WHERE status IN ('QUEUED', 'RUNNING') AND is_local = 0")
    suspend fun active(): List<LibraryItem>

    @Query("SELECT * FROM items WHERE groupId = :groupId ORDER BY sceneIndex ASC, createdAt ASC")
    fun observeGroup(groupId: String): Flow<List<LibraryItem>>

    @Query("UPDATE items SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LibraryItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LibraryItem>)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN finalPrompt TEXT")
        db.execSQL("ALTER TABLE items ADD COLUMN errorCode TEXT")
        db.execSQL("ALTER TABLE items ADD COLUMN durationSec INTEGER NOT NULL DEFAULT 5")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN groupId TEXT")
        db.execSQL("ALTER TABLE items ADD COLUMN groupTitle TEXT")
        db.execSQL("ALTER TABLE items ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE items ADD COLUMN sceneIndex INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [LibraryItem::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun items(): ItemDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "neovids.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
