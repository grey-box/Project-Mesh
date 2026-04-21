package com.greybox.projectmesh.testing

import com.greybox.projectmesh.GlobalApp.GlobalUserRepo.userRepository
import com.greybox.projectmesh.messaging.data.entities.Message
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.net.InetAddress

class TestDeviceService {
    companion object {
        const val TEST_DEVICE_IP = "192.168.0.99"
        const val TEST_DEVICE_NAME = "Test Echo Device (Online)"
        const val TEST_DEVICE_IP_OFFLINE = "192.168.0.98"
        const val TEST_DEVICE_NAME_OFFLINE = "Test Echo Device (Offline)"

        private var isInitialized = false
        private var offlineDeviceInitialized = false

        fun initialize() {
            try {
                if (!isInitialized) {
                    runBlocking {
                        val existingUser = userRepository.getUserByIp(TEST_DEVICE_IP)
                        if (existingUser == null) {
                            // If there's no user with this IP, insert one with a "temp" UUID
                            val pseudoUuid = "temp-$TEST_DEVICE_IP"
                            userRepository.insertOrUpdateUser(
                                uuid = pseudoUuid,
                                name = TEST_DEVICE_NAME,
                                address = TEST_DEVICE_IP
                            )
                        } else {
                            // If a user with this IP already exists, just update the name
                            // (keeping the same uuid and address)
                            userRepository.insertOrUpdateUser(
                                uuid = existingUser.uuid,
                                name = TEST_DEVICE_NAME,
                                address = existingUser.address
                            )
                        }
                    }
                    isInitialized = true
                    Timber.tag("TestDeviceService").d("Test device initialized successfully with " +
                            "IP: $TEST_DEVICE_IP")

                    //initialize offline test device
                    initializeOfflineDevice()
                }
            } catch (e: Exception) {
                Timber.tag("TestDeviceService").e(e,"Failed to initialize test device")
            }
        }

        fun initializeOfflineDevice() {
            try {
                if (!offlineDeviceInitialized) {
                    runBlocking {
                        val existingUser = userRepository.getUserByIp(TEST_DEVICE_IP_OFFLINE)
                        if (existingUser == null) {
                            // Create a new offline test device
                            val pseudoUuid = "temp-offline-$TEST_DEVICE_IP_OFFLINE"
                            userRepository.insertOrUpdateUser(
                                uuid = pseudoUuid,
                                name = TEST_DEVICE_NAME_OFFLINE,
                                address = null // NULL address means offline
                            )
                        } else {
                            // Update existing offline device
                            userRepository.insertOrUpdateUser(
                                uuid = existingUser.uuid,
                                name = TEST_DEVICE_NAME_OFFLINE,
                                address = null // Make sure it's offline
                            )
                        }
                    }
                    offlineDeviceInitialized = true
                    Timber.tag("TestDeviceService").d("Offline test device initialized successfully")
                }
            } catch (e: Exception) {
                Timber.tag("TestDeviceService").e(e, "Failed to initialize offline test device")
            }
        }

        fun isOnlineTestDevice(address: InetAddress): Boolean {
            return address.hostAddress == TEST_DEVICE_IP
        }

        fun isOfflineTestDevice(address: InetAddress): Boolean {
            return address.hostAddress == TEST_DEVICE_IP_OFFLINE
        }


        fun getTestDeviceAddress(): InetAddress {
            return InetAddress.getByName(TEST_DEVICE_IP)
        }

        fun isTestDevice(address: InetAddress): Boolean {
            return address.hostAddress == TEST_DEVICE_IP
        }

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