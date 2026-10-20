package com.consuelomerino.tierracolorada.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertTrue
import kotlin.math.abs
import com.consuelomerino.tierracolorada.MainActivity
import com.consuelomerino.tierracolorada.objects.ObjectType
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GardenScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    // The garden's ambient life animates forever, so Compose never goes idle on its own:
    // drive the clock by hand instead.
    @Before fun manualClock() {
        rule.mainClock.autoAdvance = false
        settle()
    }

    private fun settle() = rule.mainClock.advanceTimeBy(500)

    @Test fun rakeIsSelectedByDefault() {
        rule.onNodeWithTag("tool_rake").assertIsSelected()
    }

    @Test fun selectingSmoothHighlightsIt() {
        rule.onNodeWithTag("tool_smooth").performClick()
        settle()
        rule.onNodeWithTag("tool_smooth").assertIsSelected()
        rule.onNodeWithTag("tool_rake").assertIsNotSelected()
    }

    @Test fun clearAsksForConfirmation() {
        rule.onNodeWithTag("tool_clear").performClick()
        settle()
        rule.onNodeWithTag("clear_dialog").assertIsDisplayed()
    }

    @Test fun pondTrayItemDisabledWhenPondExists() {
        rule.onNodeWithTag("tool_place").performClick()
        settle()
        rule.runOnUiThread { rule.activity.viewModel.addObject(ObjectType.POND, 180f, 400f) }
        settle()
        rule.onNodeWithTag("tray_pond").assertIsNotEnabled()
    }

    private val vm get() = rule.activity.viewModel

    /** Clears the garden's objects so each gesture test starts from a known state. */
    private fun removeAllObjects() = rule.runOnUiThread { vm.objects.forEach { vm.deleteObject(it.id) } }

    @Test fun tappingThePondInPlaceModeMakesARipple() {
        removeAllObjects()
        rule.onNodeWithTag("tool_place").performClick()
        rule.runOnUiThread { vm.addObject(ObjectType.POND, 180f, 300f) }
        settle()
        val pond = vm.objects.single { it.type == ObjectType.POND }
        rule.onNodeWithTag("garden").performTouchInput {
            val cell = width / 360f
            click(Offset(pond.x * cell, pond.y * cell))
        }
        settle()
        assertTrue(vm.pondRipples.active(System.currentTimeMillis()).isNotEmpty())
    }

    @Test fun liftingTheFirstFingerAfterAPinchDoesNotMakeTheObjectJump() {
        removeAllObjects()
        rule.onNodeWithTag("tool_place").performClick()
        rule.runOnUiThread { vm.addObject(ObjectType.STONE, 180f, 300f) }
        settle()
        val stone = vm.objects.single()
        rule.onNodeWithTag("garden").performTouchInput {
            val cell = width / 360f
            val onStone = Offset(stone.x * cell, stone.y * cell)
            down(0, onStone)
            down(1, onStone + Offset(250f, 0f))
            moveBy(1, Offset(40f, 0f)) // pinch a little
            up(0) // the finger on the stone lifts first
            moveBy(1, Offset(10f, 0f))
            up(1)
        }
        settle()
        val after = vm.objects.single()
        assertTrue("stone jumped from ${stone.x} to ${after.x}", abs(after.x - stone.x) < 5f && abs(after.y - stone.y) < 5f)
    }
}
