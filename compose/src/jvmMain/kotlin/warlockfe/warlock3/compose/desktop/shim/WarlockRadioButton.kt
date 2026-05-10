package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.RadioButton
import org.jetbrains.jewel.ui.component.RadioButtonRow

@Composable
fun WarlockRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun WarlockRadioButtonRow(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    RadioButtonRow(
        selected = selected,
        onClick = onClick,
        text = text,
        modifier = modifier,
        enabled = enabled,
    )
}
