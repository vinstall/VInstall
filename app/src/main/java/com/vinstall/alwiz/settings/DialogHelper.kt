package com.vinstall.alwiz.settings

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.vinstall.alwiz.R
import com.vinstall.alwiz.ui.ChoiceBottomSheet
import com.vinstall.alwiz.ui.ConfirmationBottomSheet
import com.vinstall.alwiz.ui.ContinueCountdown
import com.vinstall.alwiz.ui.applyDangerStyle
import com.vinstall.alwiz.model.CallerInfo
import com.vinstall.alwiz.ui.InfoBottomSheet
import com.vinstall.alwiz.ui.UntrustedCallerBottomSheet
import com.vinstall.alwiz.ui.bindCallerRow

class DialogController(
    private val onDismiss: () -> Unit,
    private val onUpdateProgress: (step: String, progress: Float) -> Unit = { _, _ -> }
) {
    fun dismiss() = onDismiss()
    fun updateProgress(step: String, progress: Float) = onUpdateProgress(step, progress)
}

object DialogHelper {

    fun showUntrustedCaller(
        activity: FragmentActivity,
        caller: CallerInfo,
        behavior: String,
        onContinue: () -> Unit,
        onBlock: (BlockDuration) -> Unit,
        onDismissed: () -> Unit,
    ): DialogController {
        return when (AppSettings.getDialogStyle(activity)) {
            DialogStyle.BOTTOM_SHEET -> {
                val sheet = UntrustedCallerBottomSheet.show(
                    fragmentManager = activity.supportFragmentManager,
                    caller = caller,
                    behavior = behavior,
                    onContinue = onContinue,
                    onBlock = onBlock,
                    onDismissed = onDismissed,
                )
                DialogController(onDismiss = { sheet.dismissQuietly() })
            }
            DialogStyle.ALERT_DIALOG -> {
                val view = activity.layoutInflater.inflate(R.layout.dialog_untrusted, null)
                bindCallerRow(view.findViewById(R.id.layout_caller), caller)
                view.findViewById<TextView>(R.id.text_behavior).text = behavior

                val builder = MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.untrusted_title)
                    .setView(view)
                    .setPositiveButton(R.string.untrusted_continue) { _, _ -> onContinue() }
                    .setNegativeButton(R.string.cancel) { _, _ -> onDismissed() }
                    .setOnCancelListener { onDismissed() }
                if (caller.packageName.isNotEmpty()) {
                    builder.setNeutralButton(R.string.untrusted_block) { _, _ ->
                        val durations = BlockDuration.values()
                        MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.untrusted_block_title)
                            .setItems(durations.map { activity.getString(it.labelRes) }.toTypedArray()) { _, which ->
                                onBlock(durations[which])
                            }
                            .setNegativeButton(R.string.cancel) { _, _ -> onDismissed() }
                            .setOnCancelListener { onDismissed() }
                            .show()
                    }
                }
                val delay = AppSettings.getUntrustedContinueDelay(activity)
                var countdown: ContinueCountdown? = null
                builder.setOnDismissListener { countdown?.cancel() }
                val dialog = builder.create()
                dialog.setOnShowListener {
                    val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    positive.applyDangerStyle()
                    if (delay > 0) {
                        positive.isEnabled = false
                        countdown = ContinueCountdown(
                            seconds = delay,
                            onTick = { positive.text = activity.getString(R.string.untrusted_continue_countdown, it) },
                            onFinish = {
                                positive.text = activity.getString(R.string.untrusted_continue)
                                positive.isEnabled = true
                            }
                        ).also { it.start() }
                    }
                }
                dialog.show()
                DialogController(onDismiss = { if (dialog.isShowing) dialog.dismiss() })
            }
        }
    }

    fun showInfo(
        activity: FragmentActivity,
        title: String,
        body: String,
        closeLabel: String,
        subtitle: String? = null,
        monospace: Boolean = false,
        primaryLabel: String? = null,
        onPrimary: (() -> Unit)? = null,
        appInfo: CallerInfo? = null,
        caller: CallerInfo? = null,
    ) {
        when (AppSettings.getDialogStyle(activity)) {
            DialogStyle.BOTTOM_SHEET -> {
                InfoBottomSheet.show(
                    fragmentManager = activity.supportFragmentManager,
                    title = title,
                    body = body,
                    secondaryLabel = closeLabel,
                    subtitle = subtitle,
                    monospace = monospace,
                    primaryLabel = primaryLabel,
                    onPrimary = onPrimary,
                    appInfo = appInfo,
                    caller = caller,
                )
            }
            DialogStyle.ALERT_DIALOG -> {
                val view = activity.layoutInflater.inflate(R.layout.dialog_history_detail, null)
                val text = view.findViewById<TextView>(R.id.text_detail)
                text.text = body
                if (monospace) {
                    text.typeface = android.graphics.Typeface.MONOSPACE
                    text.textSize = 11f
                }
                val builder = MaterialAlertDialogBuilder(activity)
                    .setTitle(title)
                    .setView(view)
                    .setNegativeButton(closeLabel, null)
                if (primaryLabel != null) {
                    builder.setPositiveButton(primaryLabel) { _, _ -> onPrimary?.invoke() }
                }
                builder.show()
            }
        }
    }

    fun showSingleChoice(
        activity: FragmentActivity,
        title: String,
        items: List<String>,
        selectedIndex: Int,
        cancelLabel: String,
        onSelected: (Int) -> Unit,
    ) {
        when (AppSettings.getDialogStyle(activity)) {
            DialogStyle.BOTTOM_SHEET -> {
                ChoiceBottomSheet.show(
                    fragmentManager = activity.supportFragmentManager,
                    title = title,
                    items = items,
                    selectedIndex = selectedIndex,
                    cancelLabel = cancelLabel,
                    onSelected = onSelected,
                )
            }
            DialogStyle.ALERT_DIALOG -> {
                MaterialAlertDialogBuilder(activity)
                    .setTitle(title)
                    .setSingleChoiceItems(items.toTypedArray(), selectedIndex) { dialog, which ->
                        dialog.dismiss()
                        onSelected(which)
                    }
                    .setNegativeButton(cancelLabel, null)
                    .show()
            }
        }
    }

    fun showConfirmation(
        activity: FragmentActivity,
        title: String,
        message: String = "",
        positiveLabel: String,
        negativeLabel: String,
        isDangerous: Boolean = false,
        appInstallInfo: ConfirmationBottomSheet.AppInstallInfo? = null,
        onDismissed: (() -> Unit)? = null,
        onConfirm: () -> Unit,
    ): DialogController {
        return when (AppSettings.getDialogStyle(activity)) {
            DialogStyle.BOTTOM_SHEET -> {
                val sheet = ConfirmationBottomSheet.show(
                    fragmentManager = activity.supportFragmentManager,
                    title = title,
                    message = message,
                    positiveLabel = positiveLabel,
                    negativeLabel = negativeLabel,
                    isDangerous = isDangerous,
                    appInstallInfo = appInstallInfo,
                    onDismissed = onDismissed,
                    onConfirm = onConfirm,
                )
                DialogController(
                    onDismiss = { if (sheet.isAdded) sheet.dismissAllowingStateLoss() },
                    onUpdateProgress = { step, progress -> sheet.setInstalling(step, progress) }
                )
            }
            DialogStyle.ALERT_DIALOG -> {
                val builder = MaterialAlertDialogBuilder(activity)
                    .setTitle(title)
                    .setPositiveButton(positiveLabel) { _, _ -> onConfirm() }
                    .setNegativeButton(negativeLabel) { _, _ -> onDismissed?.invoke() }
                    .setOnCancelListener { onDismissed?.invoke() }

                var progressLayout: View? = null
                var progressBar: LinearProgressIndicator? = null
                var progressText: TextView? = null

                if (appInstallInfo != null) {
                    val view = activity.layoutInflater.inflate(R.layout.dialog_app_info, null)
                    val imageIcon = view.findViewById<ImageView>(R.id.image_app_icon)
                    val textLabel = view.findViewById<TextView>(R.id.text_app_label)
                    val textPackage = view.findViewById<TextView>(R.id.text_package_name)
                    val textVersion = view.findViewById<TextView>(R.id.text_version_info)
                    val textSdk = view.findViewById<TextView>(R.id.text_sdk_info)
                    val sourceRow = view.findViewById<View>(R.id.layout_source)
                    val sourceDivider = view.findViewById<View>(R.id.divider_source)
                    val source = appInstallInfo.source
                    sourceRow.isVisible = source != null
                    sourceDivider.isVisible = source != null
                    if (source != null) bindCallerRow(sourceRow, source)
                    progressLayout = view.findViewById(R.id.layout_progress)
                    progressBar = view.findViewById(R.id.progress_install)
                    progressText = view.findViewById(R.id.text_progress_status)

                    if (appInstallInfo.icon != null) {
                        imageIcon.setImageBitmap(appInstallInfo.icon)
                        imageIcon.isVisible = true
                    } else {
                        imageIcon.isVisible = false
                    }

                    textLabel.text = appInstallInfo.appLabel.ifBlank { appInstallInfo.packageName }
                    textPackage.text = appInstallInfo.packageName

                    val versionLine = ConfirmationBottomSheet.buildVersionLine(activity, appInstallInfo)
                    textVersion.text = versionLine
                    textVersion.isVisible = versionLine.isNotEmpty()

                    if (appInstallInfo.minSdk > 0 || appInstallInfo.targetSdk > 0) {
                        textSdk.text = activity.getString(
                            R.string.sdk_detail, appInstallInfo.minSdk, appInstallInfo.targetSdk
                        )
                        textSdk.isVisible = true
                    } else {
                        textSdk.isVisible = false
                    }

                    builder.setView(view)
                } else if (message.isNotEmpty()) {
                    builder.setMessage(message)
                }

                val dialog = builder.show()
                if (isDangerous) {
                    val errorColor = MaterialColors.getColor(
                        activity.window.decorView,
                        androidx.appcompat.R.attr.colorError
                    )
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(errorColor)
                }

                DialogController(
                    onDismiss = { if (dialog.isShowing) dialog.dismiss() },
                    onUpdateProgress = { step, progress ->
                        if (!dialog.isShowing) return@DialogController
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = false
                        progressLayout?.isVisible = true
                        progressText?.text = if (progress >= 0f) {
                            "$step (${(progress * 100).toInt()}%)"
                        } else {
                            step
                        }
                        if (progress >= 0f) {
                            progressBar?.isIndeterminate = false
                            progressBar?.setProgressCompat((progress * 100).toInt(), true)
                        } else {
                            progressBar?.isIndeterminate = true
                        }
                    }
                )
            }
        }
    }
}
