package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AnnotationType {
  NOTE,
  HIGHLIGHT,
  TEXT_LABEL
}

@Entity(tableName = "annotations")
data class AnnotationEntity(
  @PrimaryKey val id: String,
  val documentId: String,
  val pageIndex: Int,
  val type: String, // "NOTE", "HIGHLIGHT", "TEXT_LABEL"
  val normalizedX: Float, // 0..1
  val normalizedY: Float, // 0..1
  val normalizedWidth: Float = 0.2f,
  val normalizedHeight: Float = 0.05f,
  val colorHex: String = "#FFD600",
  val content: String = "",
  val author: String = "User",
  val createdTimestamp: Long = System.currentTimeMillis()
)
