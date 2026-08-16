package com.youneshatti.jarboa.domain.message

import com.youneshatti.jarboa.domain.model.MessageStatus

object MessageStateReducer {
    fun transition(current: MessageStatus, requested: MessageStatus): MessageStatus = when (current) {
        MessageStatus.PENDING -> requested
        MessageStatus.SENT -> when (requested) {
            MessageStatus.DELIVERED -> MessageStatus.DELIVERED
            MessageStatus.FAILED -> MessageStatus.FAILED
            else -> MessageStatus.SENT
        }
        MessageStatus.DELIVERED -> MessageStatus.DELIVERED
        MessageStatus.FAILED -> if (requested == MessageStatus.PENDING) MessageStatus.PENDING else MessageStatus.FAILED
    }
}

