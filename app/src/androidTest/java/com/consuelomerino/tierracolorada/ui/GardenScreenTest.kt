package com.consuelomerino.tierracolorada.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.consuelomerino.tierracolorada.MainActivity
import com.consuelomerino.tierracolorada.objects.ObjectType
import org.junit.Rule
import org.junit.Test

class GardenScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun rakeIsSelectedByDefault() {
        rule.onNodeWithTag("tool_rake").assertIsSelected()
    }

    @Test fun selectingSmoothHighlightsIt() {
        rule.onNodeWithTag("tool_smooth").performClick().assertIsSelected()
        rule.onNodeWithTag("tool_rake").assertIsNotSelected()
    }

    @Test fun clearAsksForConfirmation() {
        rule.onNodeWithTag("tool_clear").performClick()
        rule.onNodeWithTag("clear_dialog").assertIsDisplayed()
    }

    @Test fun pondTrayItemDisabledWhenPondExists() {
        rule.onNodeWithTag("tool_place").performClick()
        rule.runOnUiThread { rule.activity.viewModel.addObject(ObjectType.POND, 180f, 400f) }
        rule.onNodeWithTag("tray_pond").assertIsNotEnabled()
    }
}
