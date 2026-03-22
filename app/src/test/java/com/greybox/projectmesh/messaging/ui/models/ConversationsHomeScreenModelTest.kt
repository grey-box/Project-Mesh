package com.greybox.projectmesh.messaging.ui.models

import com.greybox.projectmesh.messaging.data.entities.Conversation
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationsHomeScreenModelTest {

    @Test
    fun conversationsHomeScreenModel_usesDefaultValues() {
        val model = ConversationsHomeScreenModel()

        assertEquals(false, model.isLoading)
        assertEquals(emptyList<Conversation>(), model.conversations)
        assertNull(model.error)
    }

    @Test
    fun conversationsHomeScreenModel_setsValuesCorrectly() {
        val conversations = listOf(
            mockk<Conversation>(),
            mockk<Conversation>()
        )

        val model = ConversationsHomeScreenModel(
            isLoading = true,
            conversations = conversations,
            error = "Something went wrong"
        )

        assertEquals(true, model.isLoading)
        assertEquals(conversations, model.conversations)
        assertEquals("Something went wrong", model.error)
    }

    @Test
    fun conversationsHomeScreenModel_copyUpdatesFields() {
        val original = ConversationsHomeScreenModel(
            isLoading = true,
            conversations = listOf(mockk()),
            error = null
        )

        val updated = original.copy(
            isLoading = false,
            error = "Error occurred"
        )

        assertEquals(false, updated.isLoading)
        assertEquals(1, updated.conversations.size)
        assertEquals("Error occurred", updated.error)
    }

    @Test
    fun conversationsHomeScreenModel_equalityWorks() {
        val conversations = listOf(mockk<Conversation>())

        val model1 = ConversationsHomeScreenModel(
            isLoading = true,
            conversations = conversations,
            error = "Error"
        )

        val model2 = ConversationsHomeScreenModel(
            isLoading = true,
            conversations = conversations,
            error = "Error"
        )

        assertEquals(model1, model2)
        assertEquals(model1.hashCode(), model2.hashCode())
    }
}