package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.models.*
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingTextInput
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.common.models.Streamable.Media.Companion.toServerMedia
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed

class QobuzExtension : ExtensionClient, TrackClient, SearchFeedClient {
    private lateinit var settings: Settings

    override fun setSettings(settings: Settings) {
        this.settings = settings
    }

    override suspend fun onInitialize() {
        // Initialization if needed
    }

    override suspend fun getSettingItems(): List<Setting> {
        return listOf(
            SettingTextInput(
                title = "Qobuz Instance",
                key = "qobuz_instance",
                summary = "Enter the custom Qobuz instance URL(s)",
                defaultValue = null
            )
        )
    }

    override suspend fun loadTrack(track: Track, isDownload: Boolean): Track {
        if (track.streamables.any { it.extras.containsKey("qobuz_resolved") }) return track

        val qobuzStreamable = Streamable.server(
            id = track.id,
            quality = 1,
            title = "Qobuz",
            extras = mapOf(
                "qobuz_resolved" to "true",
                "title" to track.title,
                "artist" to track.artists.joinToString(", ") { it.name },
                "album" to (track.album?.title ?: ""),
                "isrc" to (track.isrc ?: ""),
                "durationMs" to (track.duration?.toString() ?: "")
            )
        )

        return track.copy(streamables = track.streamables + qobuzStreamable)
    }

    override suspend fun loadFeed(track: Track): Feed<Shelf>? = null

    override suspend fun loadSearchFeed(query: String): Feed<Shelf> {
        val q = QobuzAudioProvider.Query(
            mediaId = "",
            title = query,
            artists = emptyList(),
            album = null,
            isrc = null,
            durationMs = null,
            countryCode = "US",
            backend = QobuzAudioProvider.ResolverBackend.CUSTOM,
            customInstances = settings.getString("qobuz_instance")
        )
        val tracks = QobuzAudioProvider.searchCandidates(q).map { candidate ->
            Track(
                id = candidate.trackId,
                title = candidate.title,
                artists = candidate.artists.map { Artist(id = "", name = it) },
                album = candidate.album?.let { Album(id = "", title = it) },
                duration = candidate.durationMs,
                isrc = candidate.isrc
            )
        }
        return listOf<Shelf>(
            Shelf.Lists.Tracks(
                id = "search_results",
                title = "Search Results",
                list = tracks
            )
        ).toFeed()
    }

    override suspend fun loadStreamableMedia(streamable: Streamable, isDownload: Boolean): Streamable.Media {
        val title = streamable.extras["title"] ?: ""
        val artist = streamable.extras["artist"] ?: ""
        val album = streamable.extras["album"]
        val isrc = streamable.extras["isrc"]
        val durationMs = streamable.extras["durationMs"]?.toLongOrNull()
        val trackId = streamable.id

        val query = QobuzAudioProvider.Query(
            mediaId = trackId,
            title = title,
            artists = if (artist.isNotBlank()) artist.split(", ") else emptyList(),
            album = album?.takeIf { it.isNotBlank() },
            isrc = isrc?.takeIf { it.isNotBlank() },
            durationMs = durationMs,
            countryCode = "US",
            backend = QobuzAudioProvider.ResolverBackend.CUSTOM,
            customInstances = settings.getString("qobuz_instance")
        )

        val resolved = QobuzAudioProvider.resolve(query)
        return resolved.mediaUri.toServerMedia()
    }
}
