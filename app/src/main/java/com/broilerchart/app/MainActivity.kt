package com.broilerchart.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged

class MainActivity : AppCompatActivity() {

    private lateinit var etCount: EditText
    private lateinit var etFcr: EditText
    private lateinit var etBag: EditText
    private lateinit var swDaily: SwitchCompat
    private lateinit var tvModeHint: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvBags: TextView
    private lateinit var tvPhases: TextView
    private lateinit var tvStatus: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var btnPreview: Button
    private lateinit var btnSave: Button
    private lateinit var btnShare: Button

    private var cachedSettings: Settings? = null
    private var cachedBitmap: Bitmap? = null
    private var pendingSave = false
    private val reqStorage = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etCount = findViewById(R.id.etCount)
        etFcr = findViewById(R.id.etFcr)
        etBag = findViewById(R.id.etBag)
        swDaily = findViewById(R.id.swDaily)
        tvModeHint = findViewById(R.id.tvModeHint)
        tvTotal = findViewById(R.id.tvTotal)
        tvBags = findViewById(R.id.tvBags)
        tvPhases = findViewById(R.id.tvPhases)
        tvStatus = findViewById(R.id.tvStatus)
        ivPreview = findViewById(R.id.ivPreview)
        btnPreview = findViewById(R.id.btnPreview)
        btnSave = findViewById(R.id.btnSave)
        btnShare = findViewById(R.id.btnShare)

        etCount.doAfterTextChanged { updateSummary() }
        etFcr.doAfterTextChanged { updateSummary() }
        etBag.doAfterTextChanged { updateSummary() }
        swDaily.setOnCheckedChangeListener { _, _ -> updateSummary() }

        btnPreview.setOnClickListener { ensureBitmap { } }
        btnSave.setOnClickListener { ensureBitmap { bmp -> trySave(bmp) } }
        btnShare.setOnClickListener { ensureBitmap { bmp -> doShare(bmp) } }

        updateSummary()
    }

    // ---------------------------------------------------------------- input
    private fun readSettings(): Settings? {
        val count = Fmt.ascii(etCount.text.toString()).trim().toIntOrNull()
        val fcr = Fmt.ascii(etFcr.text.toString()).trim().toDoubleOrNull()
        val bag = Fmt.ascii(etBag.text.toString()).trim().toIntOrNull()
        if (count == null || count < 1) return null
        if (fcr == null || fcr < 0.5 || fcr > 5.0) return null
        if (bag == null || bag < 1 || bag > 200) return null
        return Settings(count, fcr, swDaily.isChecked, bag)
    }

    private fun updateSummary() {
        tvModeHint.text = if (swDaily.isChecked)
            "১টি মুরগির দৈনিক ও মোট খাদ্যও FCR অনুযায়ী বদলাবে (ওজন একই থাকবে)।"
        else
            "শুধু মোট খাদ্য ও বস্তা বদলাবে; ১টি মুরগির টেবিল মূল চার্টের মতোই থাকবে।"

        val s = readSettings()
        if (s == null) {
            tvTotal.text = "—"
            tvBags.text = "মুরগির সংখ্যা, FCR (০.৫–৫) ও বস্তার ওজন সঠিকভাবে দিন"
            tvPhases.text = ""
            return
        }
        val c = Computed(s)
        tvTotal.text = "মোট খাদ্য: ${Fmt.dec(c.totalKg, 1)} কেজি"
        val p = c.bagParts(c.totalKg)
        tvBags.text = "= ${Fmt.int(p.first)} বস্তা ${Fmt.int(p.second)} কেজি  (${Fmt.dec(c.bagsOf(c.totalKg), 1)} বস্তা)"
        tvPhases.text = buildString {
            append("স্টার্টার (১–১৪): ${Fmt.dec(c.starterKg, 1)} কেজি = ${Fmt.dec(c.bagsOf(c.starterKg), 1)} বস্তা\n")
            append("গ্রোয়ার (১৫–২৫): ${Fmt.dec(c.growerKg, 1)} কেজি = ${Fmt.dec(c.bagsOf(c.growerKg), 1)} বস্তা\n")
            append("ফিনিশার (২৬–৩২+): ${Fmt.dec(c.finisherKg, 1)} কেজি = ${Fmt.dec(c.bagsOf(c.finisherKg), 1)} বস্তা\n")
            append("শেষ ওজন (সব মুরগি): ≈ ${Fmt.dec(c.flockWeightKg, 0)} কেজি")
        }
    }

    // ---------------------------------------------------------------- image
    private fun setBusy(busy: Boolean) {
        btnPreview.isEnabled = !busy
        btnSave.isEnabled = !busy
        btnShare.isEnabled = !busy
        if (busy) tvStatus.text = "ছবি তৈরি হচ্ছে… একটু অপেক্ষা করুন"
    }

    private fun ensureBitmap(onReady: (Bitmap) -> Unit) {
        val s = readSettings()
        if (s == null) {
            Toast.makeText(this, "মুরগির সংখ্যা, FCR ও বস্তার ওজন ঠিকভাবে দিন", Toast.LENGTH_SHORT).show()
            return
        }
        val cb = cachedBitmap
        if (cb != null && cachedSettings == s) {
            onReady(cb)
            return
        }
        setBusy(true)
        Thread {
            try {
                val bmp = InfographicRenderer(s).render()
                val preview = Bitmap.createScaledBitmap(bmp, 540, (bmp.height * 540f / bmp.width).toInt(), true)
                runOnUiThread {
                    cachedBitmap = bmp
                    cachedSettings = s
                    ivPreview.setImageBitmap(preview)
                    setBusy(false)
                    tvStatus.text = "ছবি তৈরি হয়েছে"
                    onReady(bmp)
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    setBusy(false)
                    tvStatus.text = "ছবি তৈরি করা যায়নি: ${e.message}"
                }
            }
        }.start()
    }

    // ---------------------------------------------------------------- save / share
    private fun trySave(bmp: Bitmap) {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = true
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), reqStorage)
            return
        }
        val s = cachedSettings ?: return
        tvStatus.text = "সেভ হচ্ছে…"
        Thread {
            val ok = try {
                ImageExport.saveToGallery(this, bmp, ImageExport.fileName(s))
            } catch (e: Throwable) {
                false
            }
            runOnUiThread {
                tvStatus.text = if (ok) "গ্যালারিতে সেভ হয়েছে (Pictures/BroilerChart)" else "সেভ করা যায়নি"
            }
        }.start()
    }

    private fun doShare(bmp: Bitmap) {
        tvStatus.text = "শেয়ারের জন্য প্রস্তুত হচ্ছে…"
        Thread {
            try {
                val uri = ImageExport.writeForShare(this, bmp)
                runOnUiThread {
                    tvStatus.text = ""
                    ImageExport.share(this, uri)
                }
            } catch (e: Throwable) {
                runOnUiThread { tvStatus.text = "শেয়ার করা যায়নি" }
            }
        }.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == reqStorage) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted && pendingSave) {
                pendingSave = false
                cachedBitmap?.let { trySave(it) }
            } else {
                pendingSave = false
                tvStatus.text = "সেভ করতে স্টোরেজ অনুমতি দরকার"
            }
        }
    }
}
