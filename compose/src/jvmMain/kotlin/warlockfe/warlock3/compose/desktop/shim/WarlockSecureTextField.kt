package warlockfe.warlock3.compose.desktop.shim

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

private val PasswordOutputTransformation =
    OutputTransformation {
        val length = length
        if (length > 0) {
            replace(0, length, "•".repeat(length))
        }
    }

@Composable
fun WarlockSecureTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
) {
    TextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        outputTransformation = PasswordOutputTransformation,
        placeholder = placeholder?.let { @Composable { Text(it) } },
    )
}

@Suppress("UnusedReceiverParameter")
private fun TextFieldBuffer.placeholderUnused() = Unit
