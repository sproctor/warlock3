package warlockfe.warlock3.compose.components

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun ScrollableColumnImpl(
    modifier: Modifier,
    verticalArrangement: Arrangement.Vertical,
    horizontalAlignment: Alignment.Horizontal,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberScrollState()
    val adapter = rememberScrollbarAdapter(state)
    val isScrollbarVisible = adapter.viewportSize < adapter.contentSize
    Box(modifier) {
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(end = if (isScrollbarVisible) LocalScrollbarStyle.current.thickness else 0.dp)
                .verticalScroll(state),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
        if (isScrollbarVisible) {
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = adapter,
            )
        }
    }
}

@Composable
actual fun ScrollableLazyColumnImpl(
    modifier: Modifier,
    state: LazyListState,
    content: LazyListScope.() -> Unit,
) {
    val adapter = rememberScrollbarAdapter(state)
    val isScrollbarVisible = adapter.viewportSize < adapter.contentSize
    Box(modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight()
                .padding(end = if (isScrollbarVisible) LocalScrollbarStyle.current.thickness else 0.dp),
            state = state,
            content = content,
        )
        if (isScrollbarVisible) {
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(state),
            )
        }
    }
}