package com.goepub.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Centralized application constants and external links.
 */
object AppConstants {
    const val KOFI_URL = "https://ko-fi.com/bamboysan"
    const val SAWERIA_URL = "https://saweria.co/bamboysan"
    const val GITHUB_REPO_URL = "https://github.com/bamboysan/GoEpub"

    /**
     * Safely launches an external browser intent with [ActivityNotFoundException] protection.
     */
    fun openExternalUrl(
        context: Context,
        url: String,
        onBrowserNotFound: () -> Unit
    ) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            onBrowserNotFound()
        } catch (_: Exception) {
            onBrowserNotFound()
        }
    }
}
