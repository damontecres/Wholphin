package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.RememberedTabDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.RememberedTab
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RememberedTabService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val rememberedTabDao: RememberedTabDao,
    ) {
        suspend fun getRememberedTab(itemId: String): Int? =
            withContext(WholphinDispatchers.IO) {
                serverRepository.currentUser?.rowId?.let { userId ->
                    rememberedTabDao.getRememberedTab(userId, itemId)?.index
                }
            }

        suspend fun saveRememberedTab(
            itemId: String,
            tabIndex: Int,
        ): Unit =
            withContext(WholphinDispatchers.IO) {
                serverRepository.currentUser?.rowId?.let { userId ->
                    rememberedTabDao.save(RememberedTab(userId, itemId, tabIndex))
                }
            }
    }
