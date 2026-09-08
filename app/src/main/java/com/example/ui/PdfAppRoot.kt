package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.DocumentListScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.ToolsScreen

enum class AppScreen {
  DOCUMENTS,
  TOOLS,
  VIEWER
}

@Composable
fun PdfAppRoot(
  viewModel: PdfViewModel = viewModel()
) {
  var currentScreen by remember { mutableStateOf(AppScreen.DOCUMENTS) }
  val activeDoc by viewModel.currentDocument.collectAsStateWithLifecycle()
  val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(snackbarMessage) {
    snackbarMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearSnackbar()
    }
  }

  BackHandler(enabled = currentScreen != AppScreen.DOCUMENTS) {
    if (currentScreen == AppScreen.VIEWER) {
      viewModel.closeDocument()
      currentScreen = AppScreen.DOCUMENTS
    } else {
      currentScreen = AppScreen.DOCUMENTS
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      if (currentScreen != AppScreen.VIEWER) {
        NavigationBar(
          modifier = Modifier.testTag("main_navigation_bar")
        ) {
          NavigationBarItem(
            selected = currentScreen == AppScreen.DOCUMENTS,
            onClick = { currentScreen = AppScreen.DOCUMENTS },
            icon = { Icon(Icons.Default.Description, contentDescription = "Documents") },
            label = { Text("Documents") },
            modifier = Modifier.testTag("nav_item_documents")
          )
          NavigationBarItem(
            selected = currentScreen == AppScreen.TOOLS,
            onClick = { currentScreen = AppScreen.TOOLS },
            icon = { Icon(Icons.Default.Build, contentDescription = "PDF Tools") },
            label = { Text("PDF Tools") },
            modifier = Modifier.testTag("nav_item_tools")
          )
        }
      }
    }
  ) { innerPadding ->
    androidx.compose.foundation.layout.Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(if (currentScreen == AppScreen.VIEWER) androidx.compose.foundation.layout.PaddingValues() else innerPadding)
    ) {
      when (currentScreen) {
        AppScreen.DOCUMENTS -> {
          DocumentListScreen(
            viewModel = viewModel,
            onOpenDocument = {
              currentScreen = AppScreen.VIEWER
            },
            onNavigateToTools = {
              currentScreen = AppScreen.TOOLS
            }
          )
        }
        AppScreen.TOOLS -> {
          ToolsScreen(
            viewModel = viewModel,
            onBack = { currentScreen = AppScreen.DOCUMENTS }
          )
        }
        AppScreen.VIEWER -> {
          PdfViewerScreen(
            viewModel = viewModel,
            onBack = { currentScreen = AppScreen.DOCUMENTS }
          )
        }
      }
    }
  }
}
