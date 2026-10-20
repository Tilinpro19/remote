package com.universalremote.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universalremote.domain.manager.RemoteControllerManager
import com.universalremote.domain.model.Favorite
import com.universalremote.domain.repository.FavoriteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Favoritos del TV activo. Enviar un favorito sigue pasando por [RemoteViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val manager: RemoteControllerManager,
    private val repository: FavoriteRepository,
) : ViewModel() {

    val favorites: StateFlow<List<Favorite>> = manager.activeDevice
        .flatMapLatest { device -> device?.let { repository.observeForDevice(it.id) } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mantener pulsado: guarda el favorito o lo quita si ya estaba. */
    fun toggle(label: String, target: Favorite.Target) {
        val device = manager.activeDevice.value ?: return
        val existing = favorites.value.firstOrNull { it.target == target }
        viewModelScope.launch {
            if (existing != null) repository.remove(existing.id) else repository.add(device.id, label, target)
        }
    }

    fun remove(favorite: Favorite) {
        viewModelScope.launch { repository.remove(favorite.id) }
    }
}
