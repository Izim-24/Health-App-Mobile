package com.healthapp.data.food

import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VietnameseFoodLibraryTest {
    private val asset = File("src/main/assets/${VietnameseFoodLibrary.ASSET}")
    private val server = File("../server/data/vn-foods.json")

    @Test fun assetIsACopyOfTheServerTable() {
        // The app and the food-photo AI must use the same numbers. After editing server/data/vn-foods.json,
        // copy it to app/src/main/assets/.
        assertArrayEquals(server.readBytes(), asset.readBytes())
    }

    @Test fun everyDishHasANameAndServing_andNoneAreStale() {
        val keys = JsonParser().parse(asset.readText()).asJsonObject.getAsJsonArray("foods").map { it.asJsonObject.get("key").asString }.toSet()
        assertEquals(keys, VietnameseFoodLibrary.DISHES.keys)
        VietnameseFoodLibrary.DISHES.values.forEach { (name, grams) ->
            assertTrue(name, name.isNotBlank() && grams in 30..700)
        }
        assertEquals(VietnameseFoodLibrary.DISHES.size, VietnameseFoodLibrary.DISHES.values.map { it.first }.toSet().size)
    }

    @Test fun servingScalesPer100gValues() {
        val pho = VietnameseFoodLibrary.toEntity("pho bo (beef pho, bowl incl. broth)", "Phở bò", 500, 90.0, 5.5, 11.0, 2.5)
        assertEquals("vn_pho_bo", pho.id)
        assertEquals(450, pho.calories)
        assertEquals(27.5, pho.protein, 0.01)
        assertEquals(55.0, pho.carbohydrates, 0.01)
        assertEquals(12.5, pho.fat, 0.01)
        assertEquals(500, pho.servingSize)
    }
}
