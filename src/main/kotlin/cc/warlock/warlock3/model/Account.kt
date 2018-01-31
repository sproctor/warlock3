package cc.warlock.warlock3.model

import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import tornadofx.*

class Account {
    var name by property<String>()
    fun nameProperty() = getProperty(Account::name)

    var password by property<String>()
    fun passwordProperty() = getProperty(Account::password)
}

class AccountModel : ItemViewModel<Account>(Account()) {
    val name: StringProperty = bind { item?.nameProperty() }
    val password: StringProperty = bind { item?.passwordProperty() }
}