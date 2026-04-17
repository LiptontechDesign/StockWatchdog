package com.stockwatchdog.app.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stockwatchdog.app.di.AppContainer
import com.stockwatchdog.app.ui.detail.describeAlert

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    container: AppContainer,
    onOpenSymbol: (String) -> Unit
) {
    val vm: AlertsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AlertsViewModel(container.database.alertDao()) }
        }
    )
    val alerts by vm.alerts.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("Alerts", fontWeight = FontWeight.SemiBold)
            })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (alerts.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No alerts yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Open a ticker and tap \"New alert\" to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(alerts, key = { it.id }) { a ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenSymbol(a.symbol) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    a.symbol,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    describeAlert(a),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = a.enabled,
                                onCheckedChange = { vm.toggle(a.id, it) }
                            )
                            TextButton(onClick = { vm.delete(a.id) }) { Text("Delete") }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}
