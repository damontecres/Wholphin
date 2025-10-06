package com.github.damontecres.dolphin.util

import java.util.function.Predicate

interface BlockingList<T> : List<T> {
    suspend fun getBlocking(index: Int): T

    suspend fun indexOfBlocking(predicate: Predicate<T>): Int
}
