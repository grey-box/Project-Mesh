package com.greybox.projectmesh.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class LoggerTest {

    @Test
    fun buildTag_prefixesWithMeshChat() {
        val result = Logger.buildTag("ChatScreen")
        assertEquals("MeshChat_ChatScreen", result)
    }

    @Test
    fun buildTag_handlesEmptyTag() {
        val result = Logger.buildTag("")
        assertEquals("MeshChat_", result)
    }

    @Test
    fun buildCriticalTag_appendsCriticalSuffix() {
        val result = Logger.buildCriticalTag("Network")
        assertEquals("MeshChat_Network_CRITICAL", result)
    }

    @Test
    fun buildCriticalTag_handlesEmptyTag() {
        val result = Logger.buildCriticalTag("")
        assertEquals("MeshChat__CRITICAL", result)
    }
}
