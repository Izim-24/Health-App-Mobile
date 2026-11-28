package com.healthapp.data.local

/**
 * Enum names stored as text (Room rows, imports). A name this version doesn't know — written by another version,
 * a restore or an import — must not crash a whole list, so reads use these instead of valueOf.
 */
inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E = enumOrNull<E>(name) ?: default
