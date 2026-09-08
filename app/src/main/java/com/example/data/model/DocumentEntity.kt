package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
  @PrimaryKey val id: String,
  val title: String,
  val filePath: String,
  val pageCount: Int,
  val fileSizeBytes: Long,
  val lastOpenedTimestamp: Long = System.currentTimeMillis(),
  val isPasswordProtected: Boolean = false,
  val passwordPin: String? = null,
  val isSample: Boolean = false
)
