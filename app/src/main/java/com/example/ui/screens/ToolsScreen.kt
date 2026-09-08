package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
  viewModel: PdfViewModel,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val documents by viewModel.filteredDocuments.collectAsStateWithLifecycle()

  var selectedDocForTool by remember { mutableStateOf<DocumentEntity?>(null) }
  var activeToolType by remember { mutableStateOf<String?>(null) }
  var showSelectDocDialog by remember { mutableStateOf(false) }

  // Tool dialog states
  var showSplitDialog by remember { mutableStateOf(false) }
  var showCompressDialog by remember { mutableStateOf(false) }
  var showWatermarkDialog by remember { mutableStateOf(false) }
  var showPinDialog by remember { mutableStateOf(false) }
  var showTextToPdfDialog by remember { mutableStateOf(false) }

  // Images to PDF launcher
  val pickImagesLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      viewModel.executeConvertImagesToPdf(uris, "Converted_Images_${System.currentTimeMillis() % 10000}") { }
    }
  }

  fun triggerTool(tool: String) {
    activeToolType = tool
    when (tool) {
      "IMAGES_TO_PDF" -> {
        pickImagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      }
      "TEXT_TO_PDF" -> {
        showTextToPdfDialog = true
      }
      "MERGE" -> {
        if (documents.size >= 2) {
          val files = documents.take(3).map { File(it.filePath) }
          viewModel.executeMergePdfs(files, "Merged_Document_${System.currentTimeMillis() % 1000}") { }
        } else {
          viewModel.showMessage("At least 2 documents needed to merge")
        }
      }
      else -> {
        // Needs a selected document
        if (documents.isNotEmpty()) {
          selectedDocForTool = documents.first()
          when (tool) {
            "SPLIT" -> showSplitDialog = true
            "COMPRESS" -> showCompressDialog = true
            "WATERMARK" -> showWatermarkDialog = true
            "PIN" -> showPinDialog = true
            "PDF_TO_IMAGES" -> {
              viewModel.executeConvertPdfToImages(selectedDocForTool!!) { images ->
                DocumentShareHelper.shareMultipleFiles(context, images, title = "Share Rendered JPG Pages")
              }
            }
            "PDF_TO_WORD" -> {
              viewModel.executeExportWord(selectedDocForTool!!) { file ->
                DocumentShareHelper.shareFile(context, file, mimeType = "application/msword", title = "Share Word Document")
              }
            }
            "PDF_TO_EXCEL" -> {
              viewModel.executeExportExcel(selectedDocForTool!!) { file ->
                DocumentShareHelper.shareFile(context, file, mimeType = "text/comma-separated-values", title = "Share Spreadsheet")
              }
            }
            "PDF_TO_TEXT" -> {
              viewModel.executeExportText(selectedDocForTool!!) { file ->
                DocumentShareHelper.shareFile(context, file, mimeType = "text/plain", title = "Share Text File")
              }
            }
            "PRINT" -> {
              PdfPrintAdapter.printPdf(context, File(selectedDocForTool!!.filePath))
            }
          }
        } else {
          viewModel.showMessage("No documents available. Open a document first.")
        }
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        ),
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Text(
            text = "PDF Tools & Conversions",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
          )
        }
      )
    }
  ) { innerPadding ->
    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      contentPadding = PaddingValues(16.dp),
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      item {
        ToolDashboardCard(
          title = "Merge PDFs",
          subtitle = "Combine multiple PDFs into 1",
          icon = Icons.Default.Merge,
          color = Color(0xFF0066FF),
          onClick = { triggerTool("MERGE") }
        )
      }

      item {
        ToolDashboardCard(
          title = "Split PDF",
          subtitle = "Extract page ranges",
          icon = Icons.Default.VerticalSplit,
          color = Color(0xFF0284C7),
          onClick = { triggerTool("SPLIT") }
        )
      }

      item {
        ToolDashboardCard(
          title = "Compress PDF",
          subtitle = "Reduce document file size",
          icon = Icons.Default.Compress,
          color = Color(0xFF10B981),
          onClick = { triggerTool("COMPRESS") }
        )
      }

      item {
        ToolDashboardCard(
          title = "Add Watermark",
          subtitle = "Overlay text or status",
          icon = Icons.Default.WaterDrop,
          color = Color(0xFFEF4444),
          onClick = { triggerTool("WATERMARK") }
        )
      }

      item {
        ToolDashboardCard(
          title = "Photos to PDF",
          subtitle = "Create PDF from images",
          icon = Icons.Default.Image,
          color = Color(0xFF8B5CF6),
          onClick = { triggerTool("IMAGES_TO_PDF") }
        )
      }

      item {
        ToolDashboardCard(
          title = "PDF to Images",
          subtitle = "Export pages as .jpg",
          icon = Icons.Default.FileDownload,
          color = Color(0xFFEC4899),
          onClick = { triggerTool("PDF_TO_IMAGES") }
        )
      }

      item {
        ToolDashboardCard(
          title = "PDF to Word",
          subtitle = "Export to .doc document",
          icon = Icons.Default.Description,
          color = Color(0xFF2563EB),
          onClick = { triggerTool("PDF_TO_WORD") }
        )
      }

      item {
        ToolDashboardCard(
          title = "PDF to Excel",
          subtitle = "Export annotations to .csv",
          icon = Icons.Default.GridOn,
          color = Color(0xFF059669),
          onClick = { triggerTool("PDF_TO_EXCEL") }
        )
      }

      item {
        ToolDashboardCard(
          title = "PDF to Text",
          subtitle = "Extract text to .txt",
          icon = Icons.Default.TextFields,
          color = Color(0xFFD97706),
          onClick = { triggerTool("PDF_TO_TEXT") }
        )
      }

      item {
        ToolDashboardCard(
          title = "Text to PDF",
          subtitle = "Type notes & make PDF",
          icon = Icons.Default.PictureAsPdf,
          color = Color(0xFF4F46E5),
          onClick = { triggerTool("TEXT_TO_PDF") }
        )
      }

      item {
        ToolDashboardCard(
          title = "Print Document",
          subtitle = "System print service",
          icon = Icons.Default.Print,
          color = Color(0xFF475569),
          onClick = { triggerTool("PRINT") }
        )
      }

      item {
        ToolDashboardCard(
          title = "Security PIN Vault",
          subtitle = "Protect with passcode",
          icon = Icons.Default.Lock,
          color = Color(0xFF64748B),
          onClick = { triggerTool("PIN") }
        )
      }
    }

    // Split Dialog
    if (showSplitDialog && selectedDocForTool != null) {
      SplitPdfDialog(
        maxPages = selectedDocForTool!!.pageCount,
        onDismiss = { showSplitDialog = false },
        onConfirm = { start, end ->
          viewModel.executeSplitPdf(selectedDocForTool!!, start, end) { }
          showSplitDialog = false
        }
      )
    }

    // Compress Dialog
    if (showCompressDialog && selectedDocForTool != null) {
      CompressDialog(
        onDismiss = { showCompressDialog = false },
        onConfirm = { quality ->
          viewModel.executeCompressPdf(selectedDocForTool!!, quality) { }
          showCompressDialog = false
        }
      )
    }

    // Watermark Dialog
    if (showWatermarkDialog && selectedDocForTool != null) {
      WatermarkDialog(
        onDismiss = { showWatermarkDialog = false },
        onConfirm = { text ->
          viewModel.executeWatermarkPdf(selectedDocForTool!!, text) { }
          showWatermarkDialog = false
        }
      )
    }

    // PIN Dialog
    if (showPinDialog && selectedDocForTool != null) {
      PasswordProtectionDialog(
        documentTitle = selectedDocForTool!!.title,
        currentPin = selectedDocForTool!!.passwordPin,
        onDismiss = { showPinDialog = false },
        onConfirm = { pin ->
          viewModel.setDocumentPin(selectedDocForTool!!, pin)
          showPinDialog = false
        }
      )
    }

    // Text to PDF Dialog
    if (showTextToPdfDialog) {
      TextToPdfDialog(
        onDismiss = { showTextToPdfDialog = false },
        onConfirm = { title, text ->
          viewModel.executeConvertTextToPdf(text, title) { }
          showTextToPdfDialog = false
        }
      )
    }
  }
}

@Composable
fun ToolDashboardCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  color: Color,
  onClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ),
    modifier = Modifier
      .fillMaxWidth()
      .height(130.dp)
      .clickable(onClick = onClick)
      .testTag("tool_card_${title.replace(" ", "_").lowercase()}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = color,
          modifier = Modifier.size(24.dp)
        )
      }

      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
      }
    }
  }
}
