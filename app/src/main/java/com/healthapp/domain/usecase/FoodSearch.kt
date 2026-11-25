package com.healthapp.domain.usecase

import java.text.Normalizer

/**
 * Accent-insensitive food search, so "pho bo", "PHỞ BÒ" and "phở bò" all find "Phở bò".
 * Vietnamese is often typed without diacritics (or the keyboard drops them), and SQL LIKE
 * only folds ASCII case, so matching is done here instead.
 */
object FoodSearch {
    private val marks = Regex("\\p{M}+")
    private val spaces = Regex("\\s+")

    /** Lower case, no diacritics, "đ" → "d", single spaces. */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace('đ', 'd')
            .replace(spaces, " ")
            .trim()

    /** Every word of [query] appears in [name] (any order). A blank query matches everything. */
    fun matches(name: String, query: String): Boolean {
        val words = fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val folded = fold(name)
        return words.all { it in folded }
    }

    /**
     * Sort key: 0 = name starts with the query, 1 = a word of the name starts with it, 2 = contains it.
     * Shorter names first within a rank ("Phở bò" before "Phở bò tái lăn").
     */
    fun rank(name: String, query: String): Pair<Int, Int> {
        val q = fold(query)
        val n = fold(name)
        val r = when {
            q.isEmpty() || n.startsWith(q) -> 0
            n.split(' ').any { it.startsWith(q.substringBefore(' ')) } -> 1
            else -> 2
        }
        return r to n.length
    }

    /** Filters [items] by name and orders them by [rank]. */
    fun <T> search(items: List<T>, query: String, name: (T) -> String): List<T> =
        items.filter { matches(name(it), query) }
            .sortedWith(compareBy<T>({ rank(name(it), query).first }, { rank(name(it), query).second }))
}
