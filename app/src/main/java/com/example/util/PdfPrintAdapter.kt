package com.example.util

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class PdfPrintAdapter(
  private val pdfFile: File
) : PrintDocumentAdapter() {

  override fun onLayout(
    oldAttributes: PrintAttributes?,
    newAttributes: PrintAttributes?,
    cancellationSignal: CancellationSignal?,
    callback: LayoutResultCallback?,
    extras: Bundle?
  ) {
    if (cancellationSignal?.isCanceled == true) {
      callback?.onLayoutCancelled()
      return
    }

    val info = PrintDocumentInfo.Builder(pdfFile.name)
      .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
      .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
      .build()

    callback?.onLayoutFinished(info, true)
  }

  override fun onWrite(
    pages: Array<out PageRange>?,
    destination: ParcelFileDescriptor?,
    cancellationSignal: CancellationSignal?,
    callback: WriteResultCallback?
  ) {
    if (destination == null) {
      callback?.onWriteFailed("Destination descriptor is null")
      return
    }

    try {
      FileInputStream(pdfFile).use { input ->
        FileOutputStream(destination.fileDescriptor).use { output ->
          val buf = ByteArray(16384)
          var bytesRead: Int
          while (input.read(buf).also { bytesRead = it } >= 0) {
            if (cancellationSignal?.isCanceled == true) {
              callback?.onWriteCancelled()
              return
            }
            output.write(buf, 0, bytesRead)
          }
        }
      }
      callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    } catch (e: Exception) {
      e.printStackTrace()
      callback?.onWriteFailed(e.message)
    }
  }

  companion object {
    fun printPdf(context: Context, file: File, jobName: String = file.name) {
      val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
      val adapter = PdfPrintAdapter(file)
      printManager?.print(jobName, adapter, PrintAttributes.Builder().build())
    }
  }
}
