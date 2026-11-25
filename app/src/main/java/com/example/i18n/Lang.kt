package com.example.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Current language for the newer screens (home, order, registration, support).
 * Set from the app root when the user switches RU / KG; reading it recomposes the screen.
 */
object Lang {
    var kg by mutableStateOf(false)
}

/** Russian or Kyrgyz text depending on the chosen language. */
fun t(ru: String, kg: String): String = if (Lang.kg) kg else ru
