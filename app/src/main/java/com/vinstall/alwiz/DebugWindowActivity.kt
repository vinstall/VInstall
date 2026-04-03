package com.vinstall.alwiz

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vinstall.alwiz.databinding.ActivityDebugBinding
import com.vinstall.alwiz.util.DebugLog
import kotlinx.coroutines.launch

class DebugWindowActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebugBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.debug_window)

        lifecycleScope.launch {
            DebugLog.entries.collect { entries ->
                val text = entries.joinToString("\n")
                binding.textDebugLog.text = text
                binding.scrollView.post {
                    binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN)
                }
            }
        }

        binding.btnClearLog.setOnClickListener {
            DebugLog.clear()
        }

        binding.btnCopyLog.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("VInstall Debug Log", DebugLog.getAll())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
