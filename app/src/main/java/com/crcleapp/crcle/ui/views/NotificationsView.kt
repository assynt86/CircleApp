package com.crcleapp.crcle.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crcleapp.crcle.ui.theme.LeagueSpartan
import com.crcleapp.crcle.ui.viewmodels.NotificationsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsView(
    onBack: () -> Unit,
    notificationsViewModel: NotificationsViewModel = viewModel()
) {
    val uiState by notificationsViewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification Log", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { notificationsViewModel.clearAllNotifications() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear All")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (uiState.isLoading && uiState.notifications.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.notifications.isEmpty()) {
                Text(
                    "No notifications logged yet.",
                    modifier = Modifier.align(Alignment.Center),
                    fontFamily = LeagueSpartan
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.notifications) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    log.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    fontFamily = LeagueSpartan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    log.body,
                                    fontSize = 16.sp,
                                    fontFamily = LeagueSpartan
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = log.timestamp?.toDate()?.let {
                                        SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(it)
                                    } ?: "Just now",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    fontFamily = LeagueSpartan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
