package com.github.damontecres.wholphin.util

import java.util.function.IntFunction
import java.util.function.Predicate

interface BlockingList<T> : List<T> {
    suspend fun getBlocking(index: Int): T

    suspend fun indexOfBlocking(predicate: Predicate<T>): Int

    companion object {
        fun <T> of(list: List<T>): BlockingList<T> = BlockingListWrapper(list)
    }
}

private class BlockingListWrapper<T>(
    private val list: List<T>,
) : BlockingList<T>,
    List<T> by list {
    override suspend fun getBlocking(index: Int): T = get(index)

    override suspend fun indexOfBlocking(predicate: Predicate<T>): Int = indexOfFirst { predicate.test(it) }

    @Deprecated("Deprecated")
    override fun <T> toArray(generator: IntFunction<Array<out T?>?>): Array<out T?> = super<List>.toArray(generator)
}
