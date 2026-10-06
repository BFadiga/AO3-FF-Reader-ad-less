package com.ao3reader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ao3reader.Ao3App
import com.ao3reader.AppContainer

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as Ao3App).container

/** A ViewModel scoped to the current navigation entry, built from the app container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

fun Throwable.userMessage(): String = when (this) {
    is com.ao3reader.data.remote.Ao3Exception -> message ?: "AO3 error"
    is com.ao3reader.data.remote.ffn.FfnException -> message ?: "FanFiction.net error"
    is com.ao3reader.data.remote.web.VerificationNeededException -> message.orEmpty()
    is java.net.UnknownHostException -> "No internet connection."
    is java.net.SocketTimeoutException -> "The site took too long to answer. Try again."
    is java.io.IOException -> "Couldn't reach the site (${message ?: "network error"})."
    else -> message ?: toString()
}

/** The page to open by hand when [this] is a "verify you're human" check, else null. */
fun Throwable.verificationUrl(): String? = (this as? com.ao3reader.data.remote.web.VerificationNeededException)?.url
