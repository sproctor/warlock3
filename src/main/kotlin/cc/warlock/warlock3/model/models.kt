package cc.warlock.warlock3.model

import cc.warlock.warlock3.stormfront.SgeConnection
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import tornadofx.*

data class Document(var title : String = "", var text: String = "")

class DocumentViewModel(var document: Document = Document()) : ItemViewModel<Document>() {
    val title = bind { document.observable(Document::title) }
    val text = bind { document.observable(Document::text) }

}

class AccountModel : ItemViewModel<Account>(Account()) {
    val name: StringProperty = bind { item?.nameProperty() }
    val password: StringProperty = bind { item?.passwordProperty() }
}

data class Game(val title: String, val code: String, val status: String)

class GameModel() : ItemViewModel<Game>() {
    val title = bind { SimpleStringProperty(item?.title ?: "") }
    val status = bind { SimpleStringProperty(item?.status ?: "") }
}

class SgeConnectionModel : ItemViewModel<SgeConnection>() {

}