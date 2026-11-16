package com.example.hondaecuremap

import androidx.activity.ComponentActivity
import android.content.Context
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var binInfo: TextView
    private lateinit var xdfInfo: TextView
    private lateinit var mapList: LinearLayout
    private lateinit var mapPreview: TextView
    private lateinit var mapGrid: LinearLayout
    private var selectedTable: XdfTable? = null
    private var dirty = false
    private lateinit var usbInfo: TextView
    private var binBytes: ByteArray? = null
    private var xdfTables: List<XdfTable> = emptyList()
    private var binName = "Scoopy_K93N01.bin"

    private val openBin = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) loadBin(uri) }
    private val openXdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) loadXdf(uri) }
    private val saveBin = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) writeBin(uri) }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi() }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 20, 18, 18); setBackgroundColor(0xFFF7F9FC.toInt()) }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(TextView(this).apply { text = "HONDA ECU REMAP"; textSize = 24f; setTextColor(0xFF12345A.toInt()); gravity = Gravity.CENTER_VERTICAL })
        body.addView(TextView(this).apply { text = "Scoopy K93-N01  •  Alpha 0.5"; textSize = 13f; setTextColor(0xFF64748B.toInt()); setPadding(0, 4, 0, 16) })
        body.addView(actionButton("1. BUKA FILE BIN", "Pilih file backup ECU") { openBin.launch(arrayOf("application/octet-stream", "application/*", "*/*")) })
        body.addView(actionButton("2. MUAT FILE XDF", "Pilih definisi map TunerPro") { openXdf.launch(arrayOf("application/xml", "text/xml", "*/*")) })
        body.addView(actionButton("SIMPAN SALINAN BIN", "Ekspor file tanpa perubahan") { if (binBytes == null) toast("Buka BIN dulu") else saveBin.launch(binName.substringBeforeLast('.', binName) + "_copy.bin") })
        body.addView(section("STATUS PROYEK"))
        status = infoText("Belum ada file dibuka")
        binInfo = infoText("BIN: belum dimuat")
        xdfInfo = infoText("XDF: belum dimuat")
        body.addView(status); body.addView(binInfo); body.addView(xdfInfo)
        body.addView(section("DAFTAR MAP"))
        body.addView(infoText("Nilai map ditampilkan memakai rumus MATH dari XDF. Sumbu RPM/TPS akan ditambahkan bertahap; verifikasi hasil dengan TunerPro sebelum memakai file hasil edit."))
        mapList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(mapList)
        body.addView(section("EDITOR MAP (NILAI TEKNIK XDF)"))
        mapPreview = infoText("Buka BIN dan XDF, lalu pilih salah satu map.")
        body.addView(mapPreview)
        mapGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(mapGrid)
        body.addView(section("KONEKSI USB OTG / FTDI"))
        body.addView(actionButton("PERIKSA PERANGKAT USB", "Deteksi FTDI melalui OTG") { scanUsb() })
        usbInfo = infoText("Belum diperiksa")
        body.addView(usbInfo)
        body.addView(section("CARA EDIT"))
        body.addView(infoText("Pilih map, lalu sentuh sel untuk mengedit nilai teknik. Konversi saat ini mendukung rumus linear sederhana yang ada di XDF Scoopy ini. Data 16-bit dibaca mengikuti DEFAULTS lsbfirst pada XDF. Cocokkan beberapa nilai dengan TunerPro sebelum digunakan."))
        body.addView(section("KEAMANAN"))
        body.addView(infoText("Aplikasi ini mengedit salinan BIN di memori dan tidak mengirim perintah flash ke ECU. Simpan sebagai file baru, bandingkan dengan BIN asli, dan lakukan verifikasi checksum/protokol secara terpisah sebelum flashing."))
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun section(s: String) = TextView(this).apply { text = s; textSize = 14f; setTextColor(0xFF1565C0.toInt()); setPadding(2, 20, 2, 8) }
    private fun infoText(s: String) = TextView(this).apply { text = s; textSize = 13f; setTextColor(0xFF263445.toInt()); setPadding(12, 12, 12, 12); setBackgroundColor(0xFFEAF0F6.toInt()) }
    private fun actionButton(label: String, hint: String, action: () -> Unit) = Button(this).apply { text = "$label\n$hint"; isAllCaps = false; textSize = 14f; setOnClickListener { action() } }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun readAll(uri: Uri): ByteArray = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun loadBin(uri: Uri) {
        try {
            val bytes = readAll(uri); binBytes = bytes
            binName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.contains('.') } ?: "Scoopy_K93N01.bin"
            binInfo.text = "BIN: ${bytes.size} byte (${bytes.size / 1024} KB)\nSHA-256: ${sha256(bytes)}\nPreview awal: " + bytes.take(24).joinToString(" ") { "%02X".format(it) }
            dirty = false
            status.text = "BIN berhasil dimuat. File sumber tidak diubah."
            refreshMapButtons(); toast("BIN berhasil dibuka")
        } catch (e: Exception) { toast("Gagal membuka BIN: ${e.message}") }
    }

    private fun loadXdf(uri: Uri) {
        try {
            xdfTables = XdfReader.parse(readAll(uri))
            val valid = xdfTables.count { it.address != null && binBytes?.let { b -> it.endExclusive <= b.size } == true }
            xdfInfo.text = "XDF: ${xdfTables.size} tabel ditemukan\n$valid alamat berada dalam batas file BIN yang sedang dibuka."
            status.text = "XDF terbaca. Pilih map untuk melihat data mentah."
            refreshMapButtons(); toast("XDF berhasil dibaca")
        } catch (e: Exception) { toast("Gagal membaca XDF: ${e.message}") }
    }

    private fun refreshMapButtons() {
        mapList.removeAllViews()
        if (xdfTables.isEmpty()) { mapList.addView(infoText("XDF belum dimuat")); return }
        xdfTables.forEachIndexed { index, table ->
            val bytes = binBytes
            val valid = bytes != null && table.address != null && table.endExclusive <= bytes.size
            val label = "${index + 1}. ${table.name}\n${table.rows} × ${table.cols}  •  ${table.bits}-bit  •  ${table.address?.let { "0x%X".format(it) } ?: "tanpa alamat"}  •  ${if (valid) "BIN OK" else "perlu cek BIN"}\nRumus: ${table.equation}"
            mapList.addView(Button(this).apply { text = label; isAllCaps = false; textSize = 13f; gravity = Gravity.START or Gravity.CENTER_VERTICAL; setOnClickListener { showMap(table) } })
        }
    }

    private fun readRaw(bytes: ByteArray, position: Int, width: Int, lsbFirst: Boolean): Long {
        var value = 0L
        if (lsbFirst) {
            for (j in width - 1 downTo 0) value = (value shl 8) or (bytes[position + j].toLong() and 0xFF)
        } else {
            for (j in 0 until width) value = (value shl 8) or (bytes[position + j].toLong() and 0xFF)
        }
        return value
    }

    private fun writeRaw(bytes: ByteArray, position: Int, width: Int, lsbFirst: Boolean, value: Long) {
        for (j in 0 until width) {
            val shift = if (lsbFirst) j * 8 else (width - 1 - j) * 8
            bytes[position + j] = ((value ushr shift) and 0xFF).toByte()
        }
    }

    private fun scaleFromRaw(raw: Long, equation: String): Double? {
        val e = equation.replace(" ", "")
        if (e == "X" || e == "x" || e.isBlank()) return raw.toDouble()
        val match = Regex("^[Xx](?:/([0-9]+(?:\\.[0-9]+)?))?(?:\\*([0-9]+(?:\\.[0-9]+)?))?$").matchEntire(e) ?: return null
        val divisor = match.groupValues[1].takeIf { it.isNotEmpty() }?.toDouble() ?: 1.0
        val multiplier = match.groupValues[2].takeIf { it.isNotEmpty() }?.toDouble() ?: 1.0
        if (divisor == 0.0) return null
        return raw.toDouble() / divisor * multiplier
    }

    private fun rawFromEngineering(value: Double, equation: String): Long? {
        val e = equation.replace(" ", "")
        if (e == "X" || e == "x" || e.isBlank()) return value.toLong()
        val match = Regex("^[Xx](?:/([0-9]+(?:\\.[0-9]+)?))?(?:\\*([0-9]+(?:\\.[0-9]+)?))?$").matchEntire(e) ?: return null
        val divisor = match.groupValues[1].takeIf { it.isNotEmpty() }?.toDouble() ?: 1.0
        val multiplier = match.groupValues[2].takeIf { it.isNotEmpty() }?.toDouble() ?: 1.0
        if (multiplier == 0.0) return null
        val raw = value * divisor / multiplier
        if (!raw.isFinite() || raw < 0.0 || raw > 4294967295.0) return null
        return kotlin.math.round(raw).toLong()
    }

    private fun showMap(table: XdfTable) {
        val bytes = binBytes
        val addr = table.address
        if (bytes == null || addr == null) { mapPreview.text = "${table.name}: BIN atau alamat belum tersedia."; return }
        if (table.endExclusive > bytes.size) { mapPreview.text = "${table.name}: alamat di luar ukuran BIN. Preview dibatalkan."; return }
        selectedTable = table
        val width = (table.bits / 8).coerceAtLeast(1)
        val count = table.rows * table.cols
        val unit = table.units.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
        mapPreview.text = "${table.name}\nAlamat: 0x%X • ${table.rows} × ${table.cols} • ${table.bits}-bit$unit\nRumus XDF: ${table.equation}\nSentuh sel untuk edit nilai teknik. Perubahan: ${if (dirty) "ada" else "belum ada"}.".format(addr)
        mapGrid.removeAllViews()
        val mobileColumns = minOf(table.cols, 5)
        for (start in 0 until count step mobileColumns) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in start until minOf(start + mobileColumns, count)) {
                val p = addr + i * width
                val raw = readRaw(bytes, p, width, table.lsbFirst)
                val engineering = scaleFromRaw(raw, table.equation)
                val shown = engineering?.let { String.format(java.util.Locale.US, "% .${table.decimals.coerceIn(0, 4)}f", it) } ?: "RAW $raw"
                row.addView(Button(this).apply {
                    text = "${i + 1}\n$shown"
                    isAllCaps = false
                    textSize = 11f
                    minWidth = 0
                    setPadding(3, 5, 3, 5)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2, 2, 2, 2) }
                    setOnClickListener { editEngineeringCell(table, i, p, width, raw, engineering) }
                })
            }
            mapGrid.addView(row)
        }
    }

    private fun editEngineeringCell(table: XdfTable, index: Int, position: Int, width: Int, raw: Long, engineering: Double?) {
        val input = EditText(this).apply {
            setText(engineering?.toString() ?: "")
            hint = "Masukkan nilai teknik"
            textSize = 18f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit ${table.name} • sel ${index + 1}")
            .setMessage("Nilai tersimpan memakai rumus XDF: ${table.equation}. Data mentah saat ini: $raw")
            .setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Terapkan") { _, _ ->
                val value = input.text.toString().trim().toDoubleOrNull()
                val encoded = value?.let { rawFromEngineering(it, table.equation) }
                val maxRaw = if (width >= 4) 0xFFFFFFFFL else (1L shl (width * 8)) - 1L
                if (encoded == null || encoded < 0 || encoded > maxRaw) {
                    toast("Nilai tidak valid atau rumus XDF belum didukung")
                } else {
                    binBytes?.let { writeRaw(it, position, width, table.lsbFirst, encoded) }
                    dirty = true
                    showMap(table)
                    status.text = "Ada perubahan BIN yang belum disimpan."
                    toast("Nilai teknik diperbarui di memori")
                }
            }.show()
    }

    private fun writeBin(uri: Uri) {
        try { val bytes = binBytes ?: return; contentResolver.openOutputStream(uri, "w")!!.use { it.write(bytes) }; dirty = false; status.text = "BIN hasil edit tersimpan sebagai salinan. File asli tetap tidak berubah."; toast("Salinan BIN tersimpan. SHA-256: ${sha256(bytes).take(16)}…"); selectedTable?.let { showMap(it) } }
        catch (e: Exception) { toast("Gagal menyimpan: ${e.message}") }
    }

    private fun scanUsb() {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = manager.deviceList.values
        if (devices.isEmpty()) { usbInfo.text = "Belum ada perangkat USB. Aktifkan OTG dan periksa kabel."; return }
        usbInfo.text = devices.joinToString("\n\n") { d ->
            val ftdi = if (d.vendorId == 0x0403) " • FTDI terdeteksi" else ""
            "${d.deviceName}\nVID: 0x%04X  PID: 0x%04X  Interface: %d%s".format(d.vendorId, d.productId, d.interfaceCount, ftdi)
        } + "\n\nDeteksi perangkat saja; komunikasi serial belum dijalankan."
    }
}

