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
 * Container items (BoxSet, CollectionFolder, Folder, etc.) are browsed into via the
 * home screen rather than opened in the player.
 *
 * Note: Jellyfin marks Series items with IsFolder=true at the API level, so
 * isContainerType() returns true for them.  The navigation logic in HomeScreen
 * therefore checks item.type == "Series" *before* calling isContainerType() to ensure
 * series are routed to SeriesScreen rather than the generic browse screen.
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
    fun knownContainerTypes_areRecognized() {
        for (type in CONTAINER_TYPES) {
            assertTrue("$type should be a container type", makeItem(type).isContainerType())
        }
    }

    @Test
    fun isFolder_flag_makesItemContainer() {
        // Items with isFolder=true are containers regardless of their type string
        assertTrue(makeItem(type = null, isFolder = true).isContainerType())
        assertTrue(makeItem(type = "Series", isFolder = true).isContainerType())
    }

    @Test
    fun playableAndSpecialRoutingTypes_areNotContainers() {
        for (type in listOf("Movie", "Episode", "Audio", "Series", "MusicAlbum", "MusicArtist")) {
            assertFalse("$type (isFolder=false) should not be a container", makeItem(type).isContainerType())
        }
        assertFalse("null type with isFolder=false should not be a container", makeItem(null).isContainerType())
    }
}
