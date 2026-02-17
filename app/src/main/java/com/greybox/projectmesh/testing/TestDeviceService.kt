package com.greybox.projectmesh.testing

import android.util.Log
import com.greybox.projectmesh.GlobalApp
import com.greybox.projectmesh.GlobalApp.GlobalUserRepo.userRepository
import com.greybox.projectmesh.messaging.data.entities.Message
import kotlinx.coroutines.runBlocking
import java.net.InetAddress

/**
 * Service for managing test devices in the mesh network.
 *
 * Handles initialization of both online and offline test devices,
 * provides utility methods for identifying test devices, and
 * generating echo responses for testing purposes.
 */
class TestDeviceService {
    companion object {
        /** IP and name for the online test device */
        const val TEST_DEVICE_IP = "192.168.0.99"
        const val TEST_DEVICE_NAME = "Test Echo Device (Online)"

        /** IP and name for the offline test device */
        const val TEST_DEVICE_IP_OFFLINE = "192.168.0.98"
        const val TEST_DEVICE_NAME_OFFLINE = "Test Echo Device (Offline)"

        private var isInitialized = false
        private var offlineDeviceInitialized = false

        /**
         * Initializes the online test device if it hasn't been set up already.
         */
        fun initialize() {
            try {
                if (!isInitialized) {
                    runBlocking {
                        val existingUser = userRepository.getUserByIp(TEST_DEVICE_IP)
                        if (existingUser == null) {
                            // Insert a new user with a temporary UUID
                            val pseudoUuid = "temp-$TEST_DEVICE_IP"
                            userRepository.insertOrUpdateUser(
                                uuid = pseudoUuid,
                                name = TEST_DEVICE_NAME,
                                address = TEST_DEVICE_IP
                            )
                        } else {
                            // Update the name of an existing user with this IP
                            userRepository.insertOrUpdateUser(
                                uuid = existingUser.uuid,
                                name = TEST_DEVICE_NAME,
                                address = existingUser.address
                            )
                        }
                    }
                    isInitialized = true
                    Log.d("TestDeviceService", "Test device initialized successfully with IP: $TEST_DEVICE_IP")

                    // Initialize the offline test device
                    initializeOfflineDevice()
                }
            } catch (e: Exception) {
                Log.e("TestDeviceService", "Failed to initialize test device", e)
            }
        }

        /**
         * Initializes the offline test device if it hasn't been set up already.
         */
        fun initializeOfflineDevice() {
            try {
                if (!offlineDeviceInitialized) {
                    runBlocking {
                        val existingUser = userRepository.getUserByIp(TEST_DEVICE_IP_OFFLINE)
                        if (existingUser == null) {
                            // Create a new offline test device with null address
                            val pseudoUuid = "temp-offline-$TEST_DEVICE_IP_OFFLINE"
                            userRepository.insertOrUpdateUser(
                                uuid = pseudoUuid,
                                name = TEST_DEVICE_NAME_OFFLINE,
                                address = null // null address indicates offline
                            )
                        } else {
                            // Update existing offline device to ensure it remains offline
                            userRepository.insertOrUpdateUser(
                                uuid = existingUser.uuid,
                                name = TEST_DEVICE_NAME_OFFLINE,
                                address = null
                            )
                        }
                    }
                    offlineDeviceInitialized = true
                    Log.d("TestDeviceService", "Offline test device initialized successfully")
                }
            } catch (e: Exception) {
                Log.e("TestDeviceService", "Failed to initialize offline test device", e)
            }
        }

        /** Checks if the given address is the online test device */
        fun isOnlineTestDevice(address: InetAddress): Boolean {
            return address.hostAddress == TEST_DEVICE_IP
        }

        /** Checks if the given address is the offline test device */
        fun isOfflineTestDevice(address: InetAddress): Boolean {
            return address.hostAddress == TEST_DEVICE_IP_OFFLINE
        }

        /** Returns the InetAddress of the online test device */
        fun getTestDeviceAddress(): InetAddress {
            return InetAddress.getByName(TEST_DEVICE_IP)
        }

        /** Checks if the given address matches the online test device */
        fun isTestDevice(address: InetAddress): Boolean {
            return address.hostAddress == TEST_DEVICE_IP
        }

        /**
         * Generates an echo response message for testing purposes.
         *
         * @param originalMessage the original message to echo
         * @return a new Message object containing the echo content
         */
        fun createEchoResponse(originalMessage: Message): Message {
            return Message(
                id = 0,
                dateReceived = System.currentTimeMillis(),
                content = "Echo: ${originalMessage.content}",
                sender = TEST_DEVICE_NAME,
                chat = originalMessage.chat
            )
        }
    }
}
