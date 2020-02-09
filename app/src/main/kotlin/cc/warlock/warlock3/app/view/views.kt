package cc.warlock.warlock3.app.view

import cc.warlock.warlock3.app.controller.WarlockClientController
import cc.warlock.warlock3.app.model.DocumentViewModel
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.control.TextArea
import tornadofx.*
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*

class TextEditorFragment(val documentViewModel: DocumentViewModel) : Fragment(){
    override val root = pane {
        title = documentViewModel.title.value
        textarea (documentViewModel.text) {
            this.prefWidthProperty().bind(this@pane.widthProperty());
            this.prefHeightProperty().bind(this@pane.heightProperty());

        }
    }

    init {
        documentViewModel.title.addListener { _, _, n ->
            this.title = n
        }
    }

    override val deletable = SimpleBooleanProperty(false)
    override val closeable = SimpleBooleanProperty( true)
    override val savable = documentViewModel.dirty
    override val refreshable = documentViewModel.dirty

    override fun onSave() {
        documentViewModel.commit()
    }

    override fun onRefresh() {
        documentViewModel.rollback()
    }
}


class EmptyView : View() {
    val controller: WarlockClientController by inject()
    override val root = label("Empty view")
}

/**
 * TextAreaOutputStream
 *
 * Binds an output stream to a textarea
 */
class TextAreaOutputStream(val textArea: TextArea): OutputStream() {

    /**
     * This doesn't support multibyte characters streams like utf8
     */
    @Throws(IOException::class)
    override fun write(b: Int) {
        throw UnsupportedOperationException()
    }

    /**
     * Supports multibyte characters by converting the array buffer to String
     */
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        val text = String(Arrays.copyOf(b, len), Charset.defaultCharset())
        runLater {
            // redirects data to the text area
            textArea.appendText(text)
            // scrolls the text area to the end of data
            textArea.scrollTop = java.lang.Double.MAX_VALUE
        }
    }

}
