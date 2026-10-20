package com.consuelomerino.tierracolorada.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.consuelomerino.tierracolorada.MainActivity
import com.consuelomerino.tierracolorada.objects.ObjectType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/** With "Remove animations" on, nothing in the garden moves, so the screen must stop drawing frames. */
class ReducedMotionTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    /** Turns animations off before the activity starts (the screen reads the setting once) and restores them after. */
    private val animationsOff = object : ExternalResource() {
        private var previous = "1"
        override fun before() {
            previous = shell("settings get global animator_duration_scale").trim().takeIf { it != "null" } ?: "1"
            shell("settings put global animator_duration_scale 0")
        }
        override fun after() {
            shell("settings put global animator_duration_scale $previous")
        }
    }

    @get:Rule val rules: RuleChain = RuleChain.outerRule(animationsOff).around(composeRule)

    private val vm get() = composeRule.activity.viewModel

    @Test fun gardenGoesIdleWithReducedMotion() {
        assertTrue("the screen kept drawing frames", becomesIdle())
    }

    @Test fun tappedRippleShowsAndTheGardenGoesIdleAgain() {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.runOnUiThread { vm.objects.forEach { vm.deleteObject(it.id) } }
        composeRule.onNodeWithTag("tool_place").performClick()
        composeRule.runOnUiThread { vm.addObject(ObjectType.POND, 180f, 300f) }
        composeRule.mainClock.advanceTimeBy(500)
        val pond = vm.objects.single { it.type == ObjectType.POND }
        composeRule.onNodeWithTag("garden").performTouchInput {
            val cell = width / 360f
            click(Offset(pond.x * cell, pond.y * cell))
        }
        composeRule.mainClock.advanceTimeBy(500)
        assertTrue(vm.pondRipples.active(System.currentTimeMillis()).isNotEmpty())
        assertTrue("the screen kept drawing frames after a ripple", becomesIdle())
    }

    private fun becomesIdle(): Boolean {
        composeRule.mainClock.autoAdvance = true
        return runBlocking { withTimeoutOrNull(5_000) { composeRule.awaitIdle() } } != null
    }

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }
}
