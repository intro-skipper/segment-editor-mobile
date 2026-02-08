package org.introskipper.segmenteditor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.repository.MediaRepository
import org.introskipper.segmenteditor.data.repository.SegmentRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import org.introskipper.segmenteditor.ui.state.EpisodeWithSegments
import org.introskipper.segmenteditor.ui.state.SeriesUiState
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val segmentRepository: SegmentRepository,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<SeriesUiState>(SeriesUiState.Loading)
    val uiState: StateFlow<SeriesUiState> = _uiState

    fun loadSeries(seriesId: String) {
        viewModelScope.launch {
            _uiState.value = SeriesUiState.Loading
            try {
                val userId = securePreferences.getUserId() ?: throw Exception("User not authenticated")

                // Load series info
                val seriesResult = mediaRepository.getItemResult(
                    userId = userId,
                    itemId = seriesId,
                    fields = JellyfinApiService.DETAIL_FIELDS
                )

                if (seriesResult.isFailure) {
                    _uiState.value = SeriesUiState.Error(
                        seriesResult.exceptionOrNull()?.message ?: "Failed to load series"
                    )
                    return@launch
                }

                val series = seriesResult.getOrThrow()

                // Load all episodes (all seasons)
                val episodesResult = mediaRepository.getItemsResult(
                    userId = userId,
                    parentId = seriesId,
                    includeItemTypes = listOf("Episode"),
                    recursive = true,
                    sortBy = "ParentIndexNumber,IndexNumber",
                    sortOrder = "Ascending",
                    fields = JellyfinApiService.EPISODE_FIELDS
                )

                if (episodesResult.isFailure) {
                    _uiState.value = SeriesUiState.Error(
                        episodesResult.exceptionOrNull()?.message ?: "Failed to load episodes"
                    )
                    return@launch
                }

                val episodes = episodesResult.getOrThrow().items

                // Group episodes by season
                // Custom comparator to sort seasons ascending with season 0 (specials) at the end
                val seasonComparator = Comparator<Int> { season1, season2 ->
                    when {
                        season1 == 0 && season2 == 0 -> 0
                        season1 == 0 -> 1  // season1 is 0, so it comes after season2
                        season2 == 0 -> -1 // season2 is 0, so season1 comes before it
                        else -> season1.compareTo(season2) // Normal ascending order
                    }
                }
                
                val episodesBySeason = episodes
                    .groupBy { it.parentIndexNumber ?: 0 }
                    .mapValues { (_, episodeList) ->
                        episodeList.map { episode ->
                            EpisodeWithSegments(episode = episode)
                        }
                    }
                    .toSortedMap(seasonComparator)

                // Create season name mapping from the first episode of each season
                // Use null if season name is not available to allow UI layer to handle fallback
                val seasonNames = episodesBySeason.mapValues { (_, episodeList) ->
                    episodeList.firstOrNull()?.episode?.seasonName
                }

                _uiState.value = SeriesUiState.Success(
                    series = series,
                    episodesBySeason = episodesBySeason,
                    seasonNames = seasonNames
                )

                // Load segment counts asynchronously
                loadSegmentCounts(episodesBySeason)

            } catch (e: Exception) {
                _uiState.value = SeriesUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun loadSegmentCounts(episodesBySeason: Map<Int, List<EpisodeWithSegments>>) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is SeriesUiState.Success) return@launch

            // Load segment counts for all episodes in parallel
            val updatedEpisodesBySeason = episodesBySeason.mapValues { (_, episodes) ->
                episodes.map { episodeWithSegments ->
                    async {
                        val segmentResult = segmentRepository.getSegmentsResult(episodeWithSegments.episode.id)
                        val segmentCount = segmentResult.getOrNull()?.size ?: 0
                        episodeWithSegments.copy(segmentCount = segmentCount)
                    }
                }
            }.mapValues { (_, deferredList) ->
                deferredList.awaitAll()
            }

            _uiState.value = currentState.copy(episodesBySeason = updatedEpisodesBySeason)
        }
    }

    fun refresh(seriesId: String) {
        loadSeries(seriesId)
    }
}
