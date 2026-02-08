package org.introskipper.segmenteditor

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for season sorting logic.
 * 
 * Tests verify that seasons are sorted in ascending order (1, 2, 3, ...)
 * with season 0 (specials) always at the end.
 */
class SeasonSortingTest {
    
    /**
     * Custom comparator that sorts seasons in ascending order,
     * but places season 0 (specials) at the end.
     */
    private val seasonComparator = Comparator<Int> { season1, season2 ->
        when {
            season1 == 0 && season2 == 0 -> 0
            season1 == 0 -> 1  // season1 is 0, so it comes after season2
            season2 == 0 -> -1 // season2 is 0, so season1 comes before it
            else -> season1.compareTo(season2) // Normal ascending order
        }
    }
    
    @Test
    fun seasonComparator_withSpecialsAndRegularSeasons_sortsCorrectly() {
        val seasons = listOf(0, 2, 1, 3)
        val sorted = seasons.sortedWith(seasonComparator)
        
        assertEquals("Expected [1, 2, 3, 0]", listOf(1, 2, 3, 0), sorted)
    }
    
    @Test
    fun seasonComparator_withOnlySpecials_returnsSpecials() {
        val seasons = listOf(0)
        val sorted = seasons.sortedWith(seasonComparator)
        
        assertEquals("Expected [0]", listOf(0), sorted)
    }
    
    @Test
    fun seasonComparator_withoutSpecials_sortsAscending() {
        val seasons = listOf(3, 1, 2)
        val sorted = seasons.sortedWith(seasonComparator)
        
        assertEquals("Expected [1, 2, 3]", listOf(1, 2, 3), sorted)
    }
    
    @Test
    fun seasonComparator_withMultipleDigitSeasons_sortsCorrectly() {
        val seasons = listOf(0, 10, 5, 1, 12, 8)
        val sorted = seasons.sortedWith(seasonComparator)
        
        assertEquals("Expected [1, 5, 8, 10, 12, 0]", listOf(1, 5, 8, 10, 12, 0), sorted)
    }
    
    @Test
    fun seasonComparator_withSortedMapInput_maintainsCorrectOrder() {
        val seasonMap = mapOf(0 to "Specials", 2 to "Season 2", 1 to "Season 1", 3 to "Season 3")
        val sorted = seasonMap.toSortedMap(seasonComparator)
        
        val expectedKeys = listOf(1, 2, 3, 0)
        assertEquals("Expected keys [1, 2, 3, 0]", expectedKeys, sorted.keys.toList())
    }
    
    @Test
    fun seasonComparator_emptyList_returnsEmpty() {
        val seasons = emptyList<Int>()
        val sorted = seasons.sortedWith(seasonComparator)
        
        assertTrue("Expected empty list", sorted.isEmpty())
    }
    
    @Test
    fun seasonComparator_singleNormalSeason_returnsSame() {
        val seasons = listOf(1)
        val sorted = seasons.sortedWith(seasonComparator)
        
        assertEquals("Expected [1]", listOf(1), sorted)
    }
}
