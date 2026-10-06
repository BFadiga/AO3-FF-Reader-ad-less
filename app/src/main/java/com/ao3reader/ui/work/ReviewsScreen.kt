package com.ao3reader.ui.work

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ao3reader.AppContainer
import com.ao3reader.data.model.Review
import com.ao3reader.data.model.WorkIds
import com.ao3reader.data.remote.ffn.FfnUrls
import com.ao3reader.ui.Navigator
import com.ao3reader.ui.components.ScreenScaffold
import com.ao3reader.ui.components.appViewModel
import com.ao3reader.ui.components.userMessage
import kotlinx.coroutines.launch

class ReviewsViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    var reviews by mutableStateOf<List<Review>>(emptyList()); private set
    var page by mutableStateOf(0); private set
    var totalPages by mutableStateOf(1); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    init { more() }

    fun more() {
        if (loading || (page > 0 && page >= totalPages)) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val result = c.ffn.reviews(id, page + 1)
                reviews = reviews + result.reviews
                page = result.page
                totalPages = result.totalPages
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }
}

/** A FanFiction.net story's reviews, read in the app; writing one opens the site's review box. */
@Composable
fun ReviewsScreen(id: Long, nav: Navigator) {
    val vm = appViewModel(key = "reviews-$id") { ReviewsViewModel(it, id) }
    ScreenScaffold(
        title = "Reviews",
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { nav.web(FfnUrls.chapter(WorkIds.remote(id), 1) + "#review_name_value") }) {
                Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "Write a review")
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.reviews) { r ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        if (r.meta.isNotBlank()) {
                            Text(r.meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(r.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    when {
                        vm.loading -> CircularProgressIndicator()
                        vm.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(vm.error!!, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { vm.more() }) { Text("Retry") }
                        }
                        vm.reviews.isEmpty() -> Text("No reviews yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        vm.page < vm.totalPages -> OutlinedButton(onClick = { vm.more() }) { Text("More reviews") }
                    }
                }
            }
        }
    }
}
