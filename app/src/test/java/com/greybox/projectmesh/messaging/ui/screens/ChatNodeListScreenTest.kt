package com.greybox.projectmesh.messaging.ui.screens

import android.os.Build
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.greybox.projectmesh.viewModel.NetworkScreenModel
import com.greybox.projectmesh.viewModel.NetworkScreenViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
@LooperMode(LooperMode.Mode.PAUSED)
class ChatNodeListScreenTest {

    @Test
    fun chatNodeListScreen_composesWithoutCrashing() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        val mockViewModel = mockk<NetworkScreenViewModel>(relaxed = true)
        val stateFlow = MutableStateFlow(NetworkScreenModel(allNodes = emptyMap()))
        every { mockViewModel.uiState } returns stateFlow

        activity.runOnUiThread {
            activity.setContent {
                ChatNodeListScreen(
                    onNodeSelected = {},
                    viewModel = mockViewModel
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
    }

    @Test
    fun chatNodeListScreen_handlesNodeClickLambda_withoutCrash() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()

        val mockViewModel = mockk<NetworkScreenViewModel>(relaxed = true)
        val stateFlow = MutableStateFlow(NetworkScreenModel(allNodes = emptyMap()))
        every { mockViewModel.uiState } returns stateFlow

        var selectedNode: String? = null

        activity.runOnUiThread {
            activity.setContent {
                ChatNodeListScreen(
                    onNodeSelected = { selectedNode = it },
                    viewModel = mockViewModel
                )
            }
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity)
        assertNull(selectedNode)
    }
}