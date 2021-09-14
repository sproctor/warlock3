package cc.warlock.warlock3.app.viewmodel

import cc.warlock.warlock3.core.ClientProgressBarEvent
import cc.warlock.warlock3.core.ProgressBarData
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class VitalsViewModel(
    private val client: StormfrontClient
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _vitalBars = MutableStateFlow<Map<String, ProgressBarData>>(emptyMap())
    val vitalBars = _vitalBars.asStateFlow()

    init {
        scope.launch {
            client.eventFlow.collect { event ->
                when (event) {
                    is ClientProgressBarEvent -> {
                        _vitalBars.value = _vitalBars.value +
                                mapOf(event.progressBarData.id to event.progressBarData)
                    }
                    else -> {
                        // don't care
                    }
                }
            }
        }
    }
}