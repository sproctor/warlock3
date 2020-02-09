package cc.warlock.warlock3.app.model

import tornadofx.*

class Account {
    var name by property<String>()
    fun nameProperty() = getProperty(Account::name)

    var password by property<String>()
    fun passwordProperty() = getProperty(Account::password)
}