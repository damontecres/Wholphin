package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.LocalTrailer
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.Trailer

@Composable
fun TrailerDialog(
    onDismissRequest: () -> Unit,
    trailers: List<Trailer>,
    onClick: (Trailer) -> Unit,
) {
    val trailersStr = stringResource(R.string.play_trailer)
    val localStr = stringResource(R.string.local)
    val externalStr = stringResource(R.string.external_track)
    val params =
        remember(trailers) {
            DialogParams(
                fromLongClick = false,
                title = trailersStr,
                items =
                    trailers.map { trailer ->
                        DialogItem(
                            headlineContent = {
                                Text(trailer.name)
                            },
                            supportingContent = {
                                val subtitle =
                                    when (trailer) {
                                        is LocalTrailer -> localStr
                                        is RemoteTrailer -> trailer.subtitle ?: externalStr
                                    }
                                Text(
                                    text = subtitle,
                                )
                            },
                            onClick = { onClick.invoke(trailer) },
                        )
                    },
            )
        }
    DialogPopup(
        params = params,
        onDismissRequest = onDismissRequest,
    )
}
