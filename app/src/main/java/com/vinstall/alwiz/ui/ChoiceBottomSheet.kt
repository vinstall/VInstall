package com.vinstall.alwiz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vinstall.alwiz.databinding.BottomSheetChoiceBinding
import com.vinstall.alwiz.databinding.ItemChoiceBinding

class ChoiceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetChoiceBinding? = null
    private val binding get() = _binding!!

    var title: String = ""
    var items: List<String> = emptyList()
    var selectedIndex: Int = 0
    var cancelLabel: String = ""
    var onSelected: ((Int) -> Unit)? = null

    private var rows: List<ItemChoiceBinding> = emptyList()
    private var picked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (onSelected == null) dismissAllowingStateLoss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetChoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (onSelected == null) return

        binding.textTitle.text = title
        binding.btnCancel.text = cancelLabel
        binding.btnCancel.applyPressAnimation()
        binding.btnCancel.setOnClickListener { dismiss() }

        rows = items.mapIndexed { index, label ->
            val row = ItemChoiceBinding.inflate(layoutInflater, binding.layoutOptions, false)
            row.textLabel.text = label
            row.radio.isChecked = index == selectedIndex
            row.root.applyPressAnimation(0.98f)
            row.root.setOnClickListener { pick(index) }
            binding.layoutOptions.addView(row.root)
            row
        }

        if (savedInstanceState == null) {
            val animated = ArrayList<View>()
            animated.add(binding.textTitle)
            rows.forEach { animated.add(it.root) }
            animated.add(binding.btnCancel)
            animated.staggerIn()
        }
    }

    private fun pick(index: Int) {
        if (picked) return
        picked = true
        rows.forEachIndexed { i, row -> row.radio.isChecked = i == index }
        rows.getOrNull(index)?.radio?.popIn(0.6f)
        binding.root.postDelayed({
            if (!isAdded) return@postDelayed
            dismiss()
            onSelected?.invoke(index)
        }, 180L)
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
        rows = emptyList()
        _binding = null
    }

    companion object {
        private const val TAG = "ChoiceBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            items: List<String>,
            selectedIndex: Int,
            cancelLabel: String,
            onSelected: (Int) -> Unit,
        ): ChoiceBottomSheet {
            return ChoiceBottomSheet().apply {
                this.title = title
                this.items = items
                this.selectedIndex = selectedIndex
                this.cancelLabel = cancelLabel
                this.onSelected = onSelected
                show(fragmentManager, TAG)
            }
        }
    }
}
