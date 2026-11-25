package com.healthapp.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSearchTest {
    @Test fun fold_removesDiacritics_case_andD() {
        assertEquals("pho bo", FoodSearch.fold("Phở  Bò"))
        assertEquals("dau phu chien", FoodSearch.fold("Đậu phụ chiên"))
        assertEquals("ca phe sua da", FoodSearch.fold("CÀ PHÊ SỮA ĐÁ"))
    }

    @Test fun matches_withOrWithoutAccents_anyWordOrder() {
        assertTrue(FoodSearch.matches("Phở bò", "pho bo"))
        assertTrue(FoodSearch.matches("Phở bò", "PHỞ BÒ"))     // non-ASCII upper case (SQL LIKE misses this)
        assertTrue(FoodSearch.matches("Phở bò", "bo pho"))
        assertTrue(FoodSearch.matches("Bún bò Huế", "bun hue"))
        assertFalse(FoodSearch.matches("Phở gà", "pho bo"))
        assertTrue(FoodSearch.matches("Anything", "  "))
    }

    @Test fun search_putsPrefixMatchesFirst() {
        val names = listOf("Bánh phở cuốn", "Phở gà", "Phở bò", "Gia vị phở bò")
        assertEquals(listOf("Phở bò", "Gia vị phở bò"), FoodSearch.search(names, "pho bo") { it })
        val pho = FoodSearch.search(names, "pho") { it }
        assertEquals(setOf("Phở bò", "Phở gà"), pho.take(2).toSet())          // name starts with the query
        assertEquals(setOf("Bánh phở cuốn", "Gia vị phở bò"), pho.drop(2).toSet()) // a later word starts with it
    }
}
