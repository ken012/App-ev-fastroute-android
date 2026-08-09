package com.evfastroute.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        compose.onNodeWithText("EV FastRoute").assertIsDisplayed()
        compose.onNodeWithText("Start").assertIsDisplayed()
        compose.onNodeWithText("Destination").assertIsDisplayed()
        compose.onNodeWithText("Find Route").assertIsDisplayed()
    }
}
