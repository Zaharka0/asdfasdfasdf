package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AnnotationEntity
import com.example.data.model.AnnotationType
import com.example.data.model.DocumentEntity
import com.example.pdf.PdfEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PDF Editor", appName)
  }

  @Test
  fun `generate samples and check existence`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = PdfEngine(context)
    val sampleFiles = engine.generateSampleDocuments()
    assertTrue("At least 2 sample documents generated", sampleFiles.size >= 2)
    for (file in sampleFiles) {
      assertTrue("File ${file.name} exists and not empty", file.exists() && file.length() > 0)
      val count = engine.getPageCount(file)
      assertTrue("File ${file.name} has at least 1 page", count >= 1)
    }
  }

  @Test
  fun `verify document and annotation entities`() {
    val doc = DocumentEntity(
      id = "doc-1",
      title = "Test PDF",
      filePath = "/tmp/test.pdf",
      pageCount = 3,
      fileSizeBytes = 2048L
    )
    assertEquals("doc-1", doc.id)
    assertEquals("Test PDF", doc.title)

    val annotation = AnnotationEntity(
      id = "ann-1",
      documentId = doc.id,
      pageIndex = 0,
      type = AnnotationType.NOTE.name,
      normalizedX = 0.25f,
      normalizedY = 0.5f,
      content = "Review this clause"
    )
    assertEquals("ann-1", annotation.id)
    assertEquals(AnnotationType.NOTE.name, annotation.type)
  }
}
