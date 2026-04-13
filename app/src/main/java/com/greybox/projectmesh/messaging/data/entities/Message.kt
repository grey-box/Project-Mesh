package com.greybox.projectmesh.messaging.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.URI

/**
 * Room type converter to convert between [URI] and [String] for database storage.
 */
class URIConverter {
    /**
     * Converts a [URI] to a [String] for database storage.
     *
     * @param theuri The URI to convert.
     * @return The string representation of the URI, or null if input is null.
     */
    @TypeConverter
    fun convfromURI(theuri: URI?): String? {
        return theuri?.toString()
    }

    /**
     * Converts a [String] back to a [URI].
     *
     * @param uristring The string to convert.
     * @return The corresponding URI, or null if input is null.
     */
    @TypeConverter
    fun convtoURI(uristring: String?): URI? {
        return uristring?.let { URI.create(it) }
    }
}

/**
 * Serializer to make [URI] serializable for Kotlinx serialization (e.g., JSON).
 */
object URISerializable : KSerializer<URI> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("URI", PrimitiveKind.STRING)

    /**
     * Serializes a [URI] into a string.
     *
     * @param enc The encoder.
     * @param vals The URI to serialize.
     */
    override fun serialize(enc: Encoder, vals: URI) {
        enc.encodeString(vals.toString())
    }

    /**
     * Deserializes a string into a [URI].
     *
     * @param dec The decoder.
     * @return The deserialized URI.
     */
    override fun deserialize(dec: Decoder): URI {
        return URI.create(dec.decodeString())
    }
}

/**
 * Room entity representing a message in a chat.
 *
 * @property id Unique message ID (auto-generated).
 * @property dateReceived Timestamp when the message was received.
 * @property content The text content of the message.
 * @property sender The identifier of the sender.
 * @property chat The chat/conversation ID this message belongs to.
 * @property file Optional file attached to the message, stored as a [URI].
 */
@Serializable
@Entity(tableName = "message")
@TypeConverters(URIConverter::class)
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "dateReceived") val dateReceived: Long,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "sender") val sender: String,
    @ColumnInfo(name = "chat") val chat: String,
    @ColumnInfo(name = "file") @Serializable(with = URISerializable::class) val file: URI? = null
    // @ColumnInfo(name = "file") @Serializable(with=URISerializable::class) val file: List<URI?>
)
