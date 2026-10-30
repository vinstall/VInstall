package com.vinstall.alwiz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.BottomSheetExportBinding
import com.vinstall.alwiz.model.AppInfo

class ExportBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetExportBinding? = null
    private val binding get() = _binding!!

    var app: AppInfo? = null
    var onConfirm: ((String?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (app == null || onConfirm == null) dismissAllowingStateLoss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val info = app ?: return
        if (onConfirm == null) return

        binding.imageIcon.setImageDrawable(info.icon)
        binding.imageIcon.isVisible = info.icon != null
        binding.textAppLabel.text = info.label
        binding.textAppMeta.text = getString(R.string.export_info, info.packageName, info.versionName)

        binding.checkboxEncrypt.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                android.transition.TransitionManager.beginDelayedTransition(binding.cardOptions)
            }
            binding.layoutPassword.isVisible = checked
            if (checked) {
                binding.editPassword.requestFocus()
            } else {
                binding.editPassword.text?.clear()
                binding.layoutPassword.error = null
            }
        }

        binding.editPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        binding.btnPositive.applyPressAnimation()
        binding.btnNegative.applyPressAnimation()
        binding.btnPositive.setOnClickListener { submit() }
        binding.btnNegative.setOnClickListener { dismiss() }

        if (savedInstanceState == null) {
            listOf<View>(
                binding.textTitle,
                binding.layoutApp,
                binding.cardOptions,
                binding.btnPositive,
                binding.btnNegative
            ).staggerIn()
        }
    }

    private fun submit() {
        val callback = onConfirm ?: return
        if (!binding.checkboxEncrypt.isChecked) {
            dismiss()
            callback(null)
            return
        }
        val password = binding.editPassword.text?.toString().orEmpty()
        if (password.isBlank()) {
            binding.layoutPassword.error = getString(R.string.export_password_required)
            return
        }
        dismiss()
        callback(password)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ExportBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            app: AppInfo,
            onConfirm: (String?) -> Unit,
        ): ExportBottomSheet {
            return ExportBottomSheet().apply {
                this.app = app
                this.onConfirm = onConfirm
                show(fragmentManager, TAG)
            }
        }
    }
}
