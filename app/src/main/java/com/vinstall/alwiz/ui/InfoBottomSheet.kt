package com.vinstall.alwiz.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vinstall.alwiz.databinding.BottomSheetInfoBinding

class InfoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetInfoBinding? = null
    private val binding get() = _binding!!

    var title: String = ""
    var subtitle: String? = null
    var body: String = ""
    var monospace: Boolean = false
    var primaryLabel: String? = null
    var onPrimary: (() -> Unit)? = null
    var secondaryLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (title.isEmpty()) dismissAllowingStateLoss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (title.isEmpty()) return

        binding.textTitle.text = title
        binding.textSubtitle.text = subtitle
        binding.textSubtitle.isVisible = !subtitle.isNullOrBlank()

        binding.textBody.text = body
        if (monospace) {
            binding.textBody.typeface = Typeface.MONOSPACE
            binding.textBody.textSize = 11f
        }

        binding.btnSecondary.text = secondaryLabel
        binding.btnSecondary.applyPressAnimation()
        binding.btnSecondary.setOnClickListener { dismiss() }

        val label = primaryLabel
        if (label != null) {
            binding.btnPrimary.isVisible = true
            binding.btnPrimary.text = label
            binding.btnPrimary.applyPressAnimation()
            binding.btnPrimary.setOnClickListener {
                onPrimary?.invoke()
                dismiss()
            }
        }

        if (savedInstanceState == null) {
            listOf<View>(
                binding.textTitle,
                binding.textSubtitle,
                binding.cardBody,
                binding.btnPrimary,
                binding.btnSecondary
            ).staggerIn()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "InfoBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            body: String,
            secondaryLabel: String,
            subtitle: String? = null,
            monospace: Boolean = false,
            primaryLabel: String? = null,
            onPrimary: (() -> Unit)? = null,
        ): InfoBottomSheet {
            return InfoBottomSheet().apply {
                this.title = title
                this.subtitle = subtitle
                this.body = body
                this.monospace = monospace
                this.secondaryLabel = secondaryLabel
                this.primaryLabel = primaryLabel
                this.onPrimary = onPrimary
                show(fragmentManager, TAG)
            }
        }
    }
}
