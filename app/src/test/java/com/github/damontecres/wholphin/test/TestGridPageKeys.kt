package com.github.damontecres.wholphin.test

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34])
@OptIn(ExperimentalTestApi::class)
class TestGridPageKeys {
    @get:Rule
    val compose = createComposeRule()

    private data class Item(
        val index: Int,
    ) : CardGridItem {
        override val gridId = index.toString()
        override val playable = false
        override val sortName = gridId
    }

    @Test
    fun pageDownTransfersFocusToTargetCard() {
        var position = 0
        compose.setContent {
            WholphinTheme {
                val focus = remember { FocusRequester() }
                Box(Modifier.size(600.dp, 400.dp)) {
                    CardGrid(
                        pager = List(1000) { Item(it) },
                        onClickItem = { _, _ -> },
                        onLongClickItem = { _, _ -> },
                        onClickPlay = { _, _ -> },
                        letterPosition = { 0 },
                        gridFocusRequester = focus,
                        showJumpButtons = false,
                        showLetterButtons = false,
                        positionCallback = { _, index -> position = index },
                        modifier = Modifier.fillMaxSize(),
                        cardContent = { details ->
                            Box(
                                details.mod
                                    .height(100.dp)
                                    .testTag("item-${details.index}")
                                    .focusable(),
                            )
                        },
                    )
                }
            }
        }
        compose.onNodeWithTag("item-0").requestFocus()
        compose.onNodeWithTag("item-0").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.PageDown) }
        compose.waitForIdle()
        val target = position
        assertTrue(target > 0)
        compose.onNodeWithTag("item-$target").assertIsFocused()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("item-${target + 6}").assertIsFocused()
    }
}
