package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.RememberedTabDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.RememberedTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RememberedTabService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val rememberedTabDao: RememberedTabDao,
    ) {
        suspend fun getRememberedTab(itemId: UUID): Int? =
            withContext(Dispatchers.IO) {
                serverRepository.currentUser?.rowId?.let { userId ->
                    rememberedTabDao.getRememberedTab(userId, itemId)?.index
                }
            }

        suspend fun saveRememberedTab(
            itemId: UUID,
            tabIndex: Int,
        ): Unit =
            withContext(Dispatchers.IO) {
                serverRepository.currentUser?.rowId?.let { userId ->
                    rememberedTabDao.save(RememberedTab(userId, itemId, tabIndex))
                }
            }
    }
