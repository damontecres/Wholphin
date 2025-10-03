package com.github.damontecres.dolphin.ui.data

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.Serializable

@Serializable
data class RowColumn(
    val row: Int,
    val column: Int,
)

val RowColumnSaver =
    Saver<RowColumn, List<Int>>(
        save = { listOf(it.row, it.column) },
        restore = { RowColumn(it[0], it[1]) },
    )
