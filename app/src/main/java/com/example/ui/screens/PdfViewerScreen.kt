package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AnnotationEntity
import com.example.data.model.DocumentEntity
import com.example.ui.PdfViewModel
import com.example.ui.ViewerMode
import com.example.ui.components.AnnotationsSheet
import com.example.ui.components.ThumbnailsSheet
import com.example.ui.dialogs.AddCoordinateNoteDialog
import com.example.ui.dialogs.AddTextToPageDialog
import com.example.util.DocumentShareHelper
import com.example.util.PdfPrintAdapter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
  viewModel: PdfViewModel,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val currentDoc by viewModel.currentDocument.collectAsStateWithLifecycle()
  val currentPageIndex by viewModel.currentPageIndex.collectAsStateWithLifecycle()
  val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
  val currentBitmap by viewModel.currentBitmap.collectAsStateWithLifecycle()
  val isLoading by viewModel.isLoadingPage.collectAsStateWithLifecycle()
  val viewerMode by viewModel.viewerMode.collectAsStateWithLifecycle()
  val selectedColorHex by viewModel.selectedColorHex.collectAsStateWithLifecycle()
  val strokeWidth by viewModel.strokeWidth.collectAsStateWithLifecycle()
  val zoomScale by viewModel.zoomScale.collectAsStateWithLifecycle()

  val allAnnotations by viewModel.documentAnnotations.collectAsStateWithLifecycle()
  val pageAnnotations = remember(allAnnotations, currentPageIndex) {
    allAnnotations.filter { it.pageIndex == currentPageIndex }
  }

  val pageDrawings by viewModel.pageDrawings.collectAsStateWithLifecycle()
  val pageTexts by viewModel.pageTexts.collectAsStateWithLifecycle()
  val activePoints by viewModel.activeStrokePoints.collectAsStateWithLifecycle()

  val isAnnotationsSheetOpen by viewModel.isAnnotationsDrawerOpen.collectAsStateWithLifecycle()
  val isThumbnailsOpen by viewModel.isThumbnailsOpen.collectAsStateWithLifecycle()

  // Coordinate dialog states
  var showNoteDialog by remember { mutableStateOf(false) }
  var noteClickNormalizedX by remember { mutableFloatStateOf(0.5f) }
  var noteClickNormalizedY by remember { mutableFloatStateOf(0.5f) }

  var showTextDialog by remember { mutableStateOf(false) }
  var textClickX by remember { mutableFloatStateOf(50f) }
  var textClickY by remember { mutableFloatStateOf(100f) }

  var activeNotePopup by remember { mutableStateOf<AnnotationEntity?>(null) }

  // Pan offsets
  var panOffsetX by remember { mutableFloatStateOf(0f) }
  var panOffsetY by remember { mutableFloatStateOf(0f) }

  val doc = currentDoc ?: return

  Scaffold(
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        navigationIcon = {
          IconButton(
            onClick = {
              viewModel.closeDocument()
              onBack()
            },
            modifier = Modifier.testTag("viewer_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text(
              text = doc.title,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Page ${currentPageIndex + 1} of $pageCount",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
        },
        actions = {
          // Save / Burn edits to PDF
          IconButton(
            onClick = { viewModel.saveEditsToDocument { } },
            modifier = Modifier.testTag("save_edits_button")
          ) {
            Icon(
              Icons.Default.Save,
              contentDescription = "Save Changes to PDF",
              tint = MaterialTheme.colorScheme.primary
            )
          }

          // Annotations Drawer toggle
          IconButton(
            onClick = { viewModel.toggleAnnotationsDrawer(true) },
            modifier = Modifier.testTag("toggle_annotations_button")
          ) {
            BadgedBox(
              badge = {
                if (allAnnotations.isNotEmpty()) {
                  Badge { Text(allAnnotations.size.toString()) }
                }
              }
            ) {
              Icon(Icons.Default.StickyNote2, contentDescription = "Notes & Annotations")
            }
          }

          // Thumbnails
          IconButton(
            onClick = { viewModel.toggleThumbnails(true) },
            modifier = Modifier.testTag("toggle_thumbnails_button")
          ) {
            Icon(Icons.Default.GridView, contentDescription = "Thumbnails")
          }

          // Print
          IconButton(
            onClick = { PdfPrintAdapter.printPdf(context, File(doc.filePath)) },
            modifier = Modifier.testTag("print_document_button")
          ) {
            Icon(Icons.Default.Print, contentDescription = "Print PDF")
          }

          // Share
          IconButton(
            onClick = { DocumentShareHelper.shareFile(context, File(doc.filePath)) },
            modifier = Modifier.testTag("share_document_button")
          ) {
            Icon(Icons.Default.Share, contentDescription = "Share PDF")
          }
        }
      )
    },
    bottomBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .navigationBarsPadding()
          .shadow(8.dp)
      ) {
        // Mode Selector Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          ToolChip(
            label = "View",
            icon = Icons.Default.PanTool,
            selected = viewerMode == ViewerMode.VIEW_PAN,
            onClick = { viewModel.setViewerMode(ViewerMode.VIEW_PAN) },
            tag = "tool_view"
          )
          ToolChip(
            label = "Draw",
            icon = Icons.Default.Draw,
            selected = viewerMode == ViewerMode.DRAW,
            onClick = { viewModel.setViewerMode(ViewerMode.DRAW) },
            tag = "tool_draw"
          )
          ToolChip(
            label = "Highlight",
            icon = Icons.Default.Highlight,
            selected = viewerMode == ViewerMode.HIGHLIGHT,
            onClick = { viewModel.setViewerMode(ViewerMode.HIGHLIGHT) },
            tag = "tool_highlight"
          )
          ToolChip(
            label = "Note",
            icon = Icons.Default.PinDrop,
            selected = viewerMode == ViewerMode.NOTE,
            onClick = { viewModel.setViewerMode(ViewerMode.NOTE) },
            tag = "tool_note"
          )
          ToolChip(
            label = "Text",
            icon = Icons.Default.FormatColorText,
            selected = viewerMode == ViewerMode.ADD_TEXT,
            onClick = { viewModel.setViewerMode(ViewerMode.ADD_TEXT) },
            tag = "tool_text"
          )
        }

        // Secondary Palette Bar (shown for Draw, Highlight, Note)
        AnimatedVisibility(
          visible = viewerMode == ViewerMode.DRAW ||
            viewerMode == ViewerMode.HIGHLIGHT ||
            viewerMode == ViewerMode.NOTE
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
              .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            // Colors
            val colors = listOf("#FFD600", "#0066FF", "#10B981", "#EF4444", "#0F172A")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              colors.forEach { hex ->
                val c = Color(android.graphics.Color.parseColor(hex))
                Box(
                  modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(c)
                    .clickable { viewModel.setSelectedColor(hex) }
                    .then(
                      if (selectedColorHex == hex) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                      } else Modifier
                    )
                )
              }
            }

            // Stroke controls for DRAW mode
            if (viewerMode == ViewerMode.DRAW) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                IconButton(
                  onClick = { viewModel.undoLastStroke() },
                  modifier = Modifier.size(36.dp)
                ) {
                  Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", modifier = Modifier.size(18.dp))
                }
                IconButton(
                  onClick = { viewModel.clearPageDrawings() },
                  modifier = Modifier.size(36.dp)
                ) {
                  Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                }
              }
            }
          }
        }

        // Page Navigation & Scrubber Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = { viewModel.previousPage() },
            enabled = currentPageIndex > 0,
            modifier = Modifier.testTag("prev_page_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
          }

          Slider(
            value = currentPageIndex.toFloat(),
            onValueChange = { viewModel.goToPage(it.toInt()) },
            valueRange = 0f..(pageCount - 1).coerceAtLeast(0).toFloat(),
            steps = (pageCount - 2).coerceAtLeast(0),
            modifier = Modifier
              .weight(1f)
              .testTag("page_slider")
          )

          IconButton(
            onClick = { viewModel.nextPage() },
            enabled = currentPageIndex < pageCount - 1,
            modifier = Modifier.testTag("next_page_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
          }

          // Zoom In / Out
          IconButton(
            onClick = { viewModel.setZoomScale(zoomScale - 0.25f) },
            enabled = zoomScale > 0.6f
          ) {
            Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
          }
          Text(
            text = "${(zoomScale * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .clickable {
                viewModel.setZoomScale(1.0f)
                panOffsetX = 0f
                panOffsetY = 0f
              }
              .padding(horizontal = 4.dp)
          )
          IconButton(
            onClick = { viewModel.setZoomScale(zoomScale + 0.25f) },
            enabled = zoomScale < 3.5f
          ) {
            Icon(Icons.Default.Add, contentDescription = "Zoom In")
          }
        }
      }
    }
  ) { innerPadding ->
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(Color(0xFF0A0F1D)),
      contentAlignment = Alignment.Center
    ) {
      if (isLoading && currentBitmap == null) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      } else if (currentBitmap != null) {
        val bitmap = currentBitmap!!
        var viewWidth by remember { mutableFloatStateOf(1f) }
        var viewHeight by remember { mutableFloatStateOf(1f) }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
              viewWidth = coordinates.size.width.toFloat()
              viewHeight = coordinates.size.height.toFloat()
            }
            .graphicsLayer {
              scaleX = zoomScale
              scaleY = zoomScale
              translationX = panOffsetX
              translationY = panOffsetY
            }
            .pointerInput(viewerMode, zoomScale) {
              if (viewerMode == ViewerMode.VIEW_PAN) {
                detectTransformGestures { _, pan, zoom, _ ->
                  viewModel.setZoomScale(zoomScale * zoom)
                  panOffsetX += pan.x
                  panOffsetY += pan.y
                }
              }
            }
            .pointerInput(viewerMode) {
              if (viewerMode == ViewerMode.DRAW) {
                detectDragGestures(
                  onDragStart = { offset ->
                    viewModel.addStrokePoint(offset.x, offset.y)
                  },
                  onDrag = { change, _ ->
                    change.consume()
                    viewModel.addStrokePoint(change.position.x, change.position.y)
                  },
                  onDragEnd = {
                    viewModel.finishStroke()
                  }
                )
              } else if (viewerMode == ViewerMode.NOTE) {
                detectTapGestures { offset ->
                  noteClickNormalizedX = (offset.x / viewWidth).coerceIn(0f, 1f)
                  noteClickNormalizedY = (offset.y / viewHeight).coerceIn(0f, 1f)
                  showNoteDialog = true
                }
              } else if (viewerMode == ViewerMode.HIGHLIGHT) {
                detectDragGestures(
                  onDragStart = { offset ->
                    val normX = (offset.x / viewWidth).coerceIn(0f, 1f)
                    val normY = (offset.y / viewHeight).coerceIn(0f, 1f)
                    viewModel.addHighlight(normX, normY, 0.35f, 0.04f)
                  },
                  onDrag = { change, _ -> change.consume() }
                )
              } else if (viewerMode == ViewerMode.ADD_TEXT) {
                detectTapGestures { offset ->
                  textClickX = offset.x
                  textClickY = offset.y
                  showTextDialog = true
                }
              }
            },
          contentAlignment = Alignment.Center
        ) {
          // 1. Base PDF Rendered Page Image
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "PDF Page ${currentPageIndex + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
              .shadow(12.dp, RoundedCornerShape(4.dp))
              .background(Color.White)
          )

          // 2. Overlay Layer: Highlights, Drawings, Text, Notes
          Canvas(
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
          ) {
            val canvasW = size.width
            val canvasH = size.height

            // Render Highlights
            for (ann in pageAnnotations) {
              if (ann.type == "HIGHLIGHT") {
                val parsedColor = try {
                  Color(android.graphics.Color.parseColor(ann.colorHex)).copy(alpha = 0.35f)
                } catch (e: Exception) {
                  Color.Yellow.copy(alpha = 0.35f)
                }
                val left = ann.normalizedX * canvasW
                val top = ann.normalizedY * canvasH
                val rectW = ann.normalizedWidth * canvasW
                val rectH = ann.normalizedHeight * canvasH
                drawRect(
                  color = parsedColor,
                  topLeft = Offset(left, top),
                  size = Size(rectW, rectH)
                )
              }
            }

            // Render Hand-Drawing Strokes on this page
            val strokes = pageDrawings[currentPageIndex] ?: emptyList()
            for (stroke in strokes) {
              if (stroke.points.size > 1) {
                val path = Path().apply {
                  moveTo(stroke.points[0].x, stroke.points[0].y)
                  for (i in 1 until stroke.points.size) {
                    lineTo(stroke.points[i].x, stroke.points[i].y)
                  }
                }
                drawPath(
                  path = path,
                  color = Color(stroke.color),
                  style = Stroke(
                    width = stroke.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                  )
                )
              }
            }

            // Active stroke being drawn right now
            if (activePoints.size > 1) {
              val currentPath = Path().apply {
                moveTo(activePoints[0].x, activePoints[0].y)
                for (i in 1 until activePoints.size) {
                  lineTo(activePoints[i].x, activePoints[i].y)
                }
              }
              val colorInt = try {
                android.graphics.Color.parseColor(selectedColorHex)
              } catch (e: Exception) {
                android.graphics.Color.BLUE
              }
              drawPath(
                path = currentPath,
                color = Color(colorInt),
                style = Stroke(
                  width = strokeWidth,
                  cap = StrokeCap.Round,
                  join = StrokeJoin.Round
                )
              )
            }
          }

          // 3. Render Coordinate-linked Sticky Note Pins
          for (ann in pageAnnotations) {
            if (ann.type == "NOTE") {
              val pinColor = try {
                Color(android.graphics.Color.parseColor(ann.colorHex))
              } catch (e: Exception) {
                Color.Yellow
              }

              Box(
                modifier = Modifier
                  .align(Alignment.TopStart)
                  .graphicsLayer {
                    translationX = ann.normalizedX * viewWidth - 14.dp.toPx()
                    translationY = ann.normalizedY * viewHeight - 28.dp.toPx()
                  }
                  .clickable { activeNotePopup = ann }
                  .testTag("note_pin_${ann.id}")
              ) {
                Surface(
                  shape = CircleShape,
                  color = pinColor,
                  shadowElevation = 6.dp,
                  modifier = Modifier.size(28.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      Icons.Default.PinDrop,
                      contentDescription = "Note Pin",
                      tint = Color.Black,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }
            }
          }

          // 4. Render Text Elements on this page
          val texts = pageTexts[currentPageIndex] ?: emptyList()
          for (txt in texts) {
            Text(
              text = txt.text,
              fontSize = (txt.textSize * 0.7f).sp,
              fontWeight = FontWeight.Bold,
              color = Color(txt.color),
              modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer {
                  translationX = txt.x
                  translationY = txt.y
                }
            )
          }
        }
      }
    }

    // Active Note Details Popup Card
    activeNotePopup?.let { ann ->
      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier
          .padding(24.dp)
          .fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(12.dp)
                  .clip(CircleShape)
                  .background(Color(android.graphics.Color.parseColor(ann.colorHex)))
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Note on Page ${ann.pageIndex + 1}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
            }
            IconButton(
              onClick = { activeNotePopup = null },
              modifier = Modifier.size(24.dp)
            ) {
              Icon(Icons.Default.Clear, contentDescription = "Close Note")
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = ann.content,
            style = MaterialTheme.typography.bodyMedium
          )
        }
      }
    }

    // Dialogs
    if (showNoteDialog) {
      AddCoordinateNoteDialog(
        pageNumber = currentPageIndex + 1,
        xPercent = (noteClickNormalizedX * 100).toInt(),
        yPercent = (noteClickNormalizedY * 100).toInt(),
        initialColorHex = selectedColorHex,
        onDismiss = { showNoteDialog = false },
        onSave = { content, colorHex ->
          viewModel.addCoordinateNote(noteClickNormalizedX, noteClickNormalizedY, content)
          showNoteDialog = false
        }
      )
    }

    if (showTextDialog) {
      AddTextToPageDialog(
        pageNumber = currentPageIndex + 1,
        onDismiss = { showTextDialog = false },
        onConfirm = { text, textSize ->
          viewModel.addTextElement(text, textClickX, textClickY, textSize)
          showTextDialog = false
        }
      )
    }

    if (isAnnotationsSheetOpen) {
      AnnotationsSheet(
        annotations = allAnnotations,
        onDismiss = { viewModel.toggleAnnotationsDrawer(false) },
        onJumpToAnnotation = { pageIdx ->
          viewModel.goToPage(pageIdx)
        },
        onDeleteAnnotation = { id ->
          viewModel.deleteAnnotation(id)
        }
      )
    }

    if (isThumbnailsOpen) {
      ThumbnailsSheet(
        document = doc,
        pdfEngine = viewModel.pdfEngine,
        currentPageIndex = currentPageIndex,
        pageCount = pageCount,
        onDismiss = { viewModel.toggleThumbnails(false) },
        onSelectPage = { pageIdx ->
          viewModel.goToPage(pageIdx)
        }
      )
    }
  }
}

@Composable
fun ToolChip(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  tag: String
) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    leadingIcon = {
      Icon(
        imageVector = icon,
        contentDescription = label,
        modifier = Modifier.size(16.dp)
      )
    },
    colors = FilterChipDefaults.filterChipColors(
      selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
      selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
      selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
    ),
    shape = RoundedCornerShape(20.dp),
    modifier = Modifier.testTag(tag)
  )
}
