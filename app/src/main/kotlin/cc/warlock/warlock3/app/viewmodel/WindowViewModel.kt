package cc.warlock.warlock3.app.viewmodel

import androidx.compose.ui.text.AnnotatedString
import cc.warlock.warlock3.core.WindowLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WindowViewModel(
    val name: String,
    initialLocation: WindowLocation,
    initialOpenState: Boolean,
) {
    private val _location = MutableStateFlow(initialLocation)
    val location = _location.asStateFlow()

    private val _isOpen = MutableStateFlow(initialOpenState)
    val isOpen = _isOpen.asStateFlow()
    
    var buffer: AnnotatedString? = null

    fun append(text: String) {

    }
}