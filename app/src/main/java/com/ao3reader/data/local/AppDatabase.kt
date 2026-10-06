package com.ao3reader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ao3reader.data.model.Site

class Converters {
    @TypeConverter
    fun fromList(list: List<String>): String = list.joinToString(SEP)

    @TypeConverter
    fun toList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(SEP)

    @TypeConverter
    fun fromKind(kind: BlockKind): String = kind.name

    @TypeConverter
    fun toKind(value: String): BlockKind = BlockKind.valueOf(value)

    @TypeConverter
    fun fromSite(site: Site): String = site.name

    @TypeConverter
    fun toSite(value: String): Site = Site.valueOf(value)

    private companion object {
        const val SEP = "\u001F"
    }
}

@Database(entities = [LibraryWork::class, FavoriteTag::class, Blocked::class], version = 2, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
    abstract fun favoriteTags(): FavoriteTagDao
    abstract fun blocked(): BlockedDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ao3reader.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Adds FanFiction.net: covers, likes, and a site for every favorite tag and block. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library ADD COLUMN coverUrl TEXT")
                db.execSQL("ALTER TABLE library ADD COLUMN authorIds TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE library ADD COLUMN liked INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS favorite_tags_new (site TEXT NOT NULL, name TEXT NOT NULL, " +
                        "section TEXT NOT NULL, target TEXT NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(site, name))",
                )
                db.execSQL("INSERT INTO favorite_tags_new (site, name, section, target, addedAt) SELECT 'AO3', name, section, name, addedAt FROM favorite_tags")
                db.execSQL("DROP TABLE favorite_tags")
                db.execSQL("ALTER TABLE favorite_tags_new RENAME TO favorite_tags")
                db.execSQL("ALTER TABLE blocked ADD COLUMN site TEXT NOT NULL DEFAULT 'AO3'")
                db.execSQL("DROP INDEX IF EXISTS index_blocked_kind_value")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_blocked_site_kind_value ON blocked (site, kind, value)")
            }
        }
    }
}
