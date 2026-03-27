package org.introskipper.segmenteditor

import org.introskipper.segmenteditor.data.model.CONTAINER_TYPES
import org.introskipper.segmenteditor.data.model.JellyfinMediaItem
import org.introskipper.segmenteditor.data.model.isContainerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for container type detection used in collection traversal navigation.
 *
 * These tests verify that container items (BoxSet, Folder, Playlist, etc.) are
 * correctly identified so they can be browsed into rather than opened in the player.
 */
class ContainerNavigationTest {

    private fun makeItem(type: String?, isFolder: Boolean = false) = JellyfinMediaItem(
        id = "test-id",
        name = "Test",
        imageUrl = null,
        productionYear = null,
        runTimeTicks = null,
        officialRating = null,
        type = type,
        isFolder = isFolder
    )

    @Test
    fun isContainerType_boxSet_isTrue() {
        assertTrue(makeItem("BoxSet").isContainerType())
    }

    @Test
    fun isContainerType_folder_isTrue() {
        assertTrue(makeItem("Folder").isContainerType())
    }

    @Test
    fun isContainerType_collectionFolder_isTrue() {
        assertTrue(makeItem("CollectionFolder").isContainerType())
    }

    @Test
    fun isContainerType_playlist_isTrue() {
        assertTrue(makeItem("Playlist").isContainerType())
    }

    @Test
    fun isContainerType_aggregateFolder_isTrue() {
        assertTrue(makeItem("AggregateFolder").isContainerType())
    }

    @Test
    fun isContainerType_userView_isTrue() {
        assertTrue(makeItem("UserView").isContainerType())
    }

    @Test
    fun isContainerType_photoAlbum_isTrue() {
        assertTrue(makeItem("PhotoAlbum").isContainerType())
    }

    @Test
    fun isContainerType_manualPlaylistsFolder_isTrue() {
        assertTrue(makeItem("ManualPlaylistsFolder").isContainerType())
    }

    @Test
    fun isContainerType_playlistsFolder_isTrue() {
        assertTrue(makeItem("PlaylistsFolder").isContainerType())
    }

    @Test
    fun isContainerType_isFolderTrue_isTrue() {
        // Items with isFolder=true are containers regardless of type
        assertTrue(makeItem(type = null, isFolder = true).isContainerType())
        assertTrue(makeItem(type = "Movie", isFolder = true).isContainerType())
    }

    @Test
    fun isContainerType_movie_isFalse() {
        assertFalse(makeItem("Movie").isContainerType())
    }

    @Test
    fun isContainerType_series_isFalse() {
        assertFalse(makeItem("Series").isContainerType())
    }

    @Test
    fun isContainerType_episode_isFalse() {
        assertFalse(makeItem("Episode").isContainerType())
    }

    @Test
    fun isContainerType_musicAlbum_isFalse() {
        assertFalse(makeItem("MusicAlbum").isContainerType())
    }

    @Test
    fun isContainerType_musicArtist_isFalse() {
        assertFalse(makeItem("MusicArtist").isContainerType())
    }

    @Test
    fun isContainerType_audio_isFalse() {
        assertFalse(makeItem("Audio").isContainerType())
    }

    @Test
    fun isContainerType_nullType_isFalse() {
        assertFalse(makeItem(type = null, isFolder = false).isContainerType())
    }

    @Test
    fun containerTypes_containsExpectedTypes() {
        assertTrue(CONTAINER_TYPES.contains("BoxSet"))
        assertTrue(CONTAINER_TYPES.contains("Folder"))
        assertTrue(CONTAINER_TYPES.contains("CollectionFolder"))
        assertTrue(CONTAINER_TYPES.contains("Playlist"))
        assertTrue(CONTAINER_TYPES.contains("AggregateFolder"))
        assertTrue(CONTAINER_TYPES.contains("UserView"))
        assertTrue(CONTAINER_TYPES.contains("PhotoAlbum"))
        assertTrue(CONTAINER_TYPES.contains("ManualPlaylistsFolder"))
        assertTrue(CONTAINER_TYPES.contains("PlaylistsFolder"))
    }
}
