package com.cruisepass.verify.ui

import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val tag: String, val label: String, val code: String) {
    ENGLISH("en", "English", "ENG"),
    RUSSIAN("ru", "Русский", "RUS"),
    TURKISH("tr", "Türkçe", "TUR"),
    ITALIAN("it", "Italiano", "ITA"),
    GREEK("el", "Ελληνικά", "ELL"),
    EGYPTIAN("ar-EG", "مصرى", "ARZ"),
    ;

    fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, tag)
            .commit()
    }

    companion object {
        private const val PREFS = "ui"
        private const val KEY = "language"

        fun saved(context: Context): AppLanguage? {
            val tag = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return fromTag(tag)
        }

        fun fromTag(tag: String?): AppLanguage? = when (tag?.lowercase(Locale.ROOT)) {
            "en" -> ENGLISH
            "ru" -> RUSSIAN
            "tr" -> TURKISH
            "it" -> ITALIAN
            "el" -> GREEK
            "ar", "ar-eg" -> EGYPTIAN
            else -> null
        }

        fun current(context: Context): AppLanguage = saved(context) ?: ENGLISH

        fun apply(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applyPlatformLocale(context)
            }
        }

        fun wrap(base: Context): Context {
            val language = current(base)
            val locale = Locale.forLanguageTag(language.tag)
            val config = android.content.res.Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }

        private fun applyPlatformLocale(context: Context) {
            val manager = context.getSystemService(android.app.LocaleManager::class.java) ?: return
            val desired = LocaleList.forLanguageTags(current(context).tag)
            if (manager.applicationLocales.toLanguageTags() == desired.toLanguageTags()) return
            manager.applicationLocales = desired
        }
    }
}
