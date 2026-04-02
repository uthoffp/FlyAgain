package com.flyagain.world.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatMessageSanitizerTest {
    private val sanitizer = ChatMessageSanitizer()

    @Test fun `normal text passes through unchanged`() { assertEquals("Hello world", sanitizer.sanitize("Hello world")) }
    @Test fun `strips null bytes`() { assertEquals("Hello", sanitizer.sanitize("Hel\u0000lo")) }
    @Test fun `strips unicode control characters`() { assertEquals("Hello", sanitizer.sanitize("He\u0001l\u001Flo")) }
    @Test fun `strips zero-width space`() { assertEquals("Hello", sanitizer.sanitize("Hel\u200Blo")) }
    @Test fun `strips RTL override`() { assertEquals("Hello", sanitizer.sanitize("Hel\u202Elo")) }
    @Test fun `strips HTML tags`() { assertEquals("bold text", sanitizer.sanitize("<b>bold</b> text")) }
    @Test fun `strips BBCode tags`() { assertEquals("colored", sanitizer.sanitize("[color=red]colored[/color]")) }
    @Test fun `trims whitespace`() { assertEquals("Hello", sanitizer.sanitize("  Hello  ")) }
    @Test fun `returns null for empty string after sanitization`() { assertNull(sanitizer.sanitize("   ")) }
    @Test fun `returns null for null-bytes-only string`() { assertNull(sanitizer.sanitize("\u0000\u0000")) }
    @Test fun `returns null for text exceeding max length`() { assertNull(sanitizer.sanitize("a".repeat(201))) }
    @Test fun `accepts exactly 200 characters`() { assertEquals("a".repeat(200), sanitizer.sanitize("a".repeat(200))) }
    @Test fun `preserves normal unicode like umlauts`() { assertEquals("Hallo Welt äöü", sanitizer.sanitize("Hallo Welt äöü")) }
}
