package com.github.damontecres.dolphin.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mikepenz.aboutlibraries.ui.compose.android.rememberLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

@Composable
fun LicenseInfo(modifier: Modifier) {
    val libraries by rememberLibraries()
    MaterialTheme {
        LibrariesContainer(libraries, modifier)
    }
}
