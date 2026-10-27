package warlockfe.warlock3.core.util

actual fun Throwable.isFatal(): Boolean = this is VirtualMachineError
