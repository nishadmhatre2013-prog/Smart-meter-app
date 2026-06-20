package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Mahavithan Smart Meter Lite", appName)
  }

  @Test
  fun `verify normal account limit and premium account unlimited meters`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MeterScannerViewModel(app)
    
    // Ensure fresh state by logging out
    viewModel.logout()
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    
    // Switch simulation mode on, which generates 3 virtual simulated devices
    viewModel.toggleSimulationMode(true)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    
    // Since default is Guest (Normal tier), discoveredMeters should be restricted to exactly 1 item
    assertEquals(1, viewModel.discoveredMeters.value.size)
    
    // Elevate to Premium tier
    viewModel.login("premium@smartmeter.com", AccountTier.PREMIUM)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    
    // Now the limitation is lifted, and we see all 3 devices
    assertEquals(3, viewModel.discoveredMeters.value.size)
    
    // Revert/Log out back to Guest/Normal
    viewModel.logout()
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    
    // It should immediately fall back and restrict listing to exactly 1 device
    assertEquals(1, viewModel.discoveredMeters.value.size)
  }

  @Test
  fun `verify custom account registration and persistent check`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MeterScannerViewModel(app)
    
    // Log out first
    viewModel.logout()
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    
    // Set simulation mode to generate 3 meters
    viewModel.toggleSimulationMode(true)
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    
    val email = "jane.doe@example.com"
    val pass = "mysecurepassword"
    
    // Attempt login with unregistered normal email, should succeed as NORMAL directly
    val loginBeforeRegister = viewModel.verifyAndLogin(email, pass)
    assertEquals(true, loginBeforeRegister)
    assertEquals(email, viewModel.currentUser.value?.email)
    assertEquals(AccountTier.NORMAL, viewModel.currentUser.value?.tier)
    
    // As a Normal account, list must be capped at 1 meter
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    assertEquals(1, viewModel.discoveredMeters.value.size)
    
    // Register and login with a premium account to verify premium access
    val premiumEmail = "boss@company.com"
    val premiumPass = "premium123"
    viewModel.registerUser(premiumEmail, premiumPass, AccountTier.PREMIUM)
    
    val loginAfterAdmin = viewModel.verifyAndLogin(premiumEmail, premiumPass)
    assertEquals(true, loginAfterAdmin)
    assertEquals(premiumEmail, viewModel.currentUser.value?.email)
    assertEquals(AccountTier.PREMIUM, viewModel.currentUser.value?.tier)
    
    // Premium account should see all 3 devices
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    assertEquals(3, viewModel.discoveredMeters.value.size)
  }
}
