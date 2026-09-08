package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AddCoordinateNoteDialog(
  pageNumber: Int,
  xPercent: Int,
  yPercent: Int,
  initialColorHex: String,
  onDismiss: () -> Unit,
  onSave: (content: String, colorHex: String) -> Unit
) {
  var content by remember { mutableStateOf("") }
  var selectedColor by remember { mutableStateOf(initialColorHex) }
  val colors = listOf("#FFD600", "#00E5FF", "#10B981", "#EF4444", "#A855F7")

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Add Note on Page $pageNumber",
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium
      )
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Position: X: $xPercent%, Y: $yPercent%",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Color chips
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Pin Color:", style = MaterialTheme.typography.bodySmall)
          colors.forEach { hex ->
            val color = Color(android.graphics.Color.parseColor(hex))
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color)
                .clickable { selectedColor = hex }
                .then(
                  if (selectedColor == hex) {
                    Modifier.padding(2.dp)
                  } else Modifier
                )
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
          value = content,
          onValueChange = { content = it },
          label = { Text("Enter your manual note") },
          placeholder = { Text("E.g., Review clause 3.2 before signing...") },
          minLines = 3,
          maxLines = 5,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("note_input_field")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (content.isNotBlank()) {
            onSave(content, selectedColor)
          }
        },
        enabled = content.isNotBlank(),
        modifier = Modifier.testTag("save_note_button")
      ) {
        Text("Save Note")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun AddTextToPageDialog(
  pageNumber: Int,
  onDismiss: () -> Unit,
  onConfirm: (text: String, textSize: Float) -> Unit
) {
  var text by remember { mutableStateOf("") }
  var textSize by remember { mutableFloatStateOf(24f) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Default.TextFields, contentDescription = null) },
    title = { Text("Add Text Directly to Page $pageNumber") },
    text = {
      Column {
        Text(
          "This text will be permanently embedded onto the PDF document.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          label = { Text("Text content") },
          placeholder = { Text("E.g., Approved by John Doe") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("embed_text_input")
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Text Size: ${textSize.toInt()} pt", style = MaterialTheme.typography.bodySmall)
        Slider(
          value = textSize,
          onValueChange = { textSize = it },
          valueRange = 14f..48f,
          steps = 16
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (text.isNotBlank()) onConfirm(text, textSize)
        },
        enabled = text.isNotBlank(),
        modifier = Modifier.testTag("confirm_embed_text_button")
      ) {
        Text("Insert Text")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
fun PasswordProtectionDialog(
  documentTitle: String,
  currentPin: String?,
  onDismiss: () -> Unit,
  onConfirm: (pin: String?) -> Unit
) {
  var pin by remember { mutableStateOf("") }
  val isRemoving = !currentPin.isNullOrBlank()

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
    title = {
      Text(if (isRemoving) "Remove Security PIN" else "Set 4-Digit Security PIN")
    },
    text = {
      Column {
        Text(
          if (isRemoving) "Enter existing PIN to unlock \"$documentTitle\":"
          else "Protect \"$documentTitle\" with a local passcode PIN:",
          style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = pin,
          onValueChange = { if (it.length <= 6) pin = it },
          label = { Text("PIN Code") },
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pin_input_field")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (isRemoving) {
            if (pin == currentPin) onConfirm(null)
          } else {
            if (pin.length >= 4) onConfirm(pin)
          }
        },
        modifier = Modifier.testTag("confirm_pin_button")
      ) {
        Text(if (isRemoving) "Remove Lock" else "Enable Lock")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
fun SplitPdfDialog(
  maxPages: Int,
  onDismiss: () -> Unit,
  onConfirm: (start: Int, end: Int) -> Unit
) {
  var startPage by remember { mutableIntStateOf(1) }
  var endPage by remember { mutableIntStateOf(maxPages.coerceAtLeast(1)) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Split PDF Document") },
    text = {
      Column {
        Text(
          "Total pages in document: $maxPages. Choose the page range to extract into a new document.",
          style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          OutlinedTextField(
            value = startPage.toString(),
            onValueChange = { startPage = it.toIntOrNull()?.coerceIn(1, endPage) ?: 1 },
            label = { Text("From Page") },
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = endPage.toString(),
            onValueChange = { endPage = it.toIntOrNull()?.coerceIn(startPage, maxPages) ?: maxPages },
            label = { Text("To Page") },
            modifier = Modifier.weight(1f)
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(startPage, endPage) },
        modifier = Modifier.testTag("confirm_split_button")
      ) {
        Text("Split & Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
fun WatermarkDialog(
  onDismiss: () -> Unit,
  onConfirm: (watermarkText: String) -> Unit
) {
  var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
  val presets = listOf("CONFIDENTIAL", "DRAFT", "APPROVED", "SAMPLE", "INTERNAL ONLY")

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Watermark to PDF") },
    text = {
      Column {
        Text(
          "A diagonal semi-transparent watermark will be overlaid across all pages.",
          style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = watermarkText,
          onValueChange = { watermarkText = it },
          label = { Text("Watermark Text") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("watermark_input_field")
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text("Quick Presets:", style = MaterialTheme.typography.labelSmall)
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(top = 4.dp)
        ) {
          presets.take(3).forEach { preset ->
            OutlinedButton(
              onClick = { watermarkText = preset },
              shape = RoundedCornerShape(8.dp),
              contentPadding = ButtonDefaults.TextButtonContentPadding
            ) {
              Text(preset, style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { if (watermarkText.isNotBlank()) onConfirm(watermarkText) },
        enabled = watermarkText.isNotBlank(),
        modifier = Modifier.testTag("confirm_watermark_button")
      ) {
        Text("Apply Watermark")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
fun CompressDialog(
  onDismiss: () -> Unit,
  onConfirm: (quality: Int) -> Unit
) {
  var selectedQuality by remember { mutableIntStateOf(65) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Compress PDF") },
    text = {
      Column {
        Text(
          "Optimize and reduce file size by adjusting compression scale.",
          style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          "Quality: $selectedQuality% (${if (selectedQuality < 50) "High compression (Smallest size)" else if (selectedQuality < 75) "Balanced" else "High quality"})",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold
        )
        Slider(
          value = selectedQuality.toFloat(),
          onValueChange = { selectedQuality = it.toInt() },
          valueRange = 40f..90f,
          steps = 4
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(selectedQuality) },
        modifier = Modifier.testTag("confirm_compress_button")
      ) {
        Text("Compress")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

@Composable
fun TextToPdfDialog(
  onDismiss: () -> Unit,
  onConfirm: (title: String, text: String) -> Unit
) {
  var title by remember { mutableStateOf("Meeting Notes") }
  var body by remember {
    mutableStateOf(
      """
      PROJECT ACTION ITEMS & DISCUSSION
      
      1. Review PDF engine requirements and ensure zero cloud dependencies.
      2. Enable smooth pinch-to-zoom and gesture-driven coordinate annotations.
      3. Support direct hand-drawing stylus with stroke baking.
      4. Validate multi-format conversion to images, word, and spreadsheets.
      
      Next review scheduled for Friday.
      """.trimIndent()
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create PDF from Text") },
    text = {
      Column {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Document Title") },
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
          value = body,
          onValueChange = { body = it },
          label = { Text("Text content / Notes") },
          minLines = 4,
          maxLines = 8,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { if (title.isNotBlank() && body.isNotBlank()) onConfirm(title, body) },
        enabled = title.isNotBlank() && body.isNotBlank(),
        modifier = Modifier.testTag("confirm_text_to_pdf_button")
      ) {
        Text("Generate PDF")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}
