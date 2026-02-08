package org.introskipper.segmenteditor.ui.util

/**
 * Utility object for season sorting logic.
 */
object SeasonSortUtil {
    /**
     * Custom comparator that sorts seasons in ascending order (1, 2, 3, ...),
     * but places season 0 (specials) at the end of the list.
     * 
     * Examples:
     * - [0, 2, 1, 3] -> [1, 2, 3, 0]
     * - [0, 10, 5, 1] -> [1, 5, 10, 0]
     * - [3, 1, 2] -> [1, 2, 3]
     */
    val seasonComparator = Comparator<Int> { season1, season2 ->
        when {
            season1 == 0 && season2 == 0 -> 0
            season1 == 0 -> 1  // season1 is 0, so it comes after season2
            season2 == 0 -> -1 // season2 is 0, so season1 comes before it
            else -> season1.compareTo(season2) // Normal ascending order
        }
    }
}
