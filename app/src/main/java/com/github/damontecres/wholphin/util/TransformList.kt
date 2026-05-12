package com.github.damontecres.wholphin.util

/**
 * A utility class to lazily transforms items from the source list
 */
class TransformList<S, T>(
    private val source: List<S>,
    private val transform: (S) -> T,
) : AbstractList<T>() {
    override val size: Int
        get() = source.size

    override fun get(index: Int): T = transform.invoke(source[index])
}
