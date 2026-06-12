package com.notifyai.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notifyai.ai.engine.ModelDownloader
import com.notifyai.domain.model.SummaryResult
import com.notifyai.ui.theme.ActionColor
import com.notifyai.ui.theme.ImportantColor
import com.notifyai.ui.theme.PromotionColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navigateToHistory: () -> Unit,
    navigateToInsights: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val summary by viewModel.latestSummary.collectAsState()
    val isSummarizing by viewModel.isSummarizing.collectAsState()
    val isModelGenerating by viewModel.isModelGenerating.collectAsState()
    val llmErrorMessage by viewModel.llmErrorMessage.collectAsState()
    val downloadState by viewModel.modelDownloadState.collectAsState()

    LaunchedEffect(summary, isSummarizing, isModelGenerating) {
        if (isSummarizing && summary != null && !isModelGenerating) {
            viewModel.onSummarizationFinished()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NotifyAI", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = navigateToHistory) {
                        Icon(Icons.Default.List, contentDescription = "History")
                    }
                    IconButton(onClick = navigateToInsights) {
                        Icon(Icons.Default.Insights, contentDescription = "Insights")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!isSummarizing) viewModel.forceSummarize() },
                icon = { Icon(Icons.Default.AutoAwesome, "Summarize") },
                text = { Text(if (isSummarizing) "Summarizing..." else "Summarize Now") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Today's Setup",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            // First-run model download (~1 GB from Hugging Face). Surface progress
            // so the user knows the long wait is a download, not a hang.
            when (val ds = downloadState) {
                is ModelDownloader.State.Downloading -> item { ModelDownloadCard(ds) }
                is ModelDownloader.State.Failed -> item {
                    ModelDownloadFailedCard(ds.message, onRetry = { viewModel.forceSummarize() })
                }
                else -> {}
            }

            if (summary == null) {
            if (summary == null) {
                item {
                    EmptySummaryState(
                        isLoading = isSummarizing || isModelGenerating,
                        isGenerating = isModelGenerating
                    )
                }
                if (llmErrorMessage != null) {
                    item {
                        LocalModelErrorState(
                            message = llmErrorMessage.orEmpty(),
                            onRetry = { viewModel.forceSummarize() }
                        )
                    }
                }
            }

            if (isModelGenerating && summary != null) {
                item {
                    GeneratingStatusBanner()
                }
            } else {
                item {
                    SummaryCard(summary?.summary ?: "No Summary available")
                }
                
                item {
                    CategorySection("Important", summary?.importantItems ?: listOf(), ImportantColor)
                }
                
                item {
                    CategorySection("Action Items", summary?.actionItems ?: listOf(), ActionColor)
                }

                item {
                    CategorySection("Promotions", summary?.promotions ?: listOf(), PromotionColor)
                }
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) } // FAB padding
        }
    }
}

@Composable
fun SummaryCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Overview", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun CategorySection(title: String, items: List<String>, color: Color) {
    if (items.isEmpty()) return
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        items.forEach { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "- $item",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun EmptySummaryState(isLoading: Boolean = false, isGenerating: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when {
                    isGenerating -> "Local model is generating in the background..."
                    isLoading -> "Generating your local summary..."
                    else -> "No summary generated yet."
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    isGenerating -> "The on-device model is actively producing a summary right now."
                    isLoading -> "The on-device model is starting up and summarizing your notifications locally."
                    else -> "Sit back and wait for notifications to arrive, or press 'Summarize Now' to force generation."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun GeneratingStatusBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Local model running",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Generating a summary in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun ModelDownloadCard(state: ModelDownloader.State.Downloading) {
    val mb = 1024L * 1024L
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Downloading on-device model",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (state.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { state.bytesDownloaded.toFloat() / state.totalBytes.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${state.bytesDownloaded / mb} MB of ${state.totalBytes / mb} MB (${state.percent}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${state.bytesDownloaded / mb} MB downloaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "One-time download. Summaries will start as soon as it finishes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun ModelDownloadFailedCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Model download failed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("Retry download") }
        }
    }
}

@Composable
fun LocalModelErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Local model unavailable",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) {
                Text("Retry local model")
            }
        }
    }
}
