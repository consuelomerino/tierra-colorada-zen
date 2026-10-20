package com.consuelomerino.tierracolorada.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
import org.junit.Assert.assertFalse
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

    @Test fun actionsAreButtonsAndToolsAreTabs() {
        // TalkBack must announce Clear, Mute and Share as buttons, not as tabs that can be selected.
        val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val notSelectable = SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected)
        for (tag in listOf("tool_clear", "mute", "share")) {
            rule.onNodeWithTag(tag).assert(button).assert(notSelectable)
        }
        for (tag in listOf("tool_rake", "tool_place", "tool_smooth")) {
            rule.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
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

    @Test fun pinchingInAfterHittingMaxSizeShrinksRightAway() {
        removeAllObjects()
        rule.onNodeWithTag("tool_place").performClick()
        rule.runOnUiThread { vm.addObject(ObjectType.STONE, 180f, 300f) }
        settle()
        val stone = vm.objects.single()
        rule.onNodeWithTag("garden").performTouchInput {
            val cell = width / 360f
            val onStone = Offset(stone.x * cell, stone.y * cell)
            down(0, onStone)
            down(1, onStone + Offset(100f, 0f))
            moveBy(1, Offset(400f, 0f)) // spread to 5x: the stone stops at 2x
            moveBy(1, Offset(-100f, 0f)) // close a fifth of the way
        }
        settle()
        val scale = vm.objects.single().scale
        rule.onNodeWithTag("garden").performTouchInput { up(1); up(0) }
        settle()
        assertTrue("scale stuck at $scale", scale < 1.9f)
    }

    @Test fun holdingStillWithSmoothKeepsFlattening() {
        removeAllObjects()
        rule.onNodeWithTag("tool_smooth").performClick()
        rule.runOnUiThread { vm.heights!![180, 300] = -0.8f }
        settle()
        rule.onNodeWithTag("garden").performTouchInput {
            val cell = width / 360f
            down(Offset(180.5f * cell, 300.5f * cell))
        }
        rule.mainClock.advanceTimeBy(300) // about 18 frames with the finger still
        val depth = vm.heights!![180, 300]
        rule.onNodeWithTag("garden").performTouchInput { up() }
        settle()
        assertTrue("groove only smoothed to $depth", depth > -0.1f)
    }

    @Test fun cancelledRakeStrokeEndsTheStroke() {
        removeAllObjects()
        rule.onNodeWithTag("garden").performTouchInput {
            down(Offset(100f, 400f))
            moveBy(Offset(150f, 0f))
        }
        rule.mainClock.advanceTimeBy(100)
        assertTrue(vm.raking)
        rule.onNodeWithTag("garden").performTouchInput { cancel() }
        settle()
        assertFalse("still raking after the gesture was cancelled, so the scrape keeps looping", vm.raking)
    }

    @Test fun cancelledSmoothStrokeStopsSmoothing() {
        removeAllObjects()
        rule.onNodeWithTag("tool_smooth").performClick()
        settle()
        rule.onNodeWithTag("garden").performTouchInput { down(Offset(100f, 400f)) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("garden").performTouchInput { cancel() }
        settle()
        assertFalse(vm.smoothing)
    }
}
