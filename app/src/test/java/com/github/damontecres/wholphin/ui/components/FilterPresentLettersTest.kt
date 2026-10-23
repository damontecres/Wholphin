package com.github.damontecres.wholphin.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterPresentLettersTest {
    @Test
    fun keepsOnlyLettersWithItems() {
        assertEquals("#BD", "#ABCD".filterPresent(listOf(1, 0, 2, 0, 3)))
    }

    @Test
    fun dropsHashWhenNothingSortsBeforeFirstLetter() {
        assertEquals("A", "#AB".filterPresent(listOf(0, 5, 0)))
    }

    @Test
    fun keepsEveryLetterWhenNoneHasItems() {
        assertEquals("#AB", "#AB".filterPresent(listOf(0, 0, 0)))
    }
}
