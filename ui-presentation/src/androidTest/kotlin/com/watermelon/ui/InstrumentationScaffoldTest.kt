package com.watermelon.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scaffold smoke test for commit 00: proves the ui-presentation instrumentation
 * pipeline (runner + emulator + compose test rule) executes on CI. Later player
 * regression tests (commits 01-04) build on this scaffold.
 */
@RunWith(AndroidJUnit4::class)
class InstrumentationScaffoldTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun composeTestInfrastructureBoots() {
        composeRule.setContent {
            Text("scaffold")
        }
        composeRule.onNodeWithText("scaffold").assertIsDisplayed()
    }
}
