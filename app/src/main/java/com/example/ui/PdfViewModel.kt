package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.PdfDatabase
import com.example.data.model.AnnotationEntity
import com.example.data.model.AnnotationType
import com.example.data.model.DocumentEntity
import com.example.data.repository.PdfRepository
import com.example.pdf.DrawingPoint
import com.example.pdf.DrawingStroke
import com.example.pdf.PdfEngine
import com.example.pdf.TextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ViewerMode {
  VIEW_PAN,
  DRAW,
  HIGHLIGHT,
  NOTE,
  ADD_TEXT
}

class PdfViewModel(application: Application) : AndroidViewModel(application) {

  val pdfEngine = PdfEngine(application)
  private val repository: PdfRepository

  init {
    val db = PdfDatabase.getDatabase(application)
    repository = PdfRepository(db.pdfDao())
    initializeSamples()
  }

  // Document list & search
  val searchQuery = MutableStateFlow("")
  private val _rawDocuments = repository.allDocuments.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(5000),
    emptyList()
  )

  val filteredDocuments: StateFlow<List<DocumentEntity>> = combine(
    _rawDocuments,
    searchQuery
  ) { docs, query ->
    if (query.isBlank()) docs
    else docs.filter { it.title.contains(query, ignoreCase = true) }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Current viewing document
  private val _currentDocument = MutableStateFlow<DocumentEntity?>(null)
  val currentDocument: StateFlow<DocumentEntity?> = _currentDocument.asStateFlow()

  // Viewer state
  private val _currentPageIndex = MutableStateFlow(0)
  val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

  private val _pageCount = MutableStateFlow(1)
  val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

  private val _currentBitmap = MutableStateFlow<Bitmap?>(null)
  val currentBitmap: StateFlow<Bitmap?> = _currentBitmap.asStateFlow()

  private val _isLoadingPage = MutableStateFlow(false)
  val isLoadingPage: StateFlow<Boolean> = _isLoadingPage.asStateFlow()

  private val _zoomScale = MutableStateFlow(1.0f)
  val zoomScale: StateFlow<Float> = _zoomScale.asStateFlow()

  // Tool Mode & Annotations
  private val _viewerMode = MutableStateFlow(ViewerMode.VIEW_PAN)
  val viewerMode: StateFlow<ViewerMode> = _viewerMode.asStateFlow()

  private val _selectedColorHex = MutableStateFlow("#FFD600") // Default Yellow
  val selectedColorHex: StateFlow<String> = _selectedColorHex.asStateFlow()

  private val _strokeWidth = MutableStateFlow(4f)
  val strokeWidth: StateFlow<Float> = _strokeWidth.asStateFlow()

  // In-memory drawings & texts for current session per page
  private val _pageDrawings = MutableStateFlow<Map<Int, List<DrawingStroke>>>(emptyMap())
  val pageDrawings: StateFlow<Map<Int, List<DrawingStroke>>> = _pageDrawings.asStateFlow()

  private val _pageTexts = MutableStateFlow<Map<Int, List<TextElement>>>(emptyMap())
  val pageTexts: StateFlow<Map<Int, List<TextElement>>> = _pageTexts.asStateFlow()

  // Current active drawing stroke being drawn
  private val _activeStrokePoints = MutableStateFlow<List<DrawingPoint>>(emptyList())
  val activeStrokePoints: StateFlow<List<DrawingPoint>> = _activeStrokePoints.asStateFlow()

  // Annotations from Room DB
  private val _documentAnnotations = MutableStateFlow<List<AnnotationEntity>>(emptyList())
  val documentAnnotations: StateFlow<List<AnnotationEntity>> = _documentAnnotations.asStateFlow()

  // UI sheets / dialogs
  private val _isAnnotationsDrawerOpen = MutableStateFlow(false)
  val isAnnotationsDrawerOpen: StateFlow<Boolean> = _isAnnotationsDrawerOpen.asStateFlow()

  private val _isThumbnailsOpen = MutableStateFlow(false)
  val isThumbnailsOpen: StateFlow<Boolean> = _isThumbnailsOpen.asStateFlow()

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  fun clearSnackbar() {
    _snackbarMessage.value = null
  }

  fun showMessage(msg: String) {
    _snackbarMessage.value = msg
  }

  private fun initializeSamples() {
    viewModelScope.launch {
      val sampleFiles = pdfEngine.generateSampleDocuments()
      for (file in sampleFiles) {
        val count = pdfEngine.getPageCount(file)
        val entity = DocumentEntity(
          id = file.name,
          title = if (file.name.contains("contract")) "Services Agreement & NDA" else "PDF Editor Quick Guide",
          filePath = file.absolutePath,
          pageCount = count,
          fileSizeBytes = file.length(),
          isSample = true
        )
        repository.insertOrUpdateDocument(entity)
      }
    }
  }

  fun openDocument(doc: DocumentEntity) {
    _currentDocument.value = doc
    _currentPageIndex.value = 0
    _zoomScale.value = 1.0f
    _pageDrawings.value = emptyMap()
    _pageTexts.value = emptyMap()
    _activeStrokePoints.value = emptyList()
    _viewerMode.value = ViewerMode.VIEW_PAN

    viewModelScope.launch {
      val file = File(doc.filePath)
      val count = pdfEngine.getPageCount(file)
      _pageCount.value = count.coerceAtLeast(1)

      // Update last opened
      repository.insertOrUpdateDocument(doc.copy(lastOpenedTimestamp = System.currentTimeMillis()))

      // Collect annotations
      repository.getAnnotationsForDocument(doc.id).collect { list ->
        _documentAnnotations.value = list
      }
    }
    loadCurrentPageBitmap()
  }

  fun closeDocument() {
    _currentDocument.value = null
    _currentBitmap.value = null
    pdfEngine.clearCache()
  }

  fun setViewerMode(mode: ViewerMode) {
    _viewerMode.value = mode
  }

  fun setSelectedColor(colorHex: String) {
    _selectedColorHex.value = colorHex
  }

  fun setStrokeWidth(width: Float) {
    _strokeWidth.value = width
  }

  fun setZoomScale(scale: Float) {
    _zoomScale.value = scale.coerceIn(0.5f, 4.0f)
  }

  fun toggleAnnotationsDrawer(open: Boolean) {
    _isAnnotationsDrawerOpen.value = open
  }

  fun toggleThumbnails(open: Boolean) {
    _isThumbnailsOpen.value = open
  }

  fun goToPage(pageIdx: Int) {
    val total = _pageCount.value
    if (pageIdx in 0 until total) {
      _currentPageIndex.value = pageIdx
      _activeStrokePoints.value = emptyList()
      loadCurrentPageBitmap()
    }
  }

  fun nextPage() {
    if (_currentPageIndex.value < _pageCount.value - 1) {
      goToPage(_currentPageIndex.value + 1)
    }
  }

  fun previousPage() {
    if (_currentPageIndex.value > 0) {
      goToPage(_currentPageIndex.value - 1)
    }
  }

  private fun loadCurrentPageBitmap() {
    val doc = _currentDocument.value ?: return
    viewModelScope.launch {
      _isLoadingPage.value = true
      val file = File(doc.filePath)
      val bmp = pdfEngine.renderPageBitmap(file, _currentPageIndex.value, targetWidth = 1080)
      _currentBitmap.value = bmp
      _isLoadingPage.value = false
    }
  }

  // Hand-drawing interaction
  fun addStrokePoint(x: Float, y: Float) {
    _activeStrokePoints.value = _activeStrokePoints.value + DrawingPoint(x, y)
  }

  fun finishStroke() {
    val points = _activeStrokePoints.value
    if (points.size > 1) {
      val pageIdx = _currentPageIndex.value
      val stroke = DrawingStroke(
        points = points,
        color = AndroidColor.parseColor(_selectedColorHex.value),
        strokeWidth = _strokeWidth.value
      )
      val currentMap = _pageDrawings.value.toMutableMap()
      val list = currentMap[pageIdx]?.toMutableList() ?: mutableListOf()
      list.add(stroke)
      currentMap[pageIdx] = list
      _pageDrawings.value = currentMap
    }
    _activeStrokePoints.value = emptyList()
  }

  fun undoLastStroke() {
    val pageIdx = _currentPageIndex.value
    val currentMap = _pageDrawings.value.toMutableMap()
    val list = currentMap[pageIdx]?.toMutableList()
    if (!list.isNullOrEmpty()) {
      list.removeAt(list.size - 1)
      currentMap[pageIdx] = list
      _pageDrawings.value = currentMap
      showMessage("Last stroke removed")
    }
  }

  fun clearPageDrawings() {
    val pageIdx = _currentPageIndex.value
    val currentMap = _pageDrawings.value.toMutableMap()
    currentMap.remove(pageIdx)
    _pageDrawings.value = currentMap
    showMessage("Page drawings cleared")
  }

  // Direct Text insertion
  fun addTextElement(text: String, x: Float, y: Float, textSize: Float = 24f) {
    if (text.isBlank()) return
    val pageIdx = _currentPageIndex.value
    val textEl = TextElement(
      text = text,
      x = x,
      y = y,
      color = AndroidColor.parseColor(_selectedColorHex.value),
      textSize = textSize
    )
    val currentMap = _pageTexts.value.toMutableMap()
    val list = currentMap[pageIdx]?.toMutableList() ?: mutableListOf()
    list.add(textEl)
    currentMap[pageIdx] = list
    _pageTexts.value = currentMap
    showMessage("Text added to page")
  }

  // Annotations (Highlight & Sticky Note linked to coordinates)
  fun addCoordinateNote(normalizedX: Float, normalizedY: Float, noteContent: String) {
    val doc = _currentDocument.value ?: return
    if (noteContent.isBlank()) return
    viewModelScope.launch {
      val annotation = AnnotationEntity(
        id = UUID.randomUUID().toString(),
        documentId = doc.id,
        pageIndex = _currentPageIndex.value,
        type = AnnotationType.NOTE.name,
        normalizedX = normalizedX.coerceIn(0f, 1f),
        normalizedY = normalizedY.coerceIn(0f, 1f),
        colorHex = _selectedColorHex.value,
        content = noteContent
      )
      repository.saveAnnotation(annotation)
      showMessage("Note saved to Page ${_currentPageIndex.value + 1}")
    }
  }

  fun addHighlight(normalizedX: Float, normalizedY: Float, width: Float, height: Float, note: String = "") {
    val doc = _currentDocument.value ?: return
    viewModelScope.launch {
      val annotation = AnnotationEntity(
        id = UUID.randomUUID().toString(),
        documentId = doc.id,
        pageIndex = _currentPageIndex.value,
        type = AnnotationType.HIGHLIGHT.name,
        normalizedX = normalizedX.coerceIn(0f, 1f),
        normalizedY = normalizedY.coerceIn(0f, 1f),
        normalizedWidth = width.coerceIn(0.05f, 1f),
        normalizedHeight = height.coerceIn(0.02f, 1f),
        colorHex = _selectedColorHex.value,
        content = note
      )
      repository.saveAnnotation(annotation)
      showMessage("Highlight added to Page ${_currentPageIndex.value + 1}")
    }
  }

  fun deleteAnnotation(annotationId: String) {
    viewModelScope.launch {
      repository.deleteAnnotation(annotationId)
      showMessage("Annotation removed")
    }
  }

  // Save / Burn edits directly into the PDF file
  fun saveEditsToDocument(onSaved: (File) -> Unit) {
    val doc = _currentDocument.value ?: return
    viewModelScope.launch {
      val sourceFile = File(doc.filePath)
      val editedFile = File(sourceFile.parentFile, "edited_${System.currentTimeMillis()}_${sourceFile.name}")

      // Group annotations by page
      val annotationsByPage = _documentAnnotations.value.groupBy { it.pageIndex }

      val success = pdfEngine.saveEditedPdf(
        sourceFile = sourceFile,
        targetFile = editedFile,
        pageDrawings = _pageDrawings.value,
        pageTexts = _pageTexts.value,
        pageAnnotations = annotationsByPage
      )

      if (success && editedFile.exists()) {
        val updatedDoc = doc.copy(
          filePath = editedFile.absolutePath,
          fileSizeBytes = editedFile.length(),
          lastOpenedTimestamp = System.currentTimeMillis()
        )
        repository.insertOrUpdateDocument(updatedDoc)
        _currentDocument.value = updatedDoc
        pdfEngine.clearCache()
        loadCurrentPageBitmap()
        showMessage("Changes saved directly to PDF file!")
        onSaved(editedFile)
      } else {
        showMessage("Failed to save changes to PDF")
      }
    }
  }

  // Import Document from URI
  fun importDocumentFromUri(uri: Uri, displayName: String) {
    viewModelScope.launch {
      try {
        val destFile = pdfEngine.copyUriToInternalStorage(uri, displayName)
        val count = pdfEngine.getPageCount(destFile)
        val entity = DocumentEntity(
          id = UUID.randomUUID().toString(),
          title = displayName.removeSuffix(".pdf"),
          filePath = destFile.absolutePath,
          pageCount = count,
          fileSizeBytes = destFile.length()
        )
        repository.insertOrUpdateDocument(entity)
        showMessage("Imported ${entity.title}")
      } catch (e: Exception) {
        e.printStackTrace()
        showMessage("Failed to import PDF")
      }
    }
  }

  // Delete Document
  fun deleteDocument(doc: DocumentEntity) {
    viewModelScope.launch {
      try {
        val file = File(doc.filePath)
        if (file.exists() && !doc.isSample) {
          file.delete()
        }
        repository.deleteDocument(doc.id)
        if (_currentDocument.value?.id == doc.id) {
          closeDocument()
        }
        showMessage("Document deleted")
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  // Lock / Unlock Document with PIN
  fun setDocumentPin(doc: DocumentEntity, pin: String?) {
    viewModelScope.launch {
      val isProtected = !pin.isNullOrBlank()
      val updated = doc.copy(isPasswordProtected = isProtected, passwordPin = pin)
      repository.insertOrUpdateDocument(updated)
      if (_currentDocument.value?.id == doc.id) {
        _currentDocument.value = updated
      }
      showMessage(if (isProtected) "PIN lock enabled" else "PIN lock removed")
    }
  }

  // Tools Actions
  fun executeMergePdfs(files: List<File>, outputName: String, onDone: (File) -> Unit) {
    viewModelScope.launch {
      val outDir = File(getApplication<Application>().filesDir, "documents").apply { mkdirs() }
      val targetFile = File(outDir, "${System.currentTimeMillis()}_$outputName.pdf")
      val success = pdfEngine.mergePdfs(files, targetFile)
      if (success) {
        val count = pdfEngine.getPageCount(targetFile)
        val entity = DocumentEntity(
          id = UUID.randomUUID().toString(),
          title = outputName,
          filePath = targetFile.absolutePath,
          pageCount = count,
          fileSizeBytes = targetFile.length()
        )
        repository.insertOrUpdateDocument(entity)
        showMessage("Merged ${files.size} PDFs successfully!")
        onDone(targetFile)
      } else {
        showMessage("Failed to merge PDFs")
      }
    }
  }

  fun executeSplitPdf(doc: DocumentEntity, startPage: Int, endPage: Int, onDone: (File) -> Unit) {
    viewModelScope.launch {
      val sourceFile = File(doc.filePath)
      val outDir = File(getApplication<Application>().filesDir, "documents").apply { mkdirs() }
      val targetFile = File(outDir, "split_${startPage}_${endPage}_${sourceFile.name}")
      val success = pdfEngine.splitPdf(sourceFile, targetFile, startPage, endPage)
      if (success) {
        val count = pdfEngine.getPageCount(targetFile)
        val entity = DocumentEntity(
          id = UUID.randomUUID().toString(),
          title = "${doc.title} (p.$startPage-$endPage)",
          filePath = targetFile.absolutePath,
          pageCount = count,
          fileSizeBytes = targetFile.length()
        )
        repository.insertOrUpdateDocument(entity)
        showMessage("Split pages $startPage-$endPage created!")
        onDone(targetFile)
      } else {
        showMessage("Failed to split PDF")
      }
    }
  }

  fun executeCompressPdf(doc: DocumentEntity, qualityPercent: Int, onDone: (File) -> Unit) {
    viewModelScope.launch {
      val sourceFile = File(doc.filePath)
      val outDir = File(getApplication<Application>().filesDir, "documents").apply { mkdirs() }
      val targetFile = File(outDir, "compressed_${qualityPercent}pct_${sourceFile.name}")
      val success = pdfEngine.compressPdf(sourceFile, targetFile, qualityPercent)
      if (success) {
        val count = pdfEngine.getPageCount(targetFile)
        val entity = DocumentEntity(
          id = UUID.randomUUID().toString(),
          title = "${doc.title} (Compressed)",
          filePath = targetFile.absolutePath,
          pageCount = count,
          fileSizeBytes = targetFile.length()
        )
        repository.insertOrUpdateDocument(entity)
        val origKb = sourceFile.length() / 1024
        val newKb = targetFile.length() / 1024
        showMessage("Compressed from ${origKb}KB to ${newKb}KB!")
        onDone(targetFile)
      } else {
        showMessage("Failed to compress PDF")
      }
    }
  }

  fun executeWatermarkPdf(doc: DocumentEntity, watermarkText: String, onDone: (File) -> Unit) {
    viewModelScope.launch {
      val sourceFile = File(doc.filePath)
      val outDir = File(getApplication<Application>().filesDir, "documents").apply { mkdirs() }
      val targetFile = File(outDir, "watermarked_${sourceFile.name}")
      val success = pdfEngine.applyWatermark(sourceFile, targetFile, watermarkText)
      if (success) {
        val count = pdfEngine.getPageCount(targetFile)
        val entity = DocumentEntity(
          id = UUID.randomUUID().toString(),
          title = "${doc.title} (Watermarked)",
          filePath = targetFile.absolutePath,
          pageCount = count,
          fileSizeBytes = targetFile.length()
        )
        repository.insertOrUpdateDocument(entity)
        showMessage("Watermark \"$watermarkText\" applied!")
        onDone(targetFile)
      } else {
        showMessage("Failed to apply watermark")
      }
    }
  }

  fun executeConvertImagesToPdf(uris: List<Uri>, title: String, onDone: (File) -> Unit) {
    viewModelScope.launch {
      val outDir = File(getApplication<Application>().filesDir, "documents").apply { mkdirs() }
      val targetFile = File(outDir, "${System.currentTimeMillis()}_$title.pdf")
      val success = pdfEngine.convertImagesToPdf(uris, targetFile)
      if (success) {
        val count = pdfEngine.getPageCount(targetFile)
        val entity = DocumentEntity(
          id = UUID.randomUUID().toString(),
          title = title,
          filePath = targetFile.absolutePath,
          pageCount = count,
          fileSizeBytes = targetFile.length()
        )
        repository.insertOrUpdateDocument(entity)
        showMessage("Converted ${uris.size} images to PDF!")
        onDone(targetFile)
      } else {
        showMessage("Failed to convert images to PDF")
      }
    }
  }

  fun executeConvertTextToPdf(text: String, title: String, onDone: (File) -> Unit) {
    viewModelScope.launch {
      val outDir = File(getApplication<Application>().filesDir, "documents").apply { mkdirs() }
      val targetFile = File(outDir, "${System.currentTimeMillis()}_$title.pdf")
      val success = pdfEngine.convertTextToPdf(text, title, targetFile)
      if (success) {
        val count = pdfEngine.getPageCount(targetFile)
        val entity = DocumentEntity(
          id = UUID.randomUUID().toString(),
          title = title,
          filePath = targetFile.absolutePath,
          pageCount = count,
          fileSizeBytes = targetFile.length()
        )
        repository.insertOrUpdateDocument(entity)
        showMessage("Created PDF from text!")
        onDone(targetFile)
      } else {
        showMessage("Failed to create PDF from text")
      }
    }
  }

  fun executeExportWord(doc: DocumentEntity, onDone: (File) -> Unit) {
    viewModelScope.launch {
      val sourceFile = File(doc.filePath)
      val targetFile = File(getApplication<Application>().cacheDir, "${sourceFile.nameWithoutExtension}_summary.doc")
      val annotations = _documentAnnotations.value
      val success = pdfEngine.exportToWordDocument(sourceFile, annotations, targetFile)
      if (success) {
        showMessage("Exported Word (.doc) document!")
        onDone(targetFile)
      } else {
        showMessage("Failed to export Word document")
      }
    }
  }

  fun executeExportExcel(doc: DocumentEntity, onDone: (File) -> Unit) {
    viewModelScope.launch {
      val sourceFile = File(doc.filePath)
      val targetFile = File(getApplication<Application>().cacheDir, "${sourceFile.nameWithoutExtension}_annotations.csv")
      val annotations = _documentAnnotations.value
      val success = pdfEngine.exportToExcelSpreadsheet(sourceFile, annotations, targetFile)
      if (success) {
        showMessage("Exported Excel spreadsheet (.csv)!")
        onDone(targetFile)
      } else {
        showMessage("Failed to export Excel spreadsheet")
      }
    }
  }

  fun executeExportText(doc: DocumentEntity, onDone: (File) -> Unit) {
    viewModelScope.launch {
      val sourceFile = File(doc.filePath)
      val targetFile = File(getApplication<Application>().cacheDir, "${sourceFile.nameWithoutExtension}_extracted.txt")
      val annotations = _documentAnnotations.value
      val success = pdfEngine.exportToPlainText(sourceFile, annotations, targetFile)
      if (success) {
        showMessage("Exported plain text file (.txt)!")
        onDone(targetFile)
      } else {
        showMessage("Failed to export text file")
      }
    }
  }

  fun executeConvertPdfToImages(doc: DocumentEntity, onDone: (List<File>) -> Unit) {
    viewModelScope.launch {
      val sourceFile = File(doc.filePath)
      val images = pdfEngine.convertPdfToImages(sourceFile)
      if (images.isNotEmpty()) {
        showMessage("Rendered ${images.size} pages as JPG images!")
        onDone(images)
      } else {
        showMessage("Failed to convert PDF to images")
      }
    }
  }
}
