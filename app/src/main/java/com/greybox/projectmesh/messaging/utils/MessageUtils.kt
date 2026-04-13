package com.greybox.projectmesh.messaging.utils

/**
 * Utility functions for formatting message metadata and generating stable
 * chat identifiers used throughout the messaging system.
 */
object MessageUtils {

    /**
     * Formats a Unix timestamp into a human-readable time string.
     *
     * @param timestamp The timestamp in milliseconds.
     * @return A formatted time string in `"HH:mm"` format.
     */
    fun formatTimestamp(timestamp: Long): String {
        //Adding timestamp formatting logic
        return java.text.SimpleDateFormat("HH:mm").format(timestamp)
    }

    /**
     * Generates a stable, deterministic chat ID from two user identifiers.
     *
     * The two identifiers are sorted alphabetically so both users
     * will always compute the same ID for the same pair.
     *
     * @param sender The identifier of the sender.
     * @param receiver The identifier of the receiver.
     * @return A hyphen-joined chat ID such as `"userA-userB"`.
     */
    fun generateChatId(sender: String, receiver: String): String {
        //Create a consistent chat ID for two users
        return listOf(sender, receiver).sorted().joinToString("-")
    }
}
