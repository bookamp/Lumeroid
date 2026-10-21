package org.getlume.android.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
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

    companion object {
        @Volatile
        private var INSTANCE: LumeDatabase? = null

        fun getInstance(context: Context): LumeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LumeDatabase::class.java,
                    "lume.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
