package cc.warlock.warlock3.core

data class ProgressBarData(
    val id: String,
    val groupId: String,
    val value: Percentage,
    val text: String,
    val left: Percentage,
    val width: Percentage,
)