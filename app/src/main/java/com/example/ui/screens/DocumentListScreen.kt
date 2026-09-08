package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.DocumentEntity
import com.example.ui.PdfViewModel
import com.example.ui.dialogs.CompressDialog
import com.example.ui.dialogs.PasswordProtectionDialog
import com.example.ui.dialogs.SplitPdfDialog
import com.example.ui.dialogs.TextToPdfDialog
import com.example.ui.dialogs.WatermarkDialog
import com.example.util.DocumentShareHelper
import com.example.util.PdfPrintAdapter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
  viewModel: PdfViewModel,
  onOpenDocument: (DocumentEntity) -> Unit,
  onNavigateToTools: () -> Unit
) {
  val context = LocalContext.current
  val documents by viewModel.filteredDocuments.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  var isSearchActive by remember { mutableStateOf(false) }
  var selectedTab by remember { mutableIntStateOf(0) } // 0: All, 1: Vault

  // Action Dialog states
  var docForSplit by remember { mutableStateOf<DocumentEntity?>(null) }
  var docForCompress by remember { mutableStateOf<DocumentEntity?>(null) }
  var docForWatermark by remember { mutableStateOf<DocumentEntity?>(null) }
  var docForPin by remember { mutableStateOf<DocumentEntity?>(null) }
  var showTextToPdf by remember { mutableStateOf(false) }

  // Document PIN verification when opening locked document
  var docToUnlock by remember { mutableStateOf<DocumentEntity?>(null) }

  // Launchers
  val openPdfLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    uri?.let {
      val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
      viewModel.importDocumentFromUri(it, name)
    }
  }

  val pickImagesLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      viewModel.executeConvertImagesToPdf(uris, "Scanned_Images_${System.currentTimeMillis() % 10000}") { }
    }
  }

  // Thumbnails cache for document list
  val thumbnails = remember { mutableStateMapOf<String, Bitmap?>() }

  LaunchedEffect(documents) {
    for (doc in documents) {
      if (!thumbnails.containsKey(doc.id)) {
        val file = File(doc.filePath)
        val bmp = viewModel.pdfEngine.renderPageBitmap(file, 0, targetWidth = 180)
        thumbnails[doc.id] = bmp
      }
    }
  }

  val displayedDocs = if (selectedTab == 1) {
    documents.filter { it.isPasswordProtected }
  } else {
    documents
  }

  Scaffold(
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        ),
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
              painter = painterResource(id = R.drawable.app_logo),
              contentDescription = "App Logo",
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "PDF Editor",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
          }
        },
        actions = {
          IconButton(
            onClick = { isSearchActive = !isSearchActive },
            modifier = Modifier.testTag("search_toggle_button")
          ) {
            Icon(
              if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
              contentDescription = "Search"
            )
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Search Bar
      AnimatedVisibility(visible = isSearchActive) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { viewModel.searchQuery.value = it },
          placeholder = { Text("Search documents by title...") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("search_text_input")
        )
      }

      // Quick Actions Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
          modifier = Modifier
            .weight(1f)
            .testTag("open_pdf_action_button"),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(vertical = 12.dp)
        ) {
          Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Open PDF", maxLines = 1)
        }

        FilledTonalButton(
          onClick = {
            pickImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          },
          modifier = Modifier
            .weight(1f)
            .testTag("images_to_pdf_action_button"),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(vertical = 12.dp)
        ) {
          Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Photos to PDF", maxLines = 1)
        }
      }

      // Tabs: All Documents, Security Vault
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surface
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("All Documents (${documents.size})", fontWeight = FontWeight.SemiBold) },
          icon = { Icon(Icons.Default.Article, contentDescription = null) },
          modifier = Modifier.testTag("tab_all_documents")
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Security Vault", fontWeight = FontWeight.SemiBold) },
          icon = { Icon(Icons.Default.Lock, contentDescription = null) },
          modifier = Modifier.testTag("tab_vault")
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Documents List
      if (displayedDocs.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              Icons.Default.PictureAsPdf,
              contentDescription = null,
              modifier = Modifier.size(64.dp),
              tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = if (selectedTab == 1) "No locked documents in vault" else "No documents found",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = if (selectedTab == 1) "Use the document menu (⋮) to set a PIN code." else "Open a PDF or convert images to get started.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("documents_lazy_column")
        ) {
          items(displayedDocs, key = { it.id }) { doc ->
            DocumentItemCard(
              document = doc,
              thumbnail = thumbnails[doc.id],
              onClick = {
                if (doc.isPasswordProtected) {
                  docToUnlock = doc
                } else {
                  viewModel.openDocument(doc)
                  onOpenDocument(doc)
                }
              },
              onShare = { DocumentShareHelper.shareFile(context, File(doc.filePath)) },
              onPrint = { PdfPrintAdapter.printPdf(context, File(doc.filePath)) },
              onSplit = { docForSplit = doc },
              onCompress = { docForCompress = doc },
              onWatermark = { docForWatermark = doc },
              onPinLock = { docForPin = doc },
              onExportWord = {
                viewModel.executeExportWord(doc) { wordFile ->
                  DocumentShareHelper.shareFile(context, wordFile, mimeType = "application/msword", title = "Share Word Document")
                }
              },
              onExportExcel = {
                viewModel.executeExportExcel(doc) { csvFile ->
                  DocumentShareHelper.shareFile(context, csvFile, mimeType = "text/comma-separated-values", title = "Share Spreadsheet")
                }
              },
              onExportText = {
                viewModel.executeExportText(doc) { txtFile ->
                  DocumentShareHelper.shareFile(context, txtFile, mimeType = "text/plain", title = "Share Text File")
                }
              },
              onExportImages = {
                viewModel.executeConvertPdfToImages(doc) { images ->
                  DocumentShareHelper.shareMultipleFiles(context, images, title = "Share Rendered JPG Pages")
                }
              },
              onDelete = { viewModel.deleteDocument(doc) }
            )
          }
        }
      }
    }

    // Split Dialog
    docForSplit?.let { doc ->
      SplitPdfDialog(
        maxPages = doc.pageCount,
        onDismiss = { docForSplit = null },
        onConfirm = { start, end ->
          viewModel.executeSplitPdf(doc, start, end) { }
          docForSplit = null
        }
      )
    }

    // Compress Dialog
    docForCompress?.let { doc ->
      CompressDialog(
        onDismiss = { docForCompress = null },
        onConfirm = { quality ->
          viewModel.executeCompressPdf(doc, quality) { }
          docForCompress = null
        }
      )
    }

    // Watermark Dialog
    docForWatermark?.let { doc ->
      WatermarkDialog(
        onDismiss = { docForWatermark = null },
        onConfirm = { text ->
          viewModel.executeWatermarkPdf(doc, text) { }
          docForWatermark = null
        }
      )
    }

    // PIN Protection Dialog
    docForPin?.let { doc ->
      PasswordProtectionDialog(
        documentTitle = doc.title,
        currentPin = doc.passwordPin,
        onDismiss = { docForPin = null },
        onConfirm = { pin ->
          viewModel.setDocumentPin(doc, pin)
          docForPin = null
        }
      )
    }

    // Unlock Password Dialog
    docToUnlock?.let { doc ->
      PasswordProtectionDialog(
        documentTitle = doc.title,
        currentPin = doc.passwordPin,
        onDismiss = { docToUnlock = null },
        onConfirm = {
          viewModel.openDocument(doc)
          onOpenDocument(doc)
          docToUnlock = null
        }
      )
    }

    // Text to PDF Dialog
    if (showTextToPdf) {
      TextToPdfDialog(
        onDismiss = { showTextToPdf = false },
        onConfirm = { title, text ->
          viewModel.executeConvertTextToPdf(text, title) { }
          showTextToPdf = false
        }
      )
    }
  }
}

