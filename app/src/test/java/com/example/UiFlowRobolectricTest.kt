package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.ui.DashboardActivity
import com.example.ui.LoginActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiFlowRobolectricTest {

    @Before
    fun ensureFirebaseInitialized() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            val initialized = FirebaseApp.initializeApp(context)
            if (initialized == null) {
                FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApplicationId("1:312314280256:android:2ee2092c9f42298dcaa2a5")
                        .setApiKey("AIzaSyDummyKeyForUnitTestOnly1234567890")
                        .setProjectId("gen-lang-client-0697147962")
                        .build()
                )
            }
        }
    }

    @Test
    fun `login activity displays Google sign in button`() {
        val controller = Robolectric.buildActivity(LoginActivity::class.java).setup()
        val activity = controller.get()

        val signInButton = activity.findViewById<MaterialButton>(R.id.btnGoogleSignIn)
        assertNotNull(signInButton)
        assertEquals(activity.getString(R.string.btn_sign_in_google), signInButton.text.toString())
    }

    @Test
    fun `unauthenticated dashboard redirects to login activity`() {
        val controller = Robolectric.buildActivity(DashboardActivity::class.java).create()
        val activity = controller.get()

        val nextIntent: Intent? = shadowOf(activity).nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals(LoginActivity::class.java.name, nextIntent?.component?.className)
        assertTrue(activity.isFinishing)
    }
}
