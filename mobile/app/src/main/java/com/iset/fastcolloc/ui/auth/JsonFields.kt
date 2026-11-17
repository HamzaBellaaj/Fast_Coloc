package com.iset.fastcolloc.ui.auth

import com.google.gson.JsonObject
fun JsonObject.string(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
fun JsonObject.long(key: String): Long? = get(key)?.takeUnless { it.isJsonNull }?.asLong
fun JsonObject.list(key: String): String = getAsJsonArray(key)?.joinToString(", ") { it.asString } ?: ""
fun splitValues(value: String): List<String> = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().also {
    require(it.size <= 50 && it.all { item -> item.length <= 100 }) { "50 valeurs maximum, 100 caractères par valeur." }
}