@Composable
fun DocumentItemCard(
  document: DocumentEntity,
  thumbnail: Bitmap?,
  onClick: () -> Unit,
  onShare: () -> Unit,
  onPrint: () -> Unit,
  onSplit: () -> Unit,
  onCompress: () -> Unit,
  onWatermark: () -> Unit,
  onPinLock: () -> Unit,
  onExportWord: () -> Unit,
  onExportExcel: () -> Unit,
  onExportText: () -> Unit,
  onExportImages: () -> Unit,
  onDelete: () -> Unit
) {
  var menuExpanded by remember { mutableStateOf(false) }
  val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
  val sizeKb = (document.fileSizeBytes / 1024).coerceAtLeast(1)

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("document_card_${document.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Thumbnail Box
      Box(
        modifier = Modifier
          .width(60.dp)
          .aspectRatio(0.72f)
          .clip(RoundedCornerShape(6.dp))
          .background(Color.White)
          .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
      ) {
        if (thumbnail != null) {
          Image(
            bitmap = thumbnail.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
          )
        } else {
          Icon(
            Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = Color(0xFFDC2626),
            modifier = Modifier.size(28.dp)
          )
        }
      }

      Spacer(modifier = Modifier.width(14.dp))

      // Info
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = document.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          if (document.isPasswordProtected) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              Icons.Default.Lock,
              contentDescription = "Locked",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
          ) {
            Text(
              text = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          Text(
            text = "${sizeKb} KB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Text(
            text = "•",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Text(
            text = dateFormat.format(Date(document.lastOpenedTimestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Three dots Options Menu
      Box {
        IconButton(
          onClick = { menuExpanded = true },
          modifier = Modifier.testTag("doc_menu_button_${document.id}")
        ) {
          Icon(Icons.Default.MoreVert, contentDescription = "Document Options")
        }

        DropdownMenu(
          expanded = menuExpanded,
          onDismissRequest = { menuExpanded = false }
        ) {
          DropdownMenuItem(
            text = { Text("Share Document") },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onShare()
            }
          )
          DropdownMenuItem(
            text = { Text("Print Document") },
            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onPrint()
            }
          )
          DropdownMenuItem(
            text = { Text("Split Pages") },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onSplit()
            }
          )
          DropdownMenuItem(
            text = { Text("Compress Size") },
            leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onCompress()
            }
          )
          DropdownMenuItem(
            text = { Text("Add Watermark") },
            leadingIcon = { Icon(Icons.Default.WaterDrop, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onWatermark()
            }
          )
          DropdownMenuItem(
            text = { Text("Export to Word (.doc)") },
            leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onExportWord()
            }
          )
          DropdownMenuItem(
            text = { Text("Export to Excel (.csv)") },
            leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onExportExcel()
            }
          )
          DropdownMenuItem(
            text = { Text("Export to Text (.txt)") },
            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onExportText()
            }
          )
          DropdownMenuItem(
            text = { Text("Export Pages as Images (.jpg)") },
            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
            onClick = {
              menuExpanded = false
              onExportImages()
            }
          )
          DropdownMenuItem(
            text = { Text(if (document.isPasswordProtected) "Change/Remove PIN" else "Protect with PIN") },
            leadingIcon = {
              Icon(
                if (document.isPasswordProtected) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null
              )
            },
            onClick = {
              menuExpanded = false
              onPinLock()
            }
          )
          DropdownMenuItem(
            text = { Text("Delete Document", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = {
              menuExpanded = false
              onDelete()
            }
          )
        }
      }
    }
  }
}
