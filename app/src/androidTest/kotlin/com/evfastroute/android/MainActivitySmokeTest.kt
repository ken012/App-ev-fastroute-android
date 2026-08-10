package com.evfastroute.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun plannerLaunchesAndShowsPrimaryFlow() {
        val onboardingButtons = compose.onAllNodesWithText("Get Started")
        if (onboardingButtons.fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("EV FastRoute").assertIsDisplayed()
            compose.onNodeWithText("Get Started").performClick()
            compose.waitForIdle()
        }

        compose.onNodeWithText("Trip Planner").assertIsDisplayed()
        compose.onNodeWithText("Start").assertIsDisplayed()
        compose.onNodeWithText("Destination").assertIsDisplayed()
        compose.onNodeWithTag("planner_list").performScrollToNode(hasText("Find Fastest Route"))
        compose.onNodeWithText("Find Fastest Route").assertIsDisplayed()

        compose.onNodeWithTag("tab_garage").performClick()
        compose.onNodeWithText("Garage").assertIsDisplayed()
    }
}
