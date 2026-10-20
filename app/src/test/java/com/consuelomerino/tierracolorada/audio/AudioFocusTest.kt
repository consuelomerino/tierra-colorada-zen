package com.consuelomerino.tierracolorada.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusTest {
    @Test fun mutedAppDoesNotTakeFocusFromOtherApps() = assertFalse(wantsAudioFocus(foreground = true, muted = true))
    @Test fun backgroundAppDoesNotHoldFocus() = assertFalse(wantsAudioFocus(foreground = false, muted = false))
    @Test fun visibleUnmutedAppWantsFocus() = assertTrue(wantsAudioFocus(foreground = true, muted = false))
}
