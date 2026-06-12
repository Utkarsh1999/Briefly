package com.notifyai.ui.insights

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.notifyai.data.local.entity.SummaryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val summaries by viewModel.historicalSummaries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
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
                Spacer(modifier = Modifier.height(8.dp))
                OverallStatsCard(summaries)
            }

            item {
                Text(
                    text = "Historical Summaries",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            items(summaries, key = { it.id }) { summary ->
                HistoricalSummaryItem(summary)
            }
        }
    }
}

@Composable
fun OverallStatsCard(summaries: List<SummaryEntity>) {
    val totalImportant = summaries.sumOf { it.importantCount }
    val totalActions = summaries.sumOf { it.actionCount }
    val totalPromotions = summaries.sumOf { it.promotionCount }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.width(8.dp))
                Text("All Time Stats", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Important Notifications: $totalImportant")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Action Items Extracted: $totalActions")
            Spacer(modifier = Modifier.height(4.dp))
            Text("Promotions Ignored: $totalPromotions")
        }
    }
}

@Composable
fun HistoricalSummaryItem(entity: SummaryEntity) {
    val timeString = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(entity.timestamp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = timeString,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Important: ${entity.importantCount} | Actions: ${entity.actionCount} | Promos: ${entity.promotionCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
