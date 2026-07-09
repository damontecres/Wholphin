package com.github.damontecres.wholphin.ui.preferences

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.components.SelectedLeadingContent
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun FilteredLanguagePreference(
    @StringRes title: Int,
    selectedOption: PreferredLanguageType,
    options: List<PreferredLanguageType>,
    onClickOption: (PreferredLanguageType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    var filteredOptions by remember { mutableStateOf(options) }
    LaunchedEffect(query) {
        delay(500.milliseconds)
        if (query.isNotNullOrBlank()) {
            withContext(WholphinDispatchers.Default) {
                val q = query.lowercase()
                filteredOptions =
                    options.filter {
                        it is PreferredLanguageType.Language &&
                            (it.name.lowercase().contains(q) || it.iso.contains(q))
                    }
            }
        } else {
            filteredOptions = options
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
        modifier,
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val listState = rememberLazyListState()
        val focusRequesters = remember { List(filteredOptions.size) { FocusRequester() } }
        LaunchedEffect(Unit) {
            focusRequesters.firstOrNull()?.tryRequestFocus()
        }
        LazyColumn(
            state = listState,
            modifier = Modifier,
        ) {
            item {
                SearchEditTextBox(
                    value = query,
                    onValueChange = { query = it },
                    onSearchClick = { focusRequesters.firstOrNull()?.tryRequestFocus() },
                    modifier =
                        Modifier
                            .padding(bottom = 8.dp)
                            .fillMaxWidth(),
                )
            }
            itemsIndexed(filteredOptions) { index, option ->
                ListItem(
                    selected = false,
                    onClick = {
                        onClickOption.invoke(option)
                    },
                    leadingContent = {
                        SelectedLeadingContent(option == selectedOption)
                    },
                    headlineContent = {
                        val text =
                            when (option) {
                                PreferredLanguageType.AnyLanguage -> option.displayString.getString()
                                is PreferredLanguageType.Language -> option.displayString.getString()
                                is PreferredLanguageType.ServerProfile -> stringResource(R.string.use_server_profile)
                            }
                        Text(text)
                    },
                    supportingContent =
                        when (option) {
                            PreferredLanguageType.AnyLanguage,
                            is PreferredLanguageType.Language,
                            -> null

                            is PreferredLanguageType.ServerProfile -> option.name?.let { { Text(it) } }
                        },
                    modifier = Modifier.focusRequester(focusRequesters[index]),
                )
            }
        }
    }
}
