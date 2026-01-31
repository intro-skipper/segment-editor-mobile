package org.introskipper.segmenteditor.update

import android.app.AlertDialog
import android.content.Context
import org.introskipper.segmenteditor.R

/**
 * Simple AlertDialog for update notifications
 * Replaces the Compose-based CustomDialog with native Android UI
 */
object UpdateDialog {
    
    fun show(context: Context, onInstall: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(R.string.update_heading)
            .setMessage(R.string.update_message)
            .setIcon(R.mipmap.ic_launcher)
            .setPositiveButton(R.string.button_install) { dialog, _ ->
                dialog.dismiss()
                onInstall()
            }
            .setNegativeButton(R.string.button_dismiss) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }
}
