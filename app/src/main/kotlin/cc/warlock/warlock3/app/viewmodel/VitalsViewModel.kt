package cc.warlock.warlock3.app.viewmodel

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import cc.warlock.warlock3.core.client.ClientProgressBarEvent
import cc.warlock.warlock3.core.client.ProgressBarData
import cc.warlock.warlock3.stormfront.network.StormfrontClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class VitalsViewModel(
    private val client: StormfrontClient
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _vitalBars = mutableStateMapOf<String, ProgressBarData>()
    val vitalBars: SnapshotStateMap<String, ProgressBarData> = _vitalBars

    init {
        scope.launch {
            client.eventFlow.collect { event ->
                when (event) {
                    is ClientProgressBarEvent -> {
                        _vitalBars += event.progressBarData.id to event.progressBarData
                    }
                    else -> {
                        // don't care
                    }
                }
            }
        }
    }
}