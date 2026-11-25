package com.healthapp.data.food

import android.content.Context
import com.google.gson.Gson
import com.healthapp.data.local.entity.FoodEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Built-in Vietnamese dishes for food search, available offline.
 * Nutrients per 100 g come from `assets/vn-foods.json`, a copy of `server/data/vn-foods.json` (the same values
 * the food-photo AI uses; a unit test keeps the two identical). Each dish is offered as one typical serving.
 */
@Singleton
class VietnameseFoodLibrary @Inject constructor(@ApplicationContext private val context: Context) {

    private class Row(val key: String = "", val kcal: Double = 0.0, val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0)
    private class FileShape(val foods: List<Row> = emptyList())

    val foods: List<FoodEntity> by lazy {
        val rows = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { Gson().fromJson(it, FileShape::class.java) }.foods
        rows.mapNotNull { r -> DISHES[r.key]?.let { (name, grams) -> toEntity(r.key, name, grams, r.kcal, r.protein, r.carbs, r.fat) } }
    }

    companion object {
        const val ASSET = "vn-foods.json"
        const val SOURCE = "VN_LIBRARY"

        /** Food-table key → (display name, typical serving in grams as served, broth included). */
        val DISHES: Map<String, Pair<String, Int>> = mapOf(
            "pho bo (beef pho, bowl incl. broth)" to ("Phở bò" to 500),
            "pho ga (chicken pho, bowl incl. broth)" to ("Phở gà" to 500),
            "bun bo hue (bowl incl. broth)" to ("Bún bò Huế" to 550),
            "hu tieu (noodle soup incl. broth)" to ("Hủ tiếu" to 500),
            "bun cha (grilled pork, noodles, dipping sauce)" to ("Bún chả" to 450),
            "bun tuoi (fresh rice vermicelli)" to ("Bún tươi" to 150),
            "com tam suon (broken rice with grilled pork chop)" to ("Cơm tấm sườn" to 400),
            "banh mi thit (filled baguette sandwich)" to ("Bánh mì thịt" to 200),
            "banh mi khong (plain baguette)" to ("Bánh mì không" to 80),
            "xoi (sticky rice)" to ("Xôi" to 200),
            "chao (rice porridge, plain)" to ("Cháo trắng" to 300),
            "chao ga (chicken rice porridge)" to ("Cháo gà" to 350),
            "banh cuon (steamed rice rolls)" to ("Bánh cuốn" to 250),
            "goi cuon (fresh spring rolls)" to ("Gỏi cuốn (2 cuốn)" to 150),
            "cha gio / nem ran (fried spring rolls)" to ("Chả giò / Nem rán (3 cuốn)" to 100),
            "banh xeo (crispy pancake)" to ("Bánh xèo" to 200),
            "banh bao (steamed bun)" to ("Bánh bao" to 150),
            "thit kho trung (braised pork and egg)" to ("Thịt kho trứng" to 150),
            "ca kho (braised fish)" to ("Cá kho" to 150),
            "canh chua (sour soup incl. broth)" to ("Canh chua" to 300),
            "canh rau (clear vegetable soup)" to ("Canh rau" to 250),
            "rau muong xao toi (stir-fried morning glory)" to ("Rau muống xào tỏi" to 150),
            "rau luoc (boiled vegetables)" to ("Rau luộc" to 150),
            "ga luoc (boiled chicken with skin)" to ("Gà luộc" to 150),
            "thit lon luoc (boiled pork belly)" to ("Thịt lợn luộc" to 100),
            "suon nuong (grilled pork ribs/chop)" to ("Sườn nướng" to 120),
            "thit bo xao (stir-fried beef)" to ("Thịt bò xào" to 150),
            "dau phu chien (fried tofu)" to ("Đậu phụ chiên" to 100),
            "dau phu sot ca chua (tofu in tomato sauce)" to ("Đậu phụ sốt cà chua" to 150),
            "trung chien (fried egg / omelette)" to ("Trứng chiên" to 100),
            "trung luoc (boiled egg)" to ("Trứng luộc (1 quả)" to 50),
            "mien xao (stir-fried glass noodles)" to ("Miến xào" to 250),
            "mi xao (stir-fried egg noodles)" to ("Mì xào" to 300),
            "nem nuong (grilled pork sausage)" to ("Nem nướng" to 100),
            "che (sweet bean dessert)" to ("Chè" to 250),
            "ca phe sua da (iced coffee with condensed milk)" to ("Cà phê sữa đá" to 200),
            "tra sua tran chau (bubble milk tea)" to ("Trà sữa trân châu" to 500),
            "nuoc mia (sugarcane juice)" to ("Nước mía" to 300),
            "sua dau nanh (sweetened soy milk)" to ("Sữa đậu nành" to 250),
            "xoai chin (ripe mango)" to ("Xoài chín" to 200),
            "chuoi (banana)" to ("Chuối" to 120),
            "thanh long (dragon fruit)" to ("Thanh long" to 200)
        )

        /** One serving: per-100 g values × grams / 100. Stable id from the key, so re-inserting replaces. */
        fun toEntity(key: String, name: String, grams: Int, kcal: Double, protein: Double, carbs: Double, fat: Double): FoodEntity {
            val k = grams / 100.0
            fun r1(x: Double) = (x * k * 10).roundToInt() / 10.0
            val slug = key.substringBefore(" (").substringBefore(" /").replace(' ', '_')
            return FoodEntity("vn_$slug", name, (kcal * k).roundToInt(), r1(protein), r1(carbs), r1(fat), 0.0, 0.0, 0, grams, SOURCE)
        }
    }
}
