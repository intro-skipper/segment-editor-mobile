package org.introskipper.segmenteditor

import org.introskipper.segmenteditor.ui.util.SeasonSortUtil
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for season sorting logic.
 * 
 * Tests verify that seasons are sorted in ascending order (1, 2, 3, ...)
 * with season 0 (specials) always at the end.
 */
class SeasonSortingTest {
    
    @Test
    fun seasonComparator_withSpecialsAndRegularSeasons_sortsCorrectly() {
        val seasons = listOf(0, 2, 1, 3)
        val sorted = seasons.sortedWith(SeasonSortUtil.seasonComparator)
        
        assertEquals("Expected [1, 2, 3, 0]", listOf(1, 2, 3, 0), sorted)
    }
    
    @Test
    fun seasonComparator_withOnlySpecials_returnsSpecials() {
        val seasons = listOf(0)
        val sorted = seasons.sortedWith(SeasonSortUtil.seasonComparator)
        
        assertEquals("Expected [0]", listOf(0), sorted)
    }
    
    @Test
    fun seasonComparator_withoutSpecials_sortsAscending() {
        val seasons = listOf(3, 1, 2)
        val sorted = seasons.sortedWith(SeasonSortUtil.seasonComparator)
        
        assertEquals("Expected [1, 2, 3]", listOf(1, 2, 3), sorted)
    }
    
    @Test
    fun seasonComparator_withMultipleDigitSeasons_sortsCorrectly() {
        val seasons = listOf(0, 10, 5, 1, 12, 8)
        val sorted = seasons.sortedWith(SeasonSortUtil.seasonComparator)
        
        assertEquals("Expected [1, 5, 8, 10, 12, 0]", listOf(1, 5, 8, 10, 12, 0), sorted)
    }
    
    @Test
    fun seasonComparator_withSortedMapInput_maintainsCorrectOrder() {
        val seasonMap = mapOf(0 to "Specials", 2 to "Season 2", 1 to "Season 1", 3 to "Season 3")
        val sorted = seasonMap.toSortedMap(SeasonSortUtil.seasonComparator)
        
        val expectedKeys = listOf(1, 2, 3, 0)
        assertEquals("Expected keys [1, 2, 3, 0]", expectedKeys, sorted.keys.toList())
    }
    
    @Test
    fun seasonComparator_emptyList_returnsEmpty() {
        val seasons = emptyList<Int>()
        val sorted = seasons.sortedWith(SeasonSortUtil.seasonComparator)
        
        assertTrue("Expected empty list", sorted.isEmpty())
    }
    
    @Test
    fun seasonComparator_singleNormalSeason_returnsSame() {
        val seasons = listOf(1)
        val sorted = seasons.sortedWith(SeasonSortUtil.seasonComparator)
        
        assertEquals("Expected [1]", listOf(1), sorted)
    }
}
