package org.introskipper.segmenteditor.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.data.model.JellyfinMediaItem
import org.introskipper.segmenteditor.data.model.toJellyfinMediaItem
import org.introskipper.segmenteditor.data.repository.JellyfinRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _showAllItems = MutableStateFlow(false)
    val showAllItems: StateFlow<Boolean> = _showAllItems

    private var currentLibraryId: String? = null

    var currentPage by mutableStateOf(1)
        private set

    var totalPages by mutableStateOf(1)
        private set

    private var pageSize = 20

    init {
        viewModelScope.launch {
            _searchQuery.debounce(500).collect {
                currentPage = 1
                loadMediaItems()
            }
        }
    }
    
    fun setLibraryId(libraryId: String) {
        if (currentLibraryId != libraryId) {
            currentLibraryId = libraryId
            currentPage = 1
            _showAllItems.value = false
            loadMediaItems()
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleShowAllItems() {
        _showAllItems.value = !_showAllItems.value
        currentPage = 1
        loadMediaItems()
    }

    fun nextPage() {
        if (currentPage < totalPages) {
            currentPage++
            loadMediaItems()
        }
    }

    fun previousPage() {
        if (currentPage > 1) {
            currentPage--
            loadMediaItems()
        }
    }

    fun refresh() {
        currentPage = 1
        loadMediaItems()
    }

    private fun loadMediaItems() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val libraryId = currentLibraryId
                if (libraryId == null) {
                    _uiState.value = HomeUiState.Empty
                    return@launch
                }

                val limit = if (_showAllItems.value) null else pageSize
                val startIndex = if (_showAllItems.value) 0 else (currentPage - 1) * pageSize

                val result = jellyfinRepository.getMediaItems(
                    searchTerm = _searchQuery.value.ifBlank { null },
                    parentIds = listOf(libraryId),
                    startIndex = startIndex,
                    limit = limit,
                    includeItemTypes = listOf("Series") // Only show TV series in library view
                )

                totalPages = if (_showAllItems.value) {
                    1
                } else {
                    (result.totalRecordCount + pageSize - 1) / pageSize
                }

                val serverUrl = securePreferences.getServerUrl() ?: ""
                val jellyfinItems = result.items.map { it.toJellyfinMediaItem(serverUrl) }

                _uiState.value = if (jellyfinItems.isEmpty()) {
                    HomeUiState.Empty
                } else {
                    HomeUiState.Success(jellyfinItems, result.totalRecordCount)
                }
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class HomeUiState {
    object Loading : HomeUiState()
    object Empty : HomeUiState()
    data class Success(val items: List<JellyfinMediaItem>, val totalItems: Int) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
