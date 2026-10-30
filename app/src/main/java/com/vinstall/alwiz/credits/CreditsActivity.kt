package com.vinstall.alwiz.credits

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.ActivityCreditsBinding
import com.vinstall.alwiz.ui.applyPressAnimation
import com.vinstall.alwiz.ui.applySystemBarInsets
import com.vinstall.alwiz.ui.staggerChildren

class CreditsActivity : AppCompatActivity() {

    private data class Library(
        val name: String,
        val author: String,
        val license: String,
        val licenseUrl: String,
        val projectUrl: String
    )

    private lateinit var binding: ActivityCreditsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityCreditsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.credits_title)

        binding.textVersion.text = getString(R.string.credits_app_version, appVersionName())

        binding.btnDeveloper.setOnClickListener { openUrl(URL_DEVELOPER) }
        binding.btnLicenseOsi.setOnClickListener { openUrl(OSI_GPL_3) }
        binding.btnLicenseText.setOnClickListener { openUrl(URL_LICENSE_TEXT) }
        binding.btnSource.setOnClickListener { openUrl(URL_SOURCE) }
        listOf(
            binding.btnDeveloper,
            binding.btnLicenseOsi,
            binding.btnLicenseText,
            binding.btnSource
        ).forEach { it.applyPressAnimation(0.97f) }

        populateLibraries()

        if (savedInstanceState == null) binding.creditsContent.staggerChildren()
    }

    private fun populateLibraries() {
        val inflater = LayoutInflater.from(this)
        libraries.forEach { lib ->
            val row = inflater.inflate(R.layout.item_credit, binding.libsContainer, false)
            row.findViewById<TextView>(R.id.text_name).text = lib.name
            row.findViewById<TextView>(R.id.text_meta).text = "${lib.author} · ${lib.license}"
            row.setOnClickListener { openUrl(lib.projectUrl) }
            row.findViewById<MaterialButton>(R.id.btn_license).setOnClickListener {
                openUrl(lib.licenseUrl)
            }
            binding.libsContainer.addView(row)
        }
    }

    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.credits_open_failed), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private companion object {
        const val URL_DEVELOPER = "https://github.com/lenzarchive"
        const val URL_SOURCE = "https://github.com/vinstall"
        const val URL_LICENSE_TEXT = "https://www.gnu.org/licenses/gpl-3.0.html"

        // OSI pages follow https://opensource.org/license/<spdx-id-lowercase>
        const val OSI_GPL_3 = "https://opensource.org/license/gpl-3-0"
        const val OSI_GPL_2 = "https://opensource.org/license/gpl-2-0"
        const val OSI_APACHE_2 = "https://opensource.org/license/apache-2-0"
        const val OSI_MIT = "https://opensource.org/license/mit"
        const val OSI_OFL_1_1 = "https://opensource.org/license/ofl-1-1"

        val libraries = listOf(
            Library("AndroidX (Jetpack)", "Google", "Apache License 2.0", OSI_APACHE_2,
                "https://developer.android.com/jetpack/androidx"),
            Library("Material Components for Android", "Google", "Apache License 2.0", OSI_APACHE_2,
                "https://github.com/material-components/material-components-android"),
            Library("Kotlin", "JetBrains", "Apache License 2.0", OSI_APACHE_2,
                "https://kotlinlang.org"),
            Library("kotlinx.coroutines", "JetBrains", "Apache License 2.0", OSI_APACHE_2,
                "https://github.com/Kotlin/kotlinx.coroutines"),
            Library("Gson", "Google", "Apache License 2.0", OSI_APACHE_2,
                "https://github.com/google/gson"),
            Library("Shizuku API", "RikkaApps", "MIT License", OSI_MIT,
                "https://github.com/RikkaApps/Shizuku-API"),
            Library("Android Desugar JDK Libs", "Google", "GPL-2.0 + Classpath Exception", OSI_GPL_2,
                "https://github.com/google/desugar_jdk_libs"),
            Library("Poppins (font)", "Indian Type Foundry", "SIL Open Font License 1.1", OSI_OFL_1_1,
                "https://github.com/itfoundry/poppins")
        )
    }
}
