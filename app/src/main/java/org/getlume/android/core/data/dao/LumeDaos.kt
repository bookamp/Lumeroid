package org.getlume.android.core.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.getlume.android.core.data.entities.*

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlaylist(): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    fun observeActivePlaylist(): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    fun observePlaylistById(id: String): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET isActive = (id = :activeId)")
    suspend fun setActivePlaylist(activeId: String)

    @Query("UPDATE playlists SET lastSyncDate = :lastSyncDate WHERE id = :id")
    suspend fun updateLastSyncDate(id: String, lastSyncDate: Long)

    @Query("UPDATE playlists SET syncEnabled = :syncEnabled WHERE id = :id")
    suspend fun updateSyncEnabled(id: String, syncEnabled: Boolean)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY sortOrder ASC, channelNumber ASC")
    fun getChannelsByCategory(playlistId: String, categoryId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY sortOrder ASC")
    fun getFavoriteChannels(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortOrder ASC, channelNumber ASC")
    fun observeChannelsByPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    suspend fun getAllChannels(playlistId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchChannels(query: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE (playlistId = :playlistId OR :playlistId = '') AND name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchChannels(playlistId: String, query: String): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun updateFavorite(channelId: String, isFavorite: Boolean)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsForPlaylist(playlistId: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type AND isHidden = 0 ORDER BY sortOrder ASC, name ASC")
    fun getCategories(playlistId: String, type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND isHidden = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(playlistId: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId AND type = :type")
    suspend fun deleteCategories(playlistId: String, type: String)
}

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_listings WHERE channelTvgId = :tvgId AND endTime >= :now ORDER BY startTime ASC LIMIT 10")
    fun getUpcomingPrograms(tvgId: String, now: Long): Flow<List<EpgListingEntity>>

    @Query("SELECT * FROM epg_listings WHERE channelTvgId = :tvgId AND startTime <= :now AND endTime > :now LIMIT 1")
    suspend fun getCurrentProgram(tvgId: String, now: Long): EpgListingEntity?

    @Query("SELECT * FROM epg_listings WHERE startTime >= :startWindow AND endTime <= :endWindow")
    suspend fun getProgramsForWindow(startWindow: Long, endWindow: Long): List<EpgListingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgListings(listings: List<EpgListingEntity>)

    @Query("DELETE FROM epg_listings WHERE endTime < :cutoff")
    suspend fun purgeOldListings(cutoff: Long)
}

@Dao
interface VodDao {
    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY name ASC")
    fun getMoviesByCategory(playlistId: String, categoryId: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND isFavorite = 1")
    fun getFavoriteMovies(playlistId: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE id = :movieId LIMIT 1")
    suspend fun getMovieById(movieId: String): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<MovieEntity>)

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY name ASC")
    fun getSeriesByCategory(playlistId: String, categoryId: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :seriesId LIMIT 1")
    suspend fun getSeriesById(seriesId: String): SeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(seriesList: List<SeriesEntity>)

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun getEpisodesForSeries(seriesId: String): Flow<List<EpisodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY name ASC LIMIT :limit")
    fun getMovies(playlistId: String, limit: Int = 100): Flow<List<MovieEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY name ASC LIMIT :limit")
    fun getSeries(playlistId: String, limit: Int = 100): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND posterUrl != '' ORDER BY rating DESC, name ASC LIMIT :limit")
    fun getFeaturedMovies(playlistId: String, limit: Int = 12): Flow<List<MovieEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND posterUrl != '' ORDER BY rating DESC, name ASC LIMIT :limit")
    fun getFeaturedSeries(playlistId: String, limit: Int = 12): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND isFavorite = 1")
    fun getFavoriteSeries(playlistId: String): Flow<List<SeriesEntity>>

    @Query("UPDATE movies SET backdropUrl = :backdropUrl, plot = CASE WHEN :plot != '' THEN :plot ELSE plot END, rating = CASE WHEN :rating > 0 THEN :rating ELSE rating END WHERE id = :id")
    suspend fun updateMovieEnrichment(id: String, backdropUrl: String, plot: String, rating: Float)

    @Query("UPDATE series SET backdropUrl = :backdropUrl, plot = CASE WHEN :plot != '' THEN :plot ELSE plot END, rating = CASE WHEN :rating > 0 THEN :rating ELSE rating END WHERE id = :id")
    suspend fun updateSeriesEnrichment(id: String, backdropUrl: String, plot: String, rating: Float)

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId")
    suspend fun getMovieCount(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId")
    suspend fun getSeriesCount(playlistId: String): Int

    @Query("SELECT * FROM movies WHERE (playlistId = :playlistId OR :playlistId = '') AND name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchMovies(playlistId: String, query: String): List<MovieEntity>

    @Query("SELECT * FROM series WHERE (playlistId = :playlistId OR :playlistId = '') AND name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchSeries(playlistId: String, query: String): List<SeriesEntity>
}

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND isCompleted = 0 ORDER BY lastWatchedAt DESC LIMIT 20")
    fun getContinueWatching(profileId: String): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND contentId = :contentId LIMIT 1")
    suspend fun getProgress(profileId: String, contentId: String): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND contentId = :contentId")
    suspend fun clearProgress(profileId: String, contentId: String)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId")
    suspend fun clearAllProgressForProfile(profileId: String)
}

@Dao
interface SportsDao {
    @Query("SELECT * FROM sports_follows WHERE profileId = :profileId")
    fun getFollowedSports(profileId: String): Flow<List<SportsFollowEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun follow(follow: SportsFollowEntity)

    @Delete
    suspend fun unfollow(follow: SportsFollowEntity)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY startTime DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecordings(recordings: List<RecordingEntity>)

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles ORDER BY name ASC")
    suspend fun getProfilesList(): List<UserProfileEntity>

    @Query("SELECT * FROM user_profiles WHERE isCurrent = 1 LIMIT 1")
    fun observeActiveProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE isCurrent = 1 LIMIT 1")
    suspend fun getActiveProfile(): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Update
    suspend fun updateProfile(profile: UserProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profiles SET isCurrent = (id = :activeId)")
    suspend fun setActiveProfile(activeId: String)

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun getProfileCount(): Int
}
