package com.example.utils

import java.util.regex.Pattern

object PhoneUtils {

    /**
     * Cleans non-digits except maybe the '+' prefix.
     */
    fun cleanPhoneNumber(phone: String): String {
        val hasPlus = phone.trim().startsWith("+")
        val digits = phone.replace(Regex("[^0-9]"), "")
        return if (hasPlus) "+$digits" else digits
    }

    /**
     * Formats a phone number dynamically using standard Brazilian formatting
     * or a generic fall-off format.
     */
    fun formatDisplayNumber(rawPhone: String): String {
        val digits = rawPhone.replace(Regex("[^0-9]"), "")
        
        return when {
            // Under Brazilian cellular format with country code (55XX9XXXXXXXX) - 13 digits
            digits.length == 13 && digits.startsWith("55") -> {
                val ddd = digits.substring(2, 4)
                val firstPart = digits.substring(4, 9)
                val secondPart = digits.substring(9, 13)
                "+55 ($ddd) $firstPart-$secondPart"
            }
            // Under Brazilian landline format with country code (55XXXXXXXXXX) - 12 digits
            digits.length == 12 && digits.startsWith("55") -> {
                val ddd = digits.substring(2, 4)
                val firstPart = digits.substring(4, 8)
                val secondPart = digits.substring(8, 12)
                "+55 ($ddd) $firstPart-$secondPart"
            }
            // Brazilian cellular format without country code (XX9XXXXXXXX) - 11 digits
            digits.length == 11 -> {
                val ddd = digits.substring(0, 2)
                val firstPart = digits.substring(2, 7)
                val secondPart = digits.substring(7, 11)
                "($ddd) $firstPart-$secondPart"
            }
            // Brazilian landline format without country code (XXXXXXXXXX) - 10 digits
            digits.length == 10 -> {
                val ddd = digits.substring(0, 2)
                val firstPart = digits.substring(2, 6)
                val secondPart = digits.substring(6, 10)
                "($ddd) $firstPart-$secondPart"
            }
            // No DDD, just cellphone (9XXXXXXXX) - 9 digits
            digits.length == 9 -> {
                val firstPart = digits.substring(0, 5)
                val secondPart = digits.substring(5, 9)
                "$firstPart-$secondPart"
            }
            // No DDD, landline (XXXXXXXX) - 8 digits
            digits.length == 8 -> {
                val firstPart = digits.substring(0, 4)
                val secondPart = digits.substring(4, 8)
                "$firstPart-$secondPart"
            }
            // Generic format
            else -> {
                rawPhone
            }
        }
    }

    /**
     * Formats telephone numbers for WhatsApp URL integration.
     * Starts with country code. If missing 55, checks if it belongs to Brazil or is generic.
     */
    fun formatForWhatsApp(rawPhone: String, defaultCountryCode: String = "55"): String {
        var digits = rawPhone.replace(Regex("[^0-9]"), "")
        if (digits.isEmpty()) return ""

        // If it starts with standard country code like 55, keep it.
        // If it's a Brazilian cellphone length without country code, add 55.
        if (digits.length <= 11) {
            digits = "$defaultCountryCode$digits"
        }
        return digits
    }

    /**
     * Locally extracts the first valid-looking phone number sequence using Regex.
     * Helps automatically capture copied phone numbers from clipboard instantly.
     */
    fun extractPhoneNumber(text: String): String? {
        // Matches typical numbers like +55 (11) 98765-4321, 11987654321, 98765-4321, 11 987654321
        val regex = "(\\+?\\d{1,4}[\\s-]?)?\\(?\\d{2,3}\\)?[\\s-]?\\d{4,5}[\\s-]?\\d{4}"
        val pattern = Pattern.compile(regex)
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return matcher.group()
        }
        return null
    }
}
