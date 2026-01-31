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

    private val _selectedCollections = MutableStateFlow<Set<String>>(emptySet())
    val selectedCollections: StateFlow<Set<String>> = _selectedCollections

    private val _availableCollections = MutableStateFlow<List<JellyfinCollection>>(emptyList())
    val availableCollections: StateFlow<List<JellyfinCollection>> = _availableCollections

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
        loadCollections()
        loadMediaItems()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleCollection(collectionId: String) {
        val current = _selectedCollections.value.toMutableSet()
        if (current.contains(collectionId)) {
            current.remove(collectionId)
        } else {
            current.add(collectionId)
        }
        _selectedCollections.value = current
        currentPage = 1
        loadMediaItems()
    }

    fun clearCollectionFilter() {
        _selectedCollections.value = emptySet()
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
        loadCollections()
    }

    private fun loadCollections() {
        viewModelScope.launch {
            try {
                val collections = jellyfinRepository.getLibraries()
                _availableCollections.value = collections.map {
                    JellyfinCollection(
                        id = it.id,
                        name = it.name ?: "Unknown"
                    )
                }
            } catch (e: Exception) {
                // Silently fail for collections
            }
        }
    }

    private fun loadMediaItems() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val parentIds = if (_selectedCollections.value.isEmpty()) {
                    null
                } else {
                    _selectedCollections.value.toList()
                }

                val result = jellyfinRepository.getMediaItems(
                    searchTerm = _searchQuery.value.ifBlank { null },
                    parentIds = parentIds,
                    startIndex = (currentPage - 1) * pageSize,
                    limit = pageSize
                )

                totalPages = (result.totalRecordCount + pageSize - 1) / pageSize

                val serverUrl = securePreferences.getServerUrl() ?: ""
                val jellyfinItems = result.items.map { it.toJellyfinMediaItem(serverUrl) }

                _uiState.value = if (jellyfinItems.isEmpty()) {
                    HomeUiState.Empty
                } else {
                    HomeUiState.Success(jellyfinItems)
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
    data class Success(val items: List<JellyfinMediaItem>) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

data class JellyfinCollection(
    val id: String,
    val name: String
)
