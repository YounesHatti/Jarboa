package com.youneshatti.jarboa.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection

// Resolve each paragraph from its content, independently of the phone's UI language.
internal fun TextStyle.forChatContent(): TextStyle = copy(
    textDirection = TextDirection.Content,
    textAlign = TextAlign.Start,
)
