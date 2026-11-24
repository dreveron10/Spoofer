package com.diego.mocklocation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Favorite(val name: String, val lat: Double, val lon: Double)

/** Saved spots, stored as a small JSON array in SharedPreferences. */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)

    fun all(): List<Favorite> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Favorite(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(favorite: Favorite) = save(all() + favorite)

    fun removeAt(index: Int) {
        val list = all().toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            save(list)
        }
    }

    private fun save(list: List<Favorite>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("lat", it.lat)
                    .put("lon", it.lon)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "items"
    }
}
