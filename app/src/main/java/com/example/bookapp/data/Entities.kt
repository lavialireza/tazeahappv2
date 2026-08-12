package com.example.bookapp.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// سطح ۱: زمینه‌ها (اصفهان، تهران، میرعزا و ...)
@Entity(tableName = "fields")
data class FieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String
)

// سطح ۲: تعزیه‌ها (عاشورا، بازار شام و ...) - متعلق به یک زمینه
@Entity(
    tableName = "taziehs",
    foreignKeys = [ForeignKey(
        entity = FieldEntity::class,
        parentColumns = ["id"],
        childColumns = ["fieldId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("fieldId")]
)
data class TaziehEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fieldId: Long,
    val title: String
)

// سطح ۳: نقش‌ها (شمر، امام، یزید و ...) - متعلق به یک تعزیه
@Entity(
    tableName = "roles",
    foreignKeys = [ForeignKey(
        entity = TaziehEntity::class,
        parentColumns = ["id"],
        childColumns = ["taziehId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taziehId")]
)
data class RoleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taziehId: Long,
    val title: String
)

// سطح ۴: بخش‌ها (ورود، ساقی‌نامه، شهادت و ...) - متعلق به یک نقش، شامل متن اشعار
@Entity(
    tableName = "sections",
    foreignKeys = [ForeignKey(
        entity = RoleEntity::class,
        parentColumns = ["id"],
        childColumns = ["roleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("roleId")]
)
data class SectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roleId: Long,
    val orderIndex: Int,
    val title: String,
    val content: String // متن اشعار همان بخش
)
