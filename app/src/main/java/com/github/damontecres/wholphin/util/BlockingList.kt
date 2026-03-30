package com.github.damontecres.wholphin.util

import java.util.function.IntFunction
import java.util.function.Predicate

/**
 * A [List] which has function that will wait for a result
 */
interface BlockingList<T> : List<T> {
    /**
     * Get the specified index, possibly blocking until it is available
     */
    suspend fun getBlocking(index: Int): T

    /**
     * Get first index that matches the given predicate, possibly blocking while searching
     */
    suspend fun indexOfBlocking(predicate: Predicate<T>): Int

    companion object {
        /**
         * Create a [BlockingList] over a regular [List]
         */
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
