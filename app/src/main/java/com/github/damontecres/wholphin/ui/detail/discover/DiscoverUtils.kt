package com.github.damontecres.wholphin.ui.detail.discover

import com.github.damontecres.wholphin.api.seerr.infrastructure.ClientException
import com.github.damontecres.wholphin.data.model.DiscoverRating
import kotlinx.coroutines.CancellationException
import timber.log.Timber

suspend fun getDiscoverRating(
    id: Int,
    func: suspend () -> DiscoverRating,
): DiscoverRating? =
    try {
        func.invoke()
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: ClientException) {
        if (ex.statusCode == 404) {
            Timber.w("No rating for %s", id)
        } else {
            Timber.e(ex, "Error fetching rating for %s", id)
        }
        null
    } catch (ex: Exception) {
        Timber.e(ex, "Error fetching rating for %s", id)
        null
    }
