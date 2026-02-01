package org.introskipper.segmenteditor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.repository.JellyfinRepository
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val jellyfinApiService: JellyfinApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState

    init {
        loadLibraries()
    }

    fun refresh() {
        loadLibraries()
    }
    
    /**
     * Gets the backdrop image URL for a library
     */
    fun getBackdropUrl(itemId: String, maxWidth: Int = 800): String {
        return jellyfinApiService.getBackdropUrl(
            itemId = itemId,
            backdropIndex = 0,
            maxWidth = maxWidth
        )
    }

    private fun loadLibraries() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            try {
                val libraries = jellyfinRepository.getLibraries()
                val libraryList = libraries.map { mediaItem ->
                    Library(
                        id = mediaItem.id,
                        name = mediaItem.name ?: "Unknown Library",
                        collectionType = mediaItem.collectionType,
                        backdropImageTag = mediaItem.backdropImageTags?.firstOrNull()
                    )
                }
                
                _uiState.value = if (libraryList.isEmpty()) {
                    LibraryUiState.Empty
                } else {
                    LibraryUiState.Success(libraryList)
                }
            } catch (e: Exception) {
                _uiState.value = LibraryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    object Empty : LibraryUiState()
    data class Success(val libraries: List<Library>) : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}

data class Library(
    val id: String,
    val name: String,
    val collectionType: String?,
    val backdropImageTag: String? = null
)
