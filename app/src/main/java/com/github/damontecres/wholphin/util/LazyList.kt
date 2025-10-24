package com.github.damontecres.wholphin.util

class LazyList<Source, Transform>(
    private val source: List<Source>,
    private val transform: (Source) -> Transform,
) : AbstractList<Transform>() {
    override fun get(index: Int): Transform = transform.invoke(source[index])

    override val size: Int
        get() = source.size
}
