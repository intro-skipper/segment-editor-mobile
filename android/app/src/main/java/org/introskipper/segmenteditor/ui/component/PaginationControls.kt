package org.introskipper.segmenteditor.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Constants for pagination display
private const val MAX_PAGES_WITHOUT_ELLIPSIS = 7
private const val ELLIPSIS_PLACEHOLDER = -1

@Composable
fun PaginationControls(
    currentPage: Int,
    totalPages: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onGoToPage: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Page number chips (only show if callback is provided and there are multiple pages)
        if (onGoToPage != null && totalPages > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousPage,
                    enabled = currentPage > 1
                ) {
                    Icon(
                        imageVector = Icons.Default.NavigateBefore,
                        contentDescription = "Previous page"
                    )
                }

                PageNumberChips(
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onGoToPage = onGoToPage,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onNextPage,
                    enabled = currentPage < totalPages
                ) {
                    Icon(
                        imageVector = Icons.Default.NavigateNext,
                        contentDescription = "Next page"
                    )
                }
            }
        } else {
            // Fallback to simple navigation when no direct page selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPreviousPage,
                    enabled = currentPage > 1
                ) {
                    Icon(
                        imageVector = Icons.Default.NavigateBefore,
                        contentDescription = "Previous page"
                    )
                }

                Text(
                    text = "Page $currentPage of $totalPages",
                    style = MaterialTheme.typography.bodyMedium
                )

                IconButton(
                    onClick = onNextPage,
                    enabled = currentPage < totalPages
                ) {
                    Icon(
                        imageVector = Icons.Default.NavigateNext,
                        contentDescription = "Next page"
                    )
                }
            }
        }
    }
}

@Composable
private fun PageNumberChips(
    currentPage: Int,
    totalPages: Int,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Generate list of page numbers to display
    val pageNumbers = remember(currentPage, totalPages) {
        getPageNumbersToDisplay(currentPage, totalPages)
    }
    
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        items(pageNumbers, key = { it }) { pageNumber ->
            when (pageNumber) {
                ELLIPSIS_PLACEHOLDER -> {
                    // Ellipsis
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                else -> {
                    // Page number chip
                    FilterChip(
                        selected = pageNumber == currentPage,
                        onClick = { onGoToPage(pageNumber) },
                        label = { Text(pageNumber.toString()) },
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Generates a list of page numbers to display, including ellipsis (-1) for gaps.
 * Strategy:
 * - Always show first page
 * - Always show last page
 * - Show current page and 1-2 pages around it
 * - Use ellipsis for gaps
 */
private fun getPageNumbersToDisplay(currentPage: Int, totalPages: Int): List<Int> {
    if (totalPages <= MAX_PAGES_WITHOUT_ELLIPSIS) {
        // Show all pages if MAX_PAGES_WITHOUT_ELLIPSIS or fewer
        return (1..totalPages).toList()
    }
    
    val result = mutableListOf<Int>()
    
    // Always add first page
    result.add(1)
    
    // Determine range around current page
    val rangeStart = maxOf(2, currentPage - 1)
    val rangeEnd = minOf(totalPages - 1, currentPage + 1)
    
    // Add ellipsis before current range if needed
    if (rangeStart > 2) {
        result.add(ELLIPSIS_PLACEHOLDER)
    }
    
    // Add current page range
    for (page in rangeStart..rangeEnd) {
        result.add(page)
    }
    
    // Add ellipsis after current range if needed
    if (rangeEnd < totalPages - 1) {
        result.add(ELLIPSIS_PLACEHOLDER)
    }
    
    // Always add last page
    result.add(totalPages)
    
    return result
}
