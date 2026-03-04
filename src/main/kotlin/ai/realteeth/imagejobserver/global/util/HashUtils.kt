package ai.realteeth.imagejobserver.global.util

import java.security.MessageDigest

object HashUtils {

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
