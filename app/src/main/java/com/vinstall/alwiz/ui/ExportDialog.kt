package com.vinstall.alwiz.ui

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vinstall.alwiz.R
import com.vinstall.alwiz.model.AppInfo
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.DialogStyle

object ExportDialog {

    fun show(activity: AppCompatActivity, app: AppInfo, onConfirm: (String?) -> Unit) {
        when (AppSettings.getDialogStyle(activity)) {
            DialogStyle.BOTTOM_SHEET -> ExportBottomSheet.show(activity.supportFragmentManager, app, onConfirm)
            DialogStyle.ALERT_DIALOG -> showAlert(activity, app, onConfirm)
        }
    }

    private fun showAlert(activity: AppCompatActivity, app: AppInfo, onConfirm: (String?) -> Unit) {
        val view = activity.layoutInflater.inflate(R.layout.dialog_export, null)
        val textInfo = view.findViewById<TextView>(R.id.text_export_info)
        val checkEncrypt = view.findViewById<MaterialCheckBox>(R.id.checkbox_encrypt)
        val layoutPassword = view.findViewById<TextInputLayout>(R.id.layout_password)
        val editPassword = view.findViewById<TextInputEditText>(R.id.edit_password)

        textInfo.text = activity.getString(R.string.export_info, app.label, app.versionName)

        checkEncrypt.setOnCheckedChangeListener { _, checked ->
            layoutPassword.isVisible = checked
            if (checked) {
                editPassword.requestFocus()
            } else {
                editPassword.text?.clear()
                layoutPassword.error = null
            }
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(activity.getString(R.string.export_dialog_title))
            .setView(view)
            .setPositiveButton(activity.getString(R.string.export_button), null)
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!checkEncrypt.isChecked) {
                    dialog.dismiss()
                    onConfirm(null)
                    return@setOnClickListener
                }
                val password = editPassword.text?.toString().orEmpty()
                if (password.isBlank()) {
                    layoutPassword.error = activity.getString(R.string.export_password_required)
                    return@setOnClickListener
                }
                dialog.dismiss()
                onConfirm(password)
            }
        }

        dialog.show()
    }
}
