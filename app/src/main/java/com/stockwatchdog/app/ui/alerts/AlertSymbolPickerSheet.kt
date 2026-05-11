package com.stockwatchdog.app.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockwatchdog.app.domain.SymbolMatch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlertSymbolPickerSheet(
    query: String,
    searching: Boolean,
    results: List<SymbolMatch>,
    onQueryChange: (String) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(min = 200.dp, max = 540.dp)
        ) {
            Text(
                "Pick a stock for your alert",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search ticker or company (e.g. AAPL, Tesla)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (searching) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Searching\u2026", style = MaterialTheme.typography.bodySmall)
                } else if (query.isNotBlank()) {
                    TextButton(onClick = { onPick(query.trim().uppercase()) }) {
                        Text("Use \"${query.trim().uppercase()}\" directly")
                    }
                }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(results, key = { it.symbol + (it.exchange ?: "") }) { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(m.symbol) }
                            .padding(vertical = 10.dp, horizontal = 4.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.symbol, fontWeight = FontWeight.SemiBold)
                            val sub = listOfNotNull(m.name, m.exchange, m.type)
                                .joinToString(" \u2022 ")
                            if (sub.isNotBlank()) Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}
