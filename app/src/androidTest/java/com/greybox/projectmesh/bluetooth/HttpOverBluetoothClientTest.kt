//package com.greybox.projectmesh.bluetooth
//
//import android.bluetooth.BluetoothAdapter
//import android.bluetooth.BluetoothManager
//import android.content.Context
//import androidx.test.core.app.ApplicationProvider
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import org.junit.Assert.assertTrue
//import org.junit.Assume.assumeTrue
//import org.junit.Test
//import org.junit.runner.RunWith
//import rawhttp.core.RawHttp
//import rawhttp.core.body.StringBody
//import rawhttp.core.RawHttpRequest
//import java.util.UUID
//
//@RunWith(AndroidJUnit4::class)
//class HttpOverBluetoothClientTest {
//
//    private val appContext: Context = ApplicationProvider.getApplicationContext()
//    private val rawHttp = RawHttp()
//
//    // Minimal fake allocation client that always yields "busy" UUID.
//    // (Meshrabiya defines UUID_BUSY; your production client returns it when the server is busy.)
//    private class FakeBusyUuidAllocator(
//        val uuidToReturn: UUID
//    ) : UuidAllocationClient(
//        appContext = ApplicationProvider.getApplicationContext(),
//        onLog = /* pass a real logger later */ object : com.ustadmobile.meshrabiya.log.MNetLogger {},
//        clientNodeAddr = 0
//    ) {
//        override suspend fun requestUuidAllocation(remoteAddress: String, uuidMask: UUID): UUID {
//            return uuidToReturn
//        }
//    }
//
//    /* Example Test
//    @Test
//    fun constructClient_whenBluetoothEnvPresent_orSkip() {
//        val manager = appContext.getSystemService(BluetoothManager::class.java)
//        // If the environment has no Bluetooth, skip (gives us a green skip instead of red fail)
//        assumeTrue("BluetoothManager not available on this device/emulator", manager != null)
//
//        val client = HttpOverBluetoothClient(
//            appContext = appContext,
//            rawHttp = rawHttp,
//            logger = object : com.ustadmobile.meshrabiya.log.MNetLogger {}, // no-op stub logger
//            clientNodeAddr = 1
//        )
//
//        // Just ensure it's constructed; add real assertions later
//        assertTrue(client is HttpOverBluetoothClient)
//    }
//    */
//}
