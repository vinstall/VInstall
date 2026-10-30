package com.vinstall.alwiz.ui

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.BottomSheetConfirmBinding

class ConfirmationBottomSheet : BottomSheetDialogFragment() {

    data class AppInstallInfo(
        val icon: Bitmap?,
        val appLabel: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val installedVersionName: String?,
        val installedVersionCode: Long?,
        val minSdk: Int = 0,
        val targetSdk: Int = 0
    )

    private var _binding: BottomSheetConfirmBinding? = null
    private val binding get() = _binding!!

    var title: String = ""
    var message: String = ""
    var positiveLabel: String = ""
    var negativeLabel: String = ""
    var isDangerous: Boolean = false
    var appInstallInfo: AppInstallInfo? = null
    var onConfirm: (() -> Unit)? = null
    var onDismissed: (() -> Unit)? = null
    private var confirmed = false
    private val keepOpenAfterConfirm get() = appInstallInfo != null && onDismissed != null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (onConfirm == null) dismissAllowingStateLoss()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (onConfirm == null) return

        binding.textTitle.text = title
        binding.btnPositive.text = positiveLabel
        binding.btnNegative.text = negativeLabel

        val info = appInstallInfo
        if (info != null) {
            binding.layoutAppInfo.isVisible = true
            binding.textMessage.isVisible = false

            if (info.icon != null) {
                binding.imageAppIcon.setImageBitmap(info.icon)
                binding.imageAppIcon.isVisible = true
            } else {
                binding.imageAppIcon.isVisible = false
            }

            binding.textAppLabel.text = info.appLabel.ifBlank { info.packageName }
            binding.textPackageName.text = info.packageName

            val versionLine = buildVersionLine(info)
            binding.textVersionInfo.text = versionLine
            binding.textVersionInfo.isVisible = versionLine.isNotEmpty()

            if (info.minSdk > 0 || info.targetSdk > 0) {
                binding.textSdkInfo.text = requireContext().getString(
                    R.string.sdk_detail, info.minSdk, info.targetSdk
                )
                binding.textSdkInfo.isVisible = true
            } else {
                binding.textSdkInfo.isVisible = false
            }

        } else {
            binding.layoutAppInfo.isVisible = false
            binding.textMessage.text = message
            binding.textMessage.isVisible = message.isNotEmpty()
        }

        val container = if (isDangerous) {
            MaterialColors.getColor(binding.btnPositive, androidx.appcompat.R.attr.colorError)
        } else {
            MaterialColors.getColor(binding.btnPositive, androidx.appcompat.R.attr.colorPrimary)
        }
        val onContainer = if (isDangerous) {
            MaterialColors.getColor(binding.btnPositive, com.google.android.material.R.attr.colorOnError)
        } else {
            MaterialColors.getColor(binding.btnPositive, com.google.android.material.R.attr.colorOnPrimary)
        }
        binding.btnPositive.backgroundTintList = ColorStateList.valueOf(container)
        binding.btnPositive.setTextColor(onContainer)

        binding.btnPositive.setOnClickListener {
            confirmed = true
            onConfirm?.invoke()
            if (!keepOpenAfterConfirm) dismiss()
        }

        binding.btnNegative.setOnClickListener { dismiss() }

        binding.btnPositive.applyPressAnimation()
        binding.btnNegative.applyPressAnimation()

        if (savedInstanceState == null) {
            listOf<View>(
                binding.textTitle,
                binding.layoutAppInfo,
                binding.textMessage,
                binding.btnPositive,
                binding.btnNegative
            ).staggerIn()
        }
    }

    private fun buildVersionLine(info: AppInstallInfo): String {
        return buildVersionLine(requireContext(), info)
    }

    fun setInstalling(step: String, progress: Float) {
        val b = _binding ?: return
        if (!b.layoutProgress.isVisible) {
            android.transition.TransitionManager.beginDelayedTransition(b.root)
        }
        b.btnPositive.isVisible = false
        b.btnNegative.isVisible = false
        b.layoutProgress.isVisible = true
        b.textProgressStatus.text = if (progress >= 0f) {
            "$step (${(progress * 100).toInt()}%)"
        } else {
            step
        }
        if (progress >= 0f) {
            b.progressInstall.isIndeterminate = false
            b.progressInstall.setProgressCompat((progress * 100).toInt(), true)
        } else {
            b.progressInstall.isIndeterminate = true
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (!confirmed) onDismissed?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ConfirmationBottomSheet"

        fun buildVersionLine(context: android.content.Context, info: AppInstallInfo): String {
            if (info.versionName.isEmpty()) return ""
            val newVer = "${info.versionName} (${info.versionCode})"
            val installedName = info.installedVersionName
            return when {
                installedName == null -> context.getString(R.string.version_new_install, newVer)
                info.versionCode > (info.installedVersionCode ?: 0L) -> {
                    val oldVer = "$installedName (${info.installedVersionCode})"
                    context.getString(R.string.version_upgrade, oldVer, newVer)
                }
                info.versionCode < (info.installedVersionCode ?: 0L) -> {
                    val oldVer = "$installedName (${info.installedVersionCode})"
                    context.getString(R.string.version_downgrade, oldVer, newVer)
                }
                else -> context.getString(R.string.version_reinstall, newVer)
            }
        }

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            message: String = "",
            positiveLabel: String,
            negativeLabel: String,
            isDangerous: Boolean = false,
            appInstallInfo: AppInstallInfo? = null,
            onDismissed: (() -> Unit)? = null,
            onConfirm: () -> Unit,
        ): ConfirmationBottomSheet {
            return ConfirmationBottomSheet().apply {
                this.title = title
                this.message = message
                this.positiveLabel = positiveLabel
                this.negativeLabel = negativeLabel
                this.isDangerous = isDangerous
                this.appInstallInfo = appInstallInfo
                this.onDismissed = onDismissed
                this.onConfirm = onConfirm
                show(fragmentManager, TAG)
            }
        }
    }
}
