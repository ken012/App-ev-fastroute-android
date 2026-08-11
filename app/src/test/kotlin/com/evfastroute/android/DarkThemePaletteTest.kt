package com.evfastroute.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class DarkThemePaletteTest {
    @Test
    fun primaryAndSecondaryTextRemainReadableOnDarkSurfaces() {
        val blendedGlass = EvGlass.copy(alpha = 0.78f).compositeOver(EvBackgroundMiddle)

        assertTrue(contrastRatio(EvTextPrimary, EvBackgroundTop) >= 7.0)
        assertTrue(contrastRatio(EvTextPrimary, blendedGlass) >= 7.0)
        assertTrue(contrastRatio(EvMuted, blendedGlass) >= 4.5)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = first.luminance().toDouble()
        val secondLuminance = second.luminance().toDouble()
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }
}
