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
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND categoryId = :categoryId AND isHidden = 0 ORDER BY sortOrder ASC, channelNumber ASC")
    fun getChannelsByCategory(playlistId: String, categoryId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavorite = 1 AND isHidden = 0 ORDER BY sortOrder ASC")
    fun getFavoriteChannels(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT c.* FROM channels c INNER JOIN watch_progress w ON c.id = w.contentId WHERE w.profileId = :profileId AND w.contentType = 'channel' AND c.playlistId = :playlistId AND c.isHidden = 0 ORDER BY w.lastWatchedAt DESC LIMIT 50")
    fun getRecentlyWatchedChannels(playlistId: String, profileId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isHidden = 0 AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) ORDER BY sortOrder ASC, channelNumber ASC")
    fun observeChannelsByPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    suspend fun getAllChannels(playlistId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE isHidden = 0 AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) AND name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchChannels(query: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE (playlistId = :playlistId OR :playlistId = '') AND isHidden = 0 AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) AND name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchChannels(playlistId: String, query: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY sortOrder ASC, channelNumber ASC")
    fun getChannelsForManagement(playlistId: String, categoryId: String): Flow<List<ChannelEntity>>

    @Query("UPDATE channels SET isHidden = :isHidden WHERE id = :channelId")
    suspend fun updateChannelHidden(channelId: String, isHidden: Boolean)

    @Query("UPDATE channels SET isHidden = :isHidden WHERE playlistId = :playlistId AND categoryId = :categoryId")
    suspend fun setAllChannelsHidden(playlistId: String, categoryId: String, isHidden: Boolean)

    @Query("UPDATE channels SET sortOrder = :sortOrder WHERE id = :channelId")
    suspend fun updateChannelSortOrder(channelId: String, sortOrder: Int)

    @Query("UPDATE channels SET sortOrder = channelNumber, isHidden = 0 WHERE playlistId = :playlistId AND categoryId = :categoryId")
    suspend fun resetChannelSortOrder(playlistId: String, categoryId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun updateFavorite(channelId: String, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun getChannelCount(playlistId: String): Int

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsForPlaylist(playlistId: String)

    @Query("DELETE FROM channels WHERE playlistId NOT IN (SELECT id FROM playlists)")
    suspend fun deleteOrphanedChannels()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type AND isHidden = 0 ORDER BY sortOrder ASC, name ASC")
    fun getCategories(playlistId: String, type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type AND isHidden = 0 ORDER BY sortOrder ASC, name ASC")
    suspend fun getCategoriesDirect(playlistId: String, type: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND isHidden = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeCategories(playlistId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND isHidden = 0 ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllCategories(playlistId: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND type = :type ORDER BY sortOrder ASC, name ASC")
    fun getAllCategoriesForManagement(playlistId: String, type: String): Flow<List<CategoryEntity>>

    @Query("UPDATE categories SET isHidden = :isHidden WHERE id = :categoryId")
    suspend fun updateCategoryHidden(categoryId: String, isHidden: Boolean)

    @Query("UPDATE categories SET isHidden = :isHidden WHERE playlistId = :playlistId AND type = :type")
    suspend fun setAllCategoriesHidden(playlistId: String, type: String, isHidden: Boolean)

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :categoryId")
    suspend fun updateCategorySortOrder(categoryId: String, sortOrder: Int)

    @Query("UPDATE categories SET sortOrder = 0, isHidden = 0 WHERE playlistId = :playlistId AND type = :type")
    suspend fun resetCategorySortOrder(playlistId: String, type: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId AND type = :type")
    suspend fun deleteCategories(playlistId: String, type: String)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteCategoriesForPlaylist(playlistId: String)

    @Query("DELETE FROM categories WHERE playlistId NOT IN (SELECT id FROM playlists)")
    suspend fun deleteOrphanedCategories()
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

    @Query("SELECT * FROM epg_listings WHERE channelTvgId IN (:tvgIds) AND endTime > :now AND startTime < :horizonEnd ORDER BY channelTvgId ASC, startTime ASC")
    fun observeProgramsForChannels(tvgIds: List<String>, now: Long, horizonEnd: Long): Flow<List<EpgListingEntity>>

    @Query("SELECT * FROM epg_listings WHERE channelTvgId IN (:tvgIds) AND endTime > :now AND startTime < :horizonEnd ORDER BY channelTvgId ASC, startTime ASC")
    suspend fun getProgramsForChannels(tvgIds: List<String>, now: Long, horizonEnd: Long): List<EpgListingEntity>

    @Query("SELECT COUNT(*) FROM epg_listings")
    suspend fun getCount(): Int

    @Query("DELETE FROM epg_listings WHERE endTime < :cutoff")
    suspend fun purgeOldListings(cutoff: Long)
}

data class GenreRowTuple(
    val genre: String,
    val categoryId: String
)

@Dao
interface VodDao {
    @Query("SELECT genre, categoryId FROM series WHERE playlistId = :playlistId AND genre != '' ORDER BY rowid ASC LIMIT :limit")
    suspend fun getSeriesGenreTokens(playlistId: String, limit: Int = 5000): List<GenreRowTuple>

    @Query("SELECT genre, categoryId FROM movies WHERE playlistId = :playlistId AND genre != '' ORDER BY rowid ASC LIMIT :limit")
    suspend fun getMovieGenreTokens(playlistId: String, limit: Int = 5000): List<GenreRowTuple>

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

    @Query("SELECT * FROM episodes WHERE id = :episodeId LIMIT 1")
    suspend fun getEpisodeById(episodeId: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun deleteMoviesForPlaylist(playlistId: String)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteSeriesForPlaylist(playlistId: String)

    @Query("DELETE FROM movies WHERE playlistId NOT IN (SELECT id FROM playlists)")
    suspend fun deleteOrphanedMovies()

    @Query("DELETE FROM series WHERE playlistId NOT IN (SELECT id FROM playlists)")
    suspend fun deleteOrphanedSeries()

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) ORDER BY name ASC LIMIT :limit")
    fun getMovies(playlistId: String, limit: Int = 100): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND added IS NOT NULL AND added != '' AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) ORDER BY added DESC, rowid DESC LIMIT :limit")
    fun getRecentlyAddedMovies(playlistId: String, limit: Int = 100): Flow<List<MovieEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) ORDER BY name ASC LIMIT :limit")
    fun getSeries(playlistId: String, limit: Int = 100): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND lastModified IS NOT NULL AND lastModified != '' AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) ORDER BY lastModified DESC, rowid DESC LIMIT :limit")
    fun getRecentlyAddedSeries(playlistId: String, limit: Int = 100): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND isFavorite = 1")
    suspend fun getFavoriteMoviesDirect(playlistId: String): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND added IS NOT NULL AND added != '' AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) ORDER BY added DESC, rowid DESC LIMIT :limit")
    suspend fun getRecentlyAddedMoviesDirect(playlistId: String, limit: Int = 100): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY name ASC LIMIT :limit")
    fun getMoviesByCategoryLimited(playlistId: String, categoryId: String, limit: Int = 21): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY name ASC LIMIT :limit")
    suspend fun getMoviesByCategoryLimitedDirect(playlistId: String, categoryId: String, limit: Int = 21): List<MovieEntity>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY name ASC LIMIT :limit")
    fun getSeriesByCategoryLimited(playlistId: String, categoryId: String, limit: Int = 21): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND categoryId = :categoryId ORDER BY name ASC LIMIT :limit")
    suspend fun getSeriesByCategoryLimitedDirect(playlistId: String, categoryId: String, limit: Int = 21): List<SeriesEntity>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) AND posterUrl != '' ORDER BY rating DESC, name ASC LIMIT :limit")
    fun getFeaturedMovies(playlistId: String, limit: Int = 12): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) AND posterUrl != '' ORDER BY rating DESC, name ASC LIMIT :limit")
    suspend fun getFeaturedMoviesDirect(playlistId: String, limit: Int = 12): List<MovieEntity>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) AND posterUrl != '' ORDER BY rating DESC, name ASC LIMIT :limit")
    fun getFeaturedSeries(playlistId: String, limit: Int = 12): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) AND posterUrl != '' ORDER BY rating DESC, name ASC LIMIT :limit")
    suspend fun getFeaturedSeriesDirect(playlistId: String, limit: Int = 12): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND isFavorite = 1")
    fun getFavoriteSeries(playlistId: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND isFavorite = 1")
    suspend fun getFavoriteSeriesDirect(playlistId: String): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND lastModified IS NOT NULL AND lastModified != '' AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) ORDER BY lastModified DESC, rowid DESC LIMIT :limit")
    suspend fun getRecentlyAddedSeriesDirect(playlistId: String, limit: Int = 100): List<SeriesEntity>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND (genre LIKE '%' || :genre || '%' OR categoryId IN (SELECT id FROM categories WHERE name LIKE '%' || :genre || '%')) ORDER BY name ASC")
    fun getMoviesByGenre(playlistId: String, genre: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND (genre LIKE '%' || :genre || '%' OR categoryId IN (SELECT id FROM categories WHERE name LIKE '%' || :genre || '%')) ORDER BY name ASC")
    fun getSeriesByGenre(playlistId: String, genre: String): Flow<List<SeriesEntity>>

    @Query("UPDATE movies SET backdropUrl = :backdropUrl, plot = CASE WHEN :plot != '' THEN :plot ELSE plot END, rating = CASE WHEN :rating > 0 THEN :rating ELSE rating END, genre = CASE WHEN :genre != '' THEN :genre ELSE genre END WHERE id = :id")
    suspend fun updateMovieEnrichment(id: String, backdropUrl: String, plot: String, rating: Float, genre: String = "")

    @Query("UPDATE series SET backdropUrl = :backdropUrl, plot = CASE WHEN :plot != '' THEN :plot ELSE plot END, rating = CASE WHEN :rating > 0 THEN :rating ELSE rating END, genre = CASE WHEN :genre != '' THEN :genre ELSE genre END WHERE id = :id")
    suspend fun updateSeriesEnrichment(id: String, backdropUrl: String, plot: String, rating: Float, genre: String = "")

    @Query("UPDATE movies SET isFavorite = :isFavorite WHERE id = :movieId")
    suspend fun updateMovieFavorite(movieId: String, isFavorite: Boolean)

    @Query("UPDATE series SET isFavorite = :isFavorite WHERE id = :seriesId")
    suspend fun updateSeriesFavorite(seriesId: String, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId")
    suspend fun getMovieCount(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId AND added IS NOT NULL AND added != ''")
    suspend fun getMoviesWithAddedCount(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId")
    suspend fun getSeriesCount(playlistId: String): Int

    @Query("SELECT * FROM movies WHERE (playlistId = :playlistId OR :playlistId = '') AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) AND name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchMovies(playlistId: String, query: String): List<MovieEntity>

    @Query("SELECT * FROM series WHERE (playlistId = :playlistId OR :playlistId = '') AND (categoryId IS NULL OR categoryId NOT IN (SELECT id FROM categories WHERE isHidden = 1)) AND name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchSeries(playlistId: String, query: String): List<SeriesEntity>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND tmdbId IN (:tmdbIds)")
    suspend fun getMoviesByTmdbIds(playlistId: String, tmdbIds: List<Int>): List<MovieEntity>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND tmdbId IN (:tmdbIds)")
    suspend fun getSeriesByTmdbIds(playlistId: String, tmdbIds: List<Int>): List<SeriesEntity>

    @Query("UPDATE movies SET logoUrl = :logoUrl WHERE id = :id")
    suspend fun updateMovieLogo(id: String, logoUrl: String)

    @Query("UPDATE series SET logoUrl = :logoUrl WHERE id = :id")
    suspend fun updateSeriesLogo(id: String, logoUrl: String)

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId AND tmdbId IS NOT NULL")
    suspend fun getTmdbMappedCount(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId AND tmdbId IS NOT NULL")
    suspend fun getSeriesTmdbMappedCount(playlistId: String): Int

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND categoryId IN (:categoryIds) AND posterUrl != '' ORDER BY rating DESC, name ASC LIMIT :limit")
    suspend fun getTopMoviesByCategories(playlistId: String, categoryIds: List<String>, limit: Int = 40): List<MovieEntity>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND categoryId IN (:categoryIds) AND posterUrl != '' ORDER BY rating DESC, name ASC LIMIT :limit")
    suspend fun getTopSeriesByCategories(playlistId: String, categoryIds: List<String>, limit: Int = 40): List<SeriesEntity>
}

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND isCompleted = 0 ORDER BY lastWatchedAt DESC LIMIT 20")
    fun getContinueWatching(profileId: String): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND isCompleted = 0 ORDER BY lastWatchedAt DESC LIMIT 20")
    suspend fun getContinueWatchingDirect(profileId: String): List<WatchProgressEntity>

    @Query("SELECT * FROM watch_progress WHERE profileId = :profileId AND contentId = :contentId LIMIT 1")
    suspend fun getProgress(profileId: String, contentId: String): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND contentId = :contentId")
    suspend fun clearProgress(profileId: String, contentId: String)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId")
    suspend fun clearAllProgressForProfile(profileId: String)

    @Query("DELETE FROM watch_progress WHERE profileId = :profileId AND contentType = 'channel'")
    suspend fun clearRecentlyWatchedChannels(profileId: String)

    @Query("DELETE FROM watch_progress")
    suspend fun clearAllWatchProgress()
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
