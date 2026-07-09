package com.github.damontecres.wholphin.data.filter

import com.github.damontecres.wholphin.data.model.GetItemsFilter

interface SearchFilter<T : SearchFilter<T>> {
    /**
     * Returns how many of filters are actually being used in this [GetItemsFilter]
     */
    fun countFilters(filterOptions: List<FilterBy<T, *>>): Int {
        var count = 0
        filterOptions.forEach {
            if (it.get(this as T) != null) count++
        }
        return count
    }

    /**
     * Clear all the values for the given filters
     */
    fun delete(filterOptions: List<FilterBy<T, *>>): T {
        var newFilter = this
        filterOptions.forEach {
            newFilter = it.set(null, newFilter as T)
        }
        return newFilter as T
    }
}
