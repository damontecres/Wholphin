package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import io.mockk.MockKMatcherScope

fun MockKMatcherScope.nonBlankString() = match<String> { it.isNotNullOrBlank() }
