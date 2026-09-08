package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AnnotationEntity
import com.example.data.model.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
  @Query("SELECT * FROM documents ORDER BY lastOpenedTimestamp DESC")
  fun getAllDocuments(): Flow<List<DocumentEntity>>

  @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
  suspend fun getDocumentById(id: String): DocumentEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertDocument(document: DocumentEntity)

  @Update
  suspend fun updateDocument(document: DocumentEntity)

  @Query("DELETE FROM documents WHERE id = :id")
  suspend fun deleteDocumentById(id: String)

  @Query("SELECT * FROM annotations WHERE documentId = :documentId ORDER BY pageIndex ASC, createdTimestamp ASC")
  fun getAnnotationsForDocument(documentId: String): Flow<List<AnnotationEntity>>

  @Query("SELECT * FROM annotations WHERE documentId = :documentId AND pageIndex = :pageIndex")
  fun getAnnotationsForPage(documentId: String, pageIndex: Int): Flow<List<AnnotationEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAnnotation(annotation: AnnotationEntity)

  @Query("DELETE FROM annotations WHERE id = :id")
  suspend fun deleteAnnotationById(id: String)

  @Query("DELETE FROM annotations WHERE documentId = :documentId")
  suspend fun deleteAnnotationsForDocument(documentId: String)
}
