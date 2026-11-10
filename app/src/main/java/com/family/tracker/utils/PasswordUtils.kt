package com.family.tracker.utils

import java.security.MessageDigest

object PasswordUtils {
    private const val SALT = "FamilyTracker_Secret_Salt_2024"

    fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = (password + SALT).toByteArray(Charsets.UTF_8)
        val bytes = md.digest(input)
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}