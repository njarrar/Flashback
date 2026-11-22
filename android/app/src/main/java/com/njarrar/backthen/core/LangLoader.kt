package com.njarrar.backthen.core

import android.content.Context
import android.util.Log
import java.util.Locale

@Volatile private var cachedAvailable: List<String>? = null
@Volatile private var loadedKey: Pair<String, String>? = null

/**
 * Picks the app's language from the phone's settings and loads its file. The files in
 * `lang/` at the top of the repo are packed into the app as assets (see build.gradle.kts).
 */
fun startLanguage(context: Context, only: String? = null) {
    val assets = context.assets
    val available = cachedAvailable
        ?: assets.list("").orEmpty().filter { it.endsWith(".xml") }.map { it.removeSuffix(".xml") }
            .also { if (it.isNotEmpty()) cachedAvailable = it }
    val locales = context.resources.configuration.locales
    val preferred = only?.let(::listOf) ?: (0 until locales.size()).map { locales[it].toLanguageTag() }
    val (code, tag) = L.pick(preferred, available)
    val key = code to tag
    if (loadedKey == key && L.english.strings.isNotEmpty() && L.lang.code == code) return
    fun load(c: String) = runCatching { assets.open("$c.xml").use { Strings.parse(c, it) } }
        .onFailure { Log.w("Flashback", "Could not read lang/$c.xml", it) }
        .getOrNull()
    val english = load("en") ?: Strings("en")
    L.use(english, if (code == "en") english else load(code) ?: english, Locale.forLanguageTag(tag))
    // Only remember a good load, so the next call tries again after a failure.
    loadedKey = if (english.strings.isNotEmpty()) key else null
}
