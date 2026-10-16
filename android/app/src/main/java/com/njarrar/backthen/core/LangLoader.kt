package com.njarrar.backthen.core

import android.content.Context
import java.util.Locale

/**
 * Picks the app's language from the phone's settings and loads its file. The files in
 * `lang/` at the top of the repo are packed into the app as assets (see build.gradle.kts).
 */
fun startLanguage(context: Context, only: String? = null) {
    val assets = context.assets
    val available = assets.list("").orEmpty().filter { it.endsWith(".xml") }.map { it.removeSuffix(".xml") }
    val locales = context.resources.configuration.locales
    val preferred = only?.let(::listOf) ?: (0 until locales.size()).map { locales[it].toLanguageTag() }
    val (code, tag) = L.pick(preferred, available)
    fun load(c: String) = runCatching { assets.open("$c.xml").use { Strings.parse(c, it) } }.getOrNull()
    val english = load("en") ?: Strings("en")
    L.use(english, if (code == "en") english else load(code) ?: english, Locale.forLanguageTag(tag))
}
