package io.github.fush1ft.summarised.core.database

import androidx.room.TypeConverter
import io.github.fush1ft.summarised.core.database.entity.ChatRole
import io.github.fush1ft.summarised.core.database.entity.RecordingStatus

/**
 * Enums are persisted by name, not ordinal: reordering an enum constant should not
 * silently reinterpret every existing row.
 */
class Converters {
    @TypeConverter
    fun toRecordingStatus(value: String): RecordingStatus = RecordingStatus.valueOf(value)

    @TypeConverter
    fun fromRecordingStatus(status: RecordingStatus): String = status.name

    @TypeConverter
    fun toChatRole(value: String): ChatRole = ChatRole.valueOf(value)

    @TypeConverter
    fun fromChatRole(role: ChatRole): String = role.name
}
