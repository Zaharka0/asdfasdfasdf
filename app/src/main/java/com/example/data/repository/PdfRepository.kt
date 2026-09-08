package com.example.data.repository

import com.example.data.dao.PdfDao
import com.example.data.model.AnnotationEntity
import com.example.data.model.DocumentEntity
import kotlinx.coroutines.flow.Flow

class PdfRepository(private val pdfDao: PdfDao) {

  val allDocuments: Flow<List<DocumentEntity>> = pdfDao.getAllDocuments()

  suspend fun getDocumentById(id: String): DocumentEntity? = pdfDao.getDocumentById(id)

  suspend fun insertOrUpdateDocument(document: DocumentEntity) {
    pdfDao.insertDocument(document)
  }

  suspend fun deleteDocument(id: String) {
    pdfDao.deleteDocumentById(id)
    pdfDao.deleteAnnotationsForDocument(id)
  }

  fun getAnnotationsForDocument(documentId: String): Flow<List<AnnotationEntity>> =
    pdfDao.getAnnotationsForDocument(documentId)

  fun getAnnotationsForPage(documentId: String, pageIndex: Int): Flow<List<AnnotationEntity>> =
    pdfDao.getAnnotationsForPage(documentId, pageIndex)

  suspend fun saveAnnotation(annotation: AnnotationEntity) {
    pdfDao.insertAnnotation(annotation)
  }

  suspend fun deleteAnnotation(id: String) {
    pdfDao.deleteAnnotationById(id)
  }
}
