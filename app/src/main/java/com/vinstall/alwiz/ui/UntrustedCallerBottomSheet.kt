package com.vinstall.alwiz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.vinstall.alwiz.R
import com.vinstall.alwiz.model.CallerInfo
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.BlockDuration

class UntrustedCallerBottomSheet : BottomSheetDialogFragment() {

    var caller: CallerInfo? = null
    var behavior: String = ""
    var onContinue: (() -> Unit)? = null
    var onBlock: ((BlockDuration) -> Unit)? = null
    var onDismissed: (() -> Unit)? = null
    private var handled = false
    private var countdown: ContinueCountdown? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (caller == null || onContinue == null) dismissAllowingStateLoss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_untrusted, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val info = caller ?: return
        if (onContinue == null) return

        bindCallerRow(view.findViewById(R.id.layout_caller), info)
        view.findViewById<TextView>(R.id.text_behavior).text = behavior

        val mainActions = view.findViewById<View>(R.id.layout_main_actions)
        val blockOptions = view.findViewById<View>(R.id.layout_block_options)
        val hint = view.findViewById<View>(R.id.text_hint)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnBlock = view.findViewById<MaterialButton>(R.id.btn_block)
        val btnContinue = view.findViewById<MaterialButton>(R.id.btn_continue)
        val btnBack = view.findViewById<MaterialButton>(R.id.btn_back)
        val durations = view.findViewById<LinearLayout>(R.id.container_durations)

        btnBlock.isVisible = info.packageName.isNotEmpty()

        BlockDuration.values().forEach { duration ->
            val button = MaterialButton(
                requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = getString(duration.labelRes)
                minHeight = (48 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * resources.displayMetrics.density).toInt() }
                setOnClickListener {
                    handled = true
                    onBlock?.invoke(duration)
                    dismissAllowingStateLoss()
                }
                applyPressAnimation()
            }
            durations.addView(button)
        }

        btnContinue.applyDangerStyle()
        val delay = AppSettings.getUntrustedContinueDelay(requireContext())
        if (delay > 0) {
            btnContinue.isEnabled = false
            countdown?.cancel()
            countdown = ContinueCountdown(
                seconds = delay,
                onTick = { btnContinue.text = getString(R.string.untrusted_continue_countdown, it) },
                onFinish = {
                    btnContinue.text = getString(R.string.untrusted_continue)
                    btnContinue.isEnabled = true
                }
            ).also { it.start() }
        }

        btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        btnContinue.setOnClickListener {
            handled = true
            onContinue?.invoke()
            dismissAllowingStateLoss()
        }
        btnBlock.setOnClickListener {
            beginDelayedTransitionIfAllowed(view as ViewGroup)
            mainActions.isVisible = false
            hint.isVisible = false
            blockOptions.isVisible = true
        }
        btnBack.setOnClickListener {
            beginDelayedTransitionIfAllowed(view as ViewGroup)
            blockOptions.isVisible = false
            mainActions.isVisible = true
            hint.isVisible = true
        }

        btnCancel.applyPressAnimation()
        btnBlock.applyPressAnimation()
        btnContinue.applyPressAnimation()
        btnBack.applyPressAnimation()
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
        countdown?.cancel()
        countdown = null
        super.onDestroyView()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (!handled) onDismissed?.invoke()
    }

    fun dismissQuietly() {
        handled = true
        if (isAdded) dismissAllowingStateLoss()
    }

    companion object {
        private const val TAG = "UntrustedCallerBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            caller: CallerInfo,
            behavior: String,
            onContinue: () -> Unit,
            onBlock: (BlockDuration) -> Unit,
            onDismissed: () -> Unit,
        ): UntrustedCallerBottomSheet {
            return UntrustedCallerBottomSheet().apply {
                this.caller = caller
                this.behavior = behavior
                this.onContinue = onContinue
                this.onBlock = onBlock
                this.onDismissed = onDismissed
                showSafely(fragmentManager, TAG)
            }
        }
    }
}
