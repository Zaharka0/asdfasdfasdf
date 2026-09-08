package com.example.ui.components

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DocumentEntity
import com.example.pdf.PdfEngine
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThumbnailsSheet(
  document: DocumentEntity,
  pdfEngine: PdfEngine,
  currentPageIndex: Int,
  pageCount: Int,
  onDismiss: () -> Unit,
  onSelectPage: (index: Int) -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val thumbnailBitmaps = remember { mutableStateMapOf<Int, Bitmap?>() }

  LaunchedEffect(document.id) {
    val file = File(document.filePath)
    for (i in 0 until pageCount) {
      val bmp = pdfEngine.renderPageBitmap(file, i, targetWidth = 300)
      thumbnailBitmaps[i] = bmp
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.testTag("thumbnails_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .fillMaxHeight(0.7f)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Page Thumbnails",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "$pageCount pages in document",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close")
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(pageCount) { pageIdx ->
          val isSelected = pageIdx == currentPageIndex
          val bmp = thumbnailBitmaps[pageIdx]

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .clickable {
                onSelectPage(pageIdx)
                onDismiss()
              }
              .testTag("thumbnail_page_$pageIdx")
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(
                  width = if (isSelected) 3.dp else 1.dp,
                  color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCBD5E1),
                  shape = RoundedCornerShape(8.dp)
                ),
              contentAlignment = Alignment.Center
            ) {
              if (bmp != null) {
                Image(
                  bitmap = bmp.asImageBitmap(),
                  contentDescription = "Page ${pageIdx + 1}",
                  contentScale = ContentScale.Fit,
                  modifier = Modifier.padding(4.dp)
                )
              } else {
                CircularProgressIndicator(
                  strokeWidth = 2.dp,
                  modifier = Modifier.padding(16.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Page ${pageIdx + 1}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }
  }
}