data class XdfTable(
    val name: String,
    val address: Int?,
    val rows: Int,
    val cols: Int,
    val bits: Int,
    val equation: String = "X",
    val decimals: Int = 0,
    val units: String = "",
    val lsbFirst: Boolean = false
) {
    val endExclusive: Int get() = (address ?: 0) + rows * cols * ((bits + 7) / 8)
}

object XdfReader {
    fun parse(xml: ByteArray): List<XdfTable> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val out = mutableListOf<XdfTable>()
        var inTable = false
        var title = "Untitled"
        var address: Int? = null
        var rows = 1
        var cols = 1
        var bits = 8
        var axis = ""
        var equation = "X"
        var decimals = 0
        var units = ""
        var lsbFirst = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "DEFAULTS" -> lsbFirst = parser.getAttributeValue(null, "lsbfirst") == "1"
                    "XDFTABLE" -> { inTable = true; title = "Untitled"; address = null; rows = 1; cols = 1; bits = 8; equation = "X"; decimals = 0; units = "" }
                    "title" -> if (inTable) title = parser.nextText().trim()
                    "XDFAXIS" -> if (inTable) axis = parser.getAttributeValue(null, "id") ?: ""
                    "units" -> if (inTable && axis == "z") units = parser.nextText().trim()
                    "decimalpl" -> if (inTable && axis == "z") decimals = parser.nextText().trim().toIntOrNull() ?: 0
                    "MATH" -> if (inTable && axis == "z") equation = parser.getAttributeValue(null, "equation") ?: equation
                    "EMBEDDEDDATA" -> if (inTable && axis == "z") {
                        parser.getAttributeValue(null, "mmedaddress")?.let { address = it.removePrefix("0x").removePrefix("0X").toLong(16).toInt() }
                        bits = parser.getAttributeValue(null, "mmedelementsizebits")?.toIntOrNull() ?: bits
                        rows = parser.getAttributeValue(null, "mmedrowcount")?.toIntOrNull() ?: rows
                        cols = parser.getAttributeValue(null, "mmedcolcount")?.toIntOrNull() ?: cols
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "XDFTABLE" -> if (inTable) { out.add(XdfTable(title, address, rows.coerceAtLeast(1), cols.coerceAtLeast(1), bits.coerceAtLeast(1), equation, decimals, units, lsbFirst)); inTable = false }
                    "XDFAXIS" -> axis = ""
                }
            }
            parser.next()
        }
        return out
    }
}
