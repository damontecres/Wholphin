package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.detail.series.findIndexByNumberOrId
import com.github.damontecres.wholphin.util.BlockingList
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert
import org.junit.Test

class TestFindIndexByNumberOrId {
    fun create(
        indexNumber: Int,
        parentIndexNumber: Int,
    ) = BaseItem(
        BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.EPISODE,
            indexNumber = indexNumber,
            parentIndexNumber = parentIndexNumber,
        ),
    )

    val epS02E01 = create(1, 2)
    val epS02E02 = create(2, 2)
    val epS02E03 = create(3, 2)
    val epS02E04 = create(4, 2)
    val epS02E05 = create(5, 2)
    val epS00E08 = create(8, 0)
    val epS00E02 = create(2, 0)

    private val episodes =
        listOf(
            epS02E01,
            epS02E02,
            epS02E03,
        )

    private val missingEpisodes =
        listOf(
            epS02E01,
            epS02E04,
            epS02E05,
        )

    private val withSpecials =
        listOf(
            epS02E01,
            epS02E02,
            epS02E03,
            epS00E08,
        )

    @Test
    fun `Basic tests`() =
        runTest {
            findIndexByNumberOrId(targetNum = 1, targetId = epS02E01.id, list = BlockingList.of(episodes)).let { index ->
                Assert.assertEquals(0, index)
            }
            findIndexByNumberOrId(targetNum = 2, targetId = epS02E02.id, list = BlockingList.of(episodes)).let { index ->
                Assert.assertEquals(1, index)
            }
            findIndexByNumberOrId(targetNum = 3, targetId = epS02E03.id, list = BlockingList.of(episodes)).let { index ->
                Assert.assertEquals(2, index)
            }
            findIndexByNumberOrId(targetNum = 100, targetId = UUID.randomUUID(), list = BlockingList.of(episodes)).let { index ->
                Assert.assertEquals(0, index)
            }
        }

    @Test
    fun `Test missing episodes`() =
        runTest {
            findIndexByNumberOrId(targetNum = 1, targetId = epS02E01.id, list = BlockingList.of(missingEpisodes)).let { index ->
                Assert.assertEquals(0, index)
            }
            findIndexByNumberOrId(targetNum = 2, targetId = epS02E02.id, list = BlockingList.of(missingEpisodes)).let { index ->
                Assert.assertEquals(0, index)
            }
            findIndexByNumberOrId(targetNum = 4, targetId = epS02E04.id, list = BlockingList.of(missingEpisodes)).let { index ->
                Assert.assertEquals(1, index)
            }
            findIndexByNumberOrId(targetNum = 5, targetId = epS02E05.id, list = BlockingList.of(missingEpisodes)).let { index ->
                Assert.assertEquals(2, index)
            }
        }

    @Test
    fun `Test missing episodes without ID`() =
        runTest {
            findIndexByNumberOrId(targetNum = 1, targetId = null, list = BlockingList.of(missingEpisodes)).let { index ->
                Assert.assertEquals(0, index)
            }
            findIndexByNumberOrId(targetNum = 2, targetId = null, list = BlockingList.of(missingEpisodes)).let { index ->
                Assert.assertEquals(0, index)
            }
            findIndexByNumberOrId(targetNum = 4, targetId = null, list = BlockingList.of(missingEpisodes)).let { index ->
                Assert.assertEquals(0, index)
            }
            findIndexByNumberOrId(targetNum = 5, targetId = null, list = BlockingList.of(missingEpisodes)).let { index ->
                Assert.assertEquals(0, index)
            }
        }

    @Test
    fun `Test with special`() =
        runTest {
            findIndexByNumberOrId(targetNum = 1, targetId = epS02E01.id, list = BlockingList.of(withSpecials)).let { index ->
                Assert.assertEquals(0, index)
            }
            findIndexByNumberOrId(targetNum = 2, targetId = epS02E02.id, list = BlockingList.of(withSpecials)).let { index ->
                Assert.assertEquals(1, index)
            }
            findIndexByNumberOrId(targetNum = 3, targetId = epS02E03.id, list = BlockingList.of(withSpecials)).let { index ->
                Assert.assertEquals(2, index)
            }
        }

    @Test
    fun `Test with special matching number`() =
        runTest {
            val list = listOf(epS02E01, epS00E02, epS02E02)
            findIndexByNumberOrId(targetNum = 2, targetId = epS02E02.id, list = BlockingList.of(list)).let { index ->
                Assert.assertEquals(2, index)
            }
        }
}
