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
        body.addView(TextView(this).apply { text = "Scoopy K93-N01  •  Alpha 0.2"; textSize = 13f; setTextColor(0xFF64748B.toInt()); setPadding(0, 4, 0, 16) })
        body.addView(actionButton("1. BUKA FILE BIN", "Pilih file backup ECU") { openBin.launch(arrayOf("application/octet-stream", "application/*", "*/*")) })
        body.addView(actionButton("2. MUAT FILE XDF", "Pilih definisi map TunerPro") { openXdf.launch(arrayOf("application/xml", "text/xml", "*/*")) })
        body.addView(actionButton("SIMPAN SALINAN BIN", "Ekspor file tanpa perubahan") { if (binBytes == null) toast("Buka BIN dulu") else saveBin.launch(binName.substringBeforeLast('.', binName) + "_copy.bin") })
        body.addView(section("STATUS PROYEK"))
        status = infoText("Belum ada file dibuka")
        binInfo = infoText("BIN: belum dimuat")
        xdfInfo = infoText("XDF: belum dimuat")
        body.addView(status); body.addView(binInfo); body.addView(xdfInfo)
        body.addView(section("DAFTAR MAP"))
        body.addView(infoText("Pilih nama map untuk melihat data mentah dari BIN. Nilai belum dikonversi dengan rumus XDF dan belum bisa diedit pada versi ini."))
        mapList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(mapList)
        body.addView(section("PREVIEW DATA MAP (HEX)"))
        mapPreview = infoText("Buka BIN dan XDF, lalu pilih salah satu map.")
        body.addView(mapPreview)
        mapGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(mapGrid)
        body.addView(section("KONEKSI USB OTG / FTDI"))
        body.addView(actionButton("PERIKSA PERANGKAT USB", "Deteksi FTDI melalui OTG") { scanUsb() })
        usbInfo = infoText("Belum diperiksa")
        body.addView(usbInfo)
        body.addView(section("CARA EDIT"))
        body.addView(infoText("Pilih map, lalu sentuh sel HEX untuk mengubah byte mentah. Ukuran sel mengikuti definisi XDF dan urutan byte file dipertahankan. Nilai teknik/engineering belum dikonversi otomatis; cocokkan hasil dengan TunerPro sebelum digunakan."))
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
            val label = "${index + 1}. ${table.name}\n${table.rows} × ${table.cols}  •  ${table.bits}-bit  •  ${table.address?.let { "0x%X".format(it) } ?: "tanpa alamat"}  •  ${if (valid) "BIN OK" else "perlu cek BIN"}"
            mapList.addView(Button(this).apply { text = label; isAllCaps = false; textSize = 13f; gravity = Gravity.START or Gravity.CENTER_VERTICAL; setOnClickListener { showMap(table) } })
        }
    }

    private fun showMap(table: XdfTable) {
        val bytes = binBytes
        val addr = table.address
        if (bytes == null || addr == null) { mapPreview.text = "${table.name}: BIN atau alamat belum tersedia."; return }
        if (table.endExclusive > bytes.size) { mapPreview.text = "${table.name}: alamat di luar ukuran BIN. Preview dibatalkan."; return }
        selectedTable = table
        val width = (table.bits / 8).coerceAtLeast(1)
        val count = table.rows * table.cols
        mapPreview.text = "${table.name}\nAlamat: 0x%X • ${table.rows} × ${table.cols} • ${table.bits}-bit\nSentuh sel untuk edit HEX mentah. Perubahan: ${if (dirty) "ada" else "belum ada"}.".format(addr)
        mapGrid.removeAllViews()
        val mobileColumns = minOf(table.cols, 5)
        for (start in 0 until count step mobileColumns) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in start until minOf(start + mobileColumns, count)) {
                val p = addr + i * width
                val raw = (0 until width).joinToString(" ") { j -> "%02X".format(bytes[p + j].toInt() and 0xFF) }
                row.addView(Button(this).apply {
                    text = "${i + 1}\n$raw"
                    isAllCaps = false
                    textSize = 11f
                    minWidth = 0
                    setPadding(3, 5, 3, 5)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2, 2, 2, 2) }
                    setOnClickListener { editRawCell(table, i, p, width) }
                })
            }
            mapGrid.addView(row)
        }
    }

    private fun editRawCell(table: XdfTable, index: Int, position: Int, width: Int) {
        val bytes = binBytes ?: return
        val current = (0 until width).joinToString(" ") { j -> "%02X".format(bytes[position + j].toInt() and 0xFF) }
        val input = EditText(this).apply {
            setText(current)
            hint = if (width == 1) "00 sampai FF" else "contoh: 12 34"
            textSize = 18f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit ${table.name} • sel ${index + 1}")
            .setMessage("Masukkan ${width} byte HEX. Urutan byte disimpan persis seperti yang dimasukkan.")
            .setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Terapkan") { _, _ ->
                val clean = input.text.toString().replace(" ", "").replace("0x", "", true)
                if (clean.length != width * 2 || !clean.matches(Regex("[0-9a-fA-F]+"))) {
                    toast("Format harus tepat ${width * 2} digit HEX")
                } else {
                    for (j in 0 until width) bytes[position + j] = clean.substring(j * 2, j * 2 + 2).toInt(16).toByte()
                    dirty = true
                    showMap(table)
                    status.text = "Ada perubahan BIN yang belum disimpan."
                    toast("Nilai HEX diperbarui di memori")
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

data class XdfTable(val name: String, val address: Int?, val rows: Int, val cols: Int, val bits: Int) {
    val endExclusive: Int get() = (address ?: 0) + rows * cols * ((bits + 7) / 8)
}

object XdfReader {
    fun parse(xml: ByteArray): List<XdfTable> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val out = mutableListOf<XdfTable>()
        var inTable = false; var title = "Untitled"; var address: Int? = null
        var rows = 1; var cols = 1; var bits = 8; var axis = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "XDFTABLE" -> { inTable = true; title = "Untitled"; address = null; rows = 1; cols = 1; bits = 8 }
                    "title" -> if (inTable) title = parser.nextText().trim()
                    "XDFAXIS" -> if (inTable) axis = parser.getAttributeValue(null, "id") ?: ""
                    "EMBEDDEDDATA" -> if (inTable && axis == "z") {
                        parser.getAttributeValue(null, "mmedaddress")?.let { address = it.removePrefix("0x").removePrefix("0X").toLong(16).toInt() }
                        bits = parser.getAttributeValue(null, "mmedelementsizebits")?.toIntOrNull() ?: bits
                        rows = parser.getAttributeValue(null, "mmedrowcount")?.toIntOrNull() ?: rows
                        cols = parser.getAttributeValue(null, "mmedcolcount")?.toIntOrNull() ?: cols
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "XDFTABLE" -> if (inTable) { out.add(XdfTable(title, address, rows.coerceAtLeast(1), cols.coerceAtLeast(1), bits.coerceAtLeast(1))); inTable = false }
                    "XDFAXIS" -> axis = ""
                }
            }
            parser.next()
        }
        return out
    }
}
