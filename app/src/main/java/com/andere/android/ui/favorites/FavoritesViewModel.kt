package com.andere.android.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.andere.android.data.local.FavoritePostDao
import com.andere.android.data.local.FavoritePostEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FavoritesViewModel(
    private val favoritePostDao: FavoritePostDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = FavoritesUiState(
                favorites = favoritePostDao.latest(limit = 500),
            )
        }
    }

    companion object {
        fun factory(
            favoritePostDao: FavoritePostDao,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FavoritesViewModel(
                    favoritePostDao = favoritePostDao,
                ) as T
            }
        }
    }
}

data class FavoritesUiState(
    val favorites: List<FavoritePostEntity> = emptyList(),
)
