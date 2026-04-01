package com.flyagain.world.chat

class ChatMessageSanitizer {
    companion object {
        const val MAX_LENGTH = 200
        private val HTML_TAG_REGEX = Regex("<[^>]+>")
        private val BBCODE_TAG_REGEX = Regex("\\[[^]]+]")
        private val CONTROL_CHAR_REGEX = Regex("[\\u0000-\\u001F\\u007F-\\u009F\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]")
    }

    fun sanitize(input: String): String? {
        if (input.length > MAX_LENGTH) return null
        var text = input
        text = CONTROL_CHAR_REGEX.replace(text, "")
        text = HTML_TAG_REGEX.replace(text, "")
        text = BBCODE_TAG_REGEX.replace(text, "")
        text = text.trim()
        return text.ifEmpty { null }
    }
}
