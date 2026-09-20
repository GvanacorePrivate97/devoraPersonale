package com.devora.mencare.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * These rules are the backend's (`backend/src/lib/validation.ts`) and the iOS
 * app's, word for word. This test is the net that keeps the three in step.
 */
class ValidationTest {

    @Test
    fun `hand written italian numbers normalise to E164`() {
        assertEquals("+393478124490", normalizePhone("347 812 4490"))
        assertEquals("+393478124490", normalizePhone("+39 347.812-4490"))
        assertEquals("+393478124490", normalizePhone("0039 (347) 8124490"))
        // Landlines keep their leading zero.
        assertEquals("+390811234567", normalizePhone("081 123 4567"))
    }

    @Test
    fun `implausible numbers are rejected`() {
        assertNull(normalizePhone(""))
        assertNull(normalizePhone("123"))
        assertNull(normalizePhone("347 812 4490 00 11"))
        assertNull(normalizePhone("+0 1234 5678"))
    }

    @Test
    fun `numbers are shown the way the field displays them`() {
        assertEquals("+39 347 812 4490", formatPhone("+393478124490"))
        assertEquals("+441234567890", formatPhone("+441234567890"))
    }

    @Test
    fun `an accepted phone is always E164`() {
        assertEquals("+393478124490", validatePhone("347 812 4490").valueOrNull())
        assertEquals(ValidationError.REQUIRED, validatePhone("  ").errorOrNull())
        assertEquals(ValidationError.PHONE_INVALID, validatePhone("123").errorOrNull())
    }

    @Test
    fun `names allow accents apostrophes and hyphens`() {
        assertEquals("D'Amico", validateName("  D'Amico  ").valueOrNull())
        assertEquals("De Vito", validateName("De Vito").valueOrNull())
        assertEquals(ValidationError.REQUIRED, validateName("").errorOrNull())
        assertEquals(ValidationError.NAME_LENGTH, validateName("A").errorOrNull())
        assertEquals(ValidationError.NAME_CHARS, validateName("Marco 2").errorOrNull())
    }

    @Test
    fun `emails are trimmed and lowercased`() {
        assertEquals("marco@gmail.com", validateEmail("  Marco@Gmail.COM ").valueOrNull())
        assertEquals(ValidationError.EMAIL_INVALID, validateEmail("a.@b").errorOrNull())
        assertEquals(ValidationError.EMAIL_INVALID, validateEmail("marco@gmail").errorOrNull())
        assertEquals(ValidationError.REQUIRED, validateEmail("").errorOrNull())
    }

    @Test
    fun `a password needs at least one letter and one digit`() {
        assertEquals(ValidationError.PASSWORD_TOO_SHORT, validatePassword("ab1").errorOrNull())
        assertEquals(ValidationError.PASSWORD_NO_DIGIT, validatePassword("abcdefgh").errorOrNull())
        assertEquals(ValidationError.PASSWORD_NO_LETTER, validatePassword("12345678").errorOrNull())
        assertEquals("abcdefg1", validatePassword("abcdefg1").valueOrNull())
    }

    @Test
    fun `the strength meter scores exactly like the backend`() {
        assertEquals(PasswordStrength.DEBOLE, passwordStrength("abc"))
        assertEquals(PasswordStrength.DEBOLE, passwordStrength("abcdefg1"))
        assertEquals(PasswordStrength.MEDIA, passwordStrength("Abcdefg1"))
        assertEquals(PasswordStrength.FORTE, passwordStrength("Abcdefgh1!"))
    }

    @Test
    fun `prices round instead of truncating`() {
        // The historical bug: 19,99 € was stored as 1998 cents.
        assertEquals(1999L, validatePriceInput("19,99").valueOrNull())
        assertEquals(1999L, validatePriceInput("19.99").valueOrNull())
        assertEquals(2200L, validatePriceInput("22").valueOrNull())
        assertEquals(1000L, validatePriceInput("10,004").valueOrNull())
    }

    @Test
    fun `a malformed price never becomes zero`() {
        assertEquals(ValidationError.PRICE_INVALID, validatePriceInput("1.2.3").errorOrNull())
        assertEquals(ValidationError.REQUIRED, validatePriceInput("").errorOrNull())
        assertEquals(ValidationError.PRICE_TOO_HIGH, validatePriceInput("1001").errorOrNull())
    }

    @Test
    fun `the price field only lets a price through`() {
        assertEquals("19,99", sanitizePriceInput("19,999"))
        assertEquals("19,99", sanitizePriceInput("1a9.9b9"))
        assertEquals("0,50", sanitizePriceInput(",50"))
        assertEquals("1000", sanitizePriceInput("100000"))
    }

    @Test
    fun `cents and the field text agree`() {
        assertEquals("19,99", formatCentsAsInput(1999))
        assertEquals("22", formatCentsAsInput(2200))
        assertEquals("0,05", formatCentsAsInput(5))
    }

    @Test
    fun `durations go in five minute steps`() {
        assertEquals(45, validateDuration(45).valueOrNull())
        assertEquals(ValidationError.DURATION_STEP, validateDuration(47).errorOrNull())
        assertEquals(ValidationError.DURATION_TOO_SHORT, validateDuration(0).errorOrNull())
        assertEquals(ValidationError.DURATION_TOO_LONG, validateDuration(485).errorOrNull())
    }

    @Test
    fun `an empty note becomes null`() {
        assertNull(validateNote("   ").valueOrNull())
        assertEquals("Sfumatura bassa", validateNote(" Sfumatura bassa ").valueOrNull())
        assertEquals(ValidationError.NOTE_TOO_LONG, validateNote("x".repeat(NOTE_MAX + 1)).errorOrNull())
    }
}
