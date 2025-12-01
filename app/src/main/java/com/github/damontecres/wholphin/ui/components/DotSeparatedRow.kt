/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.theme.WholphinTheme

@Composable
fun DotSeparatedRow(
    texts: List<String>,
    communityRating: Float? = null,
    criticRating: Float? = null,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    CompositionLocalProvider(LocalTextStyle provides textStyle) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            texts.forEachIndexed { index, text ->
                Text(
                    text = text,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (communityRating != null || criticRating != null || index != texts.lastIndex) {
                    Dot()
                }
            }
            communityRating?.let {
                SimpleStarRating(
                    communityRating = it,
                    modifier = Modifier,
                )
                if (criticRating != null) {
                    Dot()
                }
            }
            criticRating?.let {
                TomatoRating(it)
            }
        }
    }
}

@Composable
fun Dot(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(horizontal = 8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 1f))
                .size(4.dp),
    )
}

@PreviewTvSpec
@Composable
private fun DotSeparatedRowPreview() {
    WholphinTheme {
        Column {
            DotSeparatedRow(
                texts = listOf("2025", "1h 48m", "PG-13", "1h 30m left"),
                communityRating = null,
                criticRating = .75f,
                modifier = Modifier,
                textStyle = MaterialTheme.typography.titleMedium,
            )
            DotSeparatedRow(
                texts = listOf("2025", "1h 48m", "PG-13", "1h 30m left"),
                communityRating = 7.5f,
                criticRating = .75f,
                modifier = Modifier,
                textStyle = MaterialTheme.typography.titleMedium,
            )
            DotSeparatedRow(
                texts = listOf("2025", "1h 48m", "PG-13", "1h 30m left 7.5"),
                communityRating = 7.5f,
                criticRating = .45f,
                modifier = Modifier,
                textStyle = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
