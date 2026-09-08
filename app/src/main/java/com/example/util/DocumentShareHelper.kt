package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object DocumentShareHelper {

  fun getFileUri(context: Context, file: File): Uri {
    return FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      file
    )
  }

  fun shareFile(
    context: Context,
    file: File,
    mimeType: String = "application/pdf",
    title: String = "Share Document"
  ) {
    val uri = getFileUri(context, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, file.name)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, title)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
  }

  fun shareMultipleFiles(
    context: Context,
    files: List<File>,
    mimeType: String = "image/jpeg",
    title: String = "Share Files"
  ) {
    val uris = ArrayList<Uri>(files.map { getFileUri(context, it) })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
      type = mimeType
      putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, title)
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
  }

  fun sendEmail(
    context: Context,
    file: File,
    subject: String = "Document: ${file.name}",
    body: String = "Please find the attached PDF document."
  ) {
    val uri = getFileUri(context, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "message/rfc822"
      putExtra(Intent.EXTRA_SUBJECT, subject)
      putExtra(Intent.EXTRA_TEXT, body)
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "Send Email via")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
  }
}
