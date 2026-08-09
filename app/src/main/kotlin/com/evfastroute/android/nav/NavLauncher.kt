package com.evfastroute.android.nav

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

// Fires the navigation deep link built by :core NavigationLinks. https universal links open the
// native Google Maps / Waze app when installed and fall back to the browser otherwise; geo: opens
// the system maps chooser. This is the only Android-specific part of the handoff.
object NavLauncher {

    /** Opens [url] in a maps app. Returns false if nothing on the device can handle it. */
    fun open(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
