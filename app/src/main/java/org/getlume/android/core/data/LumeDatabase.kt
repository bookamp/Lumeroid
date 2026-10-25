package org.getlume.android.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.getlume.android.core.data.dao.*
import org.getlume.android.core.data.entities.*

@Database(
    entities = [
        PlaylistEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        EpgListingEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        UserProfileEntity::class,
        WatchProgressEntity::class,
        SportsFollowEntity::class,
        RecordingEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class LumeDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun categoryDao(): CategoryDao
    abstract fun epgDao(): EpgDao
    abstract fun vodDao(): VodDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun sportsDao(): SportsDao
    abstract fun recordingDao(): RecordingDao
    abstract fun profileDao(): ProfileDao

    companion object {
        @Volatile
        private var INSTANCE: LumeDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN syncEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE playlists ADD COLUMN lastSyncDate INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE series ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS idx_movies_id")
                db.execSQL("DROP INDEX IF EXISTS idx_series_id")
                db.execSQL("ALTER TABLE movies ADD COLUMN tmdbId INTEGER")
                db.execSQL("ALTER TABLE movies ADD COLUMN logoUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE series ADD COLUMN tmdbId INTEGER")
                db.execSQL("ALTER TABLE series ADD COLUMN logoUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movies_tmdbId ON movies(tmdbId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_tmdbId ON series(tmdbId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies ADD COLUMN added TEXT")
                db.execSQL("ALTER TABLE series ADD COLUMN lastModified TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movies_added ON movies(added)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_lastModified ON series(lastModified)")
            }
        }

        fun getInstance(context: Context): LumeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LumeDatabase::class.java,
                    "lume.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
