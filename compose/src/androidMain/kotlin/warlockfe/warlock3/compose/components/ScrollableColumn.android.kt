package warlockfe.warlock3.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun ScrollableColumnImpl(
    modifier: Modifier,
    verticalArrangement: Arrangement.Vertical,
    horizontalAlignment: Alignment.Horizontal,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement,
        horizontalAlignment,
        content,
    )
}

@Composable
actual fun ScrollableLazyColumnImpl(
    modifier: Modifier,
    state: LazyListState,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(modifier = modifier, state = state, content = content)
}
