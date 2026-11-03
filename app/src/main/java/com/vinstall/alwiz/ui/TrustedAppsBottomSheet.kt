package com.vinstall.alwiz.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.vinstall.alwiz.appmanager.AppIconLoader
import com.vinstall.alwiz.databinding.BottomSheetTrustedAppsBinding
import com.vinstall.alwiz.settings.TrustableApp
import com.vinstall.alwiz.settings.TrustedAppsAdapter

class TrustedAppsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTrustedAppsBinding? = null
    private val binding get() = _binding!!

    var title: String = ""
    var loader: (() -> List<TrustableApp>)? = null
    var trusted: Set<String> = emptySet()
    var onConfirm: ((List<String>) -> Unit)? = null

    private var adapter: TrustedAppsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (onConfirm == null) dismissAllowingStateLoss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetTrustedAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (onConfirm == null) return

        binding.textTitle.text = title

        val selected = trusted.toMutableSet()
        val iconLoader = AppIconLoader(requireContext(), viewLifecycleOwner.lifecycleScope)
        adapter = TrustedAppsAdapter(emptyList(), iconLoader, selected)
        binding.recyclerApps.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerApps.adapter = adapter

        var all: List<TrustableApp> = emptyList()
        fun refresh() {
            val showSystem = binding.checkSystem.isChecked
            adapter?.submit(if (showSystem) all else all.filter { !it.system || it.packageName in selected })
        }
        binding.checkSystem.setOnCheckedChangeListener { _, _ -> refresh() }
        binding.rowSystem.setOnClickListener { binding.checkSystem.toggle() }
        viewLifecycleOwner.lifecycleScope.launch {
            all = withContext(Dispatchers.IO) { loader?.invoke() ?: emptyList() }
            binding.progress.visibility = View.GONE
            binding.recyclerApps.visibility = View.VISIBLE
            binding.checkSystem.isEnabled = true
            binding.rowSystem.isEnabled = true
            refresh()
        }

        binding.btnPositive.applyPressAnimation()
        binding.btnNegative.applyPressAnimation()
        binding.btnNegative.setOnClickListener { dismiss() }
        binding.btnPositive.setOnClickListener {
            val picked = all.filter { it.packageName in selected }.map { it.packageName }
            dismiss()
            onConfirm?.invoke(picked)
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
        binding.recyclerApps.adapter = null
        adapter = null
        _binding = null
    }

    companion object {
        private const val TAG = "TrustedAppsBottomSheet"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            loader: () -> List<TrustableApp>,
            trusted: Set<String>,
            onConfirm: (List<String>) -> Unit,
        ): TrustedAppsBottomSheet {
            return TrustedAppsBottomSheet().apply {
                this.title = title
                this.loader = loader
                this.trusted = trusted
                this.onConfirm = onConfirm
                show(fragmentManager, TAG)
            }
        }
    }
}
