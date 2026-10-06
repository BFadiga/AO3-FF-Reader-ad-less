package com.ao3reader.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ao3reader.data.model.WorkPage
import com.ao3reader.data.model.WorkSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Paged list of works loaded page by page from AO3. */
class WorkListState(
    private val scope: CoroutineScope,
    private val loader: suspend (page: Int) -> WorkPage,
) {
    var works by mutableStateOf<List<WorkSummary>>(emptyList()); private set
    var heading by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var page by mutableStateOf(0); private set
    var totalPages by mutableStateOf(1); private set
    var started by mutableStateOf(false); private set
    /** Set when the site asked for a "verify you're human" check; the UI offers to open it. */
    var verifyUrl by mutableStateOf<String?>(null); private set

    private var job: Job? = null
    private var generation = 0

    val canLoadMore get() = !loading && error == null && page < totalPages

    fun refresh() {
        job?.cancel()
        generation++
        works = emptyList()
        page = 0
        totalPages = 1
        error = null
        loading = false
        loadMore()
    }

    fun loadMore() {
        if (loading || (started && page >= totalPages)) return
        started = true
        loading = true
        error = null
        verifyUrl = null
        val next = page + 1
        val gen = generation
        job = scope.launch {
            try {
                val result = loader(next)
                val seen = works.map { it.id }.toHashSet()
                works = works + result.works.filter { it.id !in seen }
                // The loader may skip ahead past pages whose results were all filtered out.
                page = maxOf(next, result.page)
                totalPages = result.totalPages
                heading = result.heading
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.userMessage()
                verifyUrl = e.verificationUrl()
            } finally {
                if (gen == generation) loading = false
            }
        }
    }

    fun retry() {
        error = null
        loadMore()
    }
}
