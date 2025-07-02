package warlockfe.warlock3.compose.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.oikvpqya.compose.fastscroller.ScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.ThumbStyle
import io.github.oikvpqya.compose.fastscroller.TrackStyle
import io.github.oikvpqya.compose.fastscroller.VerticalScrollbar
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter

@Composable
fun ScrollableColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollbarStyle: ScrollbarStyle = defaultMaterialScrollbarStyle(),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier) {
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection)
                )
                .verticalScroll(state),
            verticalArrangement,
            horizontalAlignment,
        ) {
            Spacer(Modifier.height(contentPadding.calculateTopPadding()))
            content()
            Spacer(Modifier.height(contentPadding.calculateBottomPadding()))
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            style = scrollbarStyle,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
fun ScrollableLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical =
        if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    scrollbarStyle: ScrollbarStyle = defaultMaterialScrollbarStyle(),
    content: LazyListScope.() -> Unit
) {
    Box(modifier) {
        LazyColumn(
            state = state,
            contentPadding = contentPadding,
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            style = scrollbarStyle,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
fun defaultScrollbarStyle(
    minimalHeight: Dp = 52.dp,
    thickness: Dp = 8.dp,
    hoverDurationMillis: Int = 300,
    thumbStyle: ThumbStyle = ThumbStyle(
        shape = RoundedCornerShape(4.dp),
        unhoverColor = Color(MaterialTheme.colorScheme.primary.toArgb()),
        hoverColor = Color(MaterialTheme.colorScheme.primary.toArgb()),
    ),
    trackStyle: TrackStyle = TrackStyle(
        shape = RoundedCornerShape(4.dp),
        unhoverColor = Color(MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).toArgb()),
        hoverColor = Color(MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).toArgb()),
    ),
): ScrollbarStyle {
    return ScrollbarStyle(
        minimalHeight = minimalHeight,
        thickness = thickness,
        hoverDurationMillis = hoverDurationMillis,
        thumbStyle = thumbStyle,
        trackStyle = trackStyle,
    )
}
