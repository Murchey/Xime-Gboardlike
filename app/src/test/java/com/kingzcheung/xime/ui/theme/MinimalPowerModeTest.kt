package com.kingzcheung.xime.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinimalPowerModeTest {
    @Test fun restoresGlassPreferenceAfterModeIsDisabled() {
        MinimalPowerMode.enabled = false
        GlassEffect.enabled = true
        try {
            assertTrue(GlassEffect.enabled)
            MinimalPowerMode.enabled = true
            assertFalse(GlassEffect.enabled)
            MinimalPowerMode.enabled = false
            assertTrue(GlassEffect.enabled)
            MinimalPowerMode.enabled = true
            GlassEffect.enabled = false
            MinimalPowerMode.enabled = false
            assertFalse(GlassEffect.enabled)
        } finally {
            MinimalPowerMode.enabled = false
            GlassEffect.enabled = false
        }
    }
}
