package com.github.damontecres.wholphin.util

class TransformList<S, T>(
    private val source: List<S>,
    private val transform: (S) -> T,
) : AbstractList<T>() {
    override val size: Int
        get() = source.size

    override fun get(index: Int): T = transform.invoke(source[index])
}
