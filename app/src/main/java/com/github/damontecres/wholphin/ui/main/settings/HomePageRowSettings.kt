package com.github.damontecres.wholphin.ui.main.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.ui.preferences.ComposablePreference

@Composable
fun HomePageRowSettings(
    title: String,
    viewOptions: HomeRowViewOptions,
    onViewOptionsChange: (HomeRowViewOptions) -> Unit,
    onApplyApplyAll: () -> Unit,
    modifier: Modifier = Modifier,
    defaultViewOptions: HomeRowViewOptions = HomeRowViewOptions(),
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyColumn {
            items(HomeRowViewOptions.OPTIONS) { pref ->
                pref as AppPreference<HomeRowViewOptions, Any>
                val interactionSource = remember { MutableInteractionSource() }
                val value = pref.getter.invoke(viewOptions)
                ComposablePreference(
                    preference = pref,
                    value = value,
                    onNavigate = {},
                    onValueChange = { newValue ->
                        onViewOptionsChange.invoke(pref.setter(viewOptions, newValue))
                    },
                    interactionSource = interactionSource,
                    onClickPreference = { pref ->
                        if (pref == HomeRowViewOptions.ViewOptionsReset) {
                            onViewOptionsChange.invoke(defaultViewOptions)
                        } else if (pref == HomeRowViewOptions.ViewOptionsApplyAll) {
                            onApplyApplyAll.invoke()
                        }
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp)),
                )
            }
        }
    }
}
