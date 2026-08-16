package com.youneshatti.jarboa.domain.message

import com.youneshatti.jarboa.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageStateReducerTest {
    @Test
    fun `pending message can be sent`() {
        assertEquals(
            MessageStatus.SENT,
            MessageStateReducer.transition(MessageStatus.PENDING, MessageStatus.SENT),
        )
    }

    @Test
    fun `delivery is terminal`() {
        assertEquals(
            MessageStatus.DELIVERED,
            MessageStateReducer.transition(MessageStatus.DELIVERED, MessageStatus.FAILED),
        )
    }

    @Test
    fun `failed message can be queued for retry`() {
        assertEquals(
            MessageStatus.PENDING,
            MessageStateReducer.transition(MessageStatus.FAILED, MessageStatus.PENDING),
        )
    }
}
