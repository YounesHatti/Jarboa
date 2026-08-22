package com.youneshatti.jarboa.data.local

import androidx.room.TypeConverter
import com.youneshatti.jarboa.domain.model.MessageStatus

class DatabaseConverters {
    @TypeConverter
    fun messageStatusToString(status: MessageStatus): String = status.name

    @TypeConverter
    fun stringToMessageStatus(value: String): MessageStatus = MessageStatus.valueOf(value)
}

