package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class UserListFocusTest {
    @get:Rule val composeTestRule = createComposeRule()

    private val serverId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val users =
        listOf("Ada", "Bert", "Cleo").mapIndexed { index, name ->
            JellyfinUserAndImage(
                user =
                    JellyfinUser(
                        id = UUID(0, (index + 1).toLong()),
                        name = name,
                        serverId = serverId,
                        accessToken = "token",
                    ),
                imageUrl = null,
                known = true,
            )
        }

    private fun showUsers(currentUser: JellyfinUser?) {
        composeTestRule.setContent {
            WholphinTheme {
                UserList(
                    users = users,
                    currentUser = currentUser,
                    onSwitchUser = {},
                    onAddUser = {},
                    onRemoveUser = {},
                    onSwitchServer = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    @Test
    fun `opening selector focuses active profile and supports D-pad navigation`() {
        showUsers(users[1].user)

        composeTestRule.onNodeWithText("B").assertIsFocused().performKeyInput {
            pressKey(Key.DirectionRight)
        }
        composeTestRule.onNodeWithText("C").assertIsFocused()
    }

    @Test
    fun `opening selector scrolls to an active profile outside the initial viewport`() {
        showUsers(users[2].user)

        composeTestRule.onNodeWithText("C").assertIsFocused()
    }

    @Test
    fun `opening selector without a listed active profile focuses first profile`() {
        showUsers(null)

        composeTestRule.onNodeWithText("A").assertIsFocused()
    }

    @Test
    fun `opening selector with a removed active profile focuses first profile`() {
        showUsers(users[1].user.copy(id = UUID(0, 99)))

        composeTestRule.onNodeWithText("A").assertIsFocused()
    }
}
