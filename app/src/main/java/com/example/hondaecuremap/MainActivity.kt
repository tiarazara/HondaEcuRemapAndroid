package com.example.hondaecuremap

import androidx.activity.ComponentActivity
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.HorizontalScrollView
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
    private var selectedCellIndex = 0
    private var blockSelectMode = false
    private var rangeEndIndex: Int? = null

    private val openBin = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) loadBin(uri) }
    private val openXdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) loadXdf(uri) }
    private val saveBin = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) writeBin(uri) }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildUi() }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 20, 18, 18); setBackgroundColor(0xFFF7F9FC.toInt()) }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(TextView(this).apply { text = "HONDA ECU REMAP"; textSize = 24f; setTextColor(0xFF12345A.toInt()); gravity = Gravity.CENTER_VERTICAL })
        body.addView(TextView(this).apply { text = "Scoopy K93-N01  •  Alpha 0.7 Read-Only Viewer"; textSize = 13f; setTextColor(0xFF64748B.toInt()); setPadding(0, 4, 0, 16) })
        body.addView(actionButton("1. BUKA FILE BIN", "Pilih file backup ECU") { openBin.launch(arrayOf("application/octet-stream", "application/*", "*/*")) })
        body.addView(actionButton("2. MUAT FILE XDF", "Pilih definisi map TunerPro") { openXdf.launch(arrayOf("application/xml", "text/xml", "*/*")) })
        /* Read-only viewer: export/edit controls intentionally omitted. */
        /* body.addView(actionButton("SIMPAN SALINAN BIN", "Ekspor file tanpa perubahan") { if (binBytes == null) toast("Buka BIN dulu") else saveBin.launch(binName.substringBeforeLast('.', binName) + "_copy.bin") }) */
        body.addView(section("STATUS PROYEK"))
        status = infoText("Belum ada file dibuka")
        binInfo = infoText("BIN: belum dimuat")
        xdfInfo = infoText("XDF: belum dimuat")
        body.addView(status); body.addView(binInfo); body.addView(xdfInfo)
        body.addView(section("DAFTAR MAP"))
        body.addView(infoText("Tampilan baca-saja. Tabel memakai sumbu RPM dan TPS dari breakpoint XDF; seluruh baris dapat digulir untuk pemeriksaan."))
        mapList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(mapList)
        body.addView(section("PRATINJAU MAP (NILAI TEKNIK XDF)"))
        mapPreview = infoText("Buka BIN dan XDF, lalu pilih salah satu map.")
        body.addView(mapPreview)
        mapGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(mapGrid)
        body.addView(section("KONEKSI USB OTG / FTDI"))
        body.addView(actionButton("PERIKSA PERANGKAT USB", "Deteksi FTDI melalui OTG") { scanUsb() })
        usbInfo = infoText("Belum diperiksa")
        body.addView(usbInfo)
        body.addView(section("NAVIGASI TABEL"))
        body.addView(infoText("Geser tabel ke atas/bawah untuk melihat seluruh breakpoint RPM, dan ke kanan/kiri untuk melihat seluruh breakpoint TPS. Alpha 0.7 ini hanya untuk membaca dan memeriksa data."))
        body.addView(section("KEAMANAN"))
        body.addView(infoText("Alpha 0.7 adalah penampil baca-saja. Aplikasi tidak mengubah BIN dan tidak mengirim perintah ke ECU. Komunikasi FTDI, checksum, dan flashing belum diimplementasikan atau divalidasi."))
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
        selectedCellIndex = selectedCellIndex.coerceIn(0, table.rows * table.cols - 1)
        rangeEndIndex = rangeEndIndex?.coerceIn(0, table.rows * table.cols - 1)
        val width = (table.bits / 8).coerceAtLeast(1)
        val xAxis = axisValues(table.xAxisLink, table.cols, bytes)
        val yAxis = axisValues(table.yAxisLink, table.rows, bytes)
        val unit = table.units.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
        val rangeText = rangeEndIndex?.let { end -> "\nBlok: baris ${minOf(selectedCellIndex / table.cols, end / table.cols) + 1}-${maxOf(selectedCellIndex / table.cols, end / table.cols) + 1}, kolom ${minOf(selectedCellIndex % table.cols, end % table.cols) + 1}-${maxOf(selectedCellIndex % table.cols, end % table.cols) + 1}" } ?: ""
        mapPreview.text = "${table.name}\nAlamat: 0x%X • ${table.rows} baris × ${table.cols} kolom • ${table.bits}-bit$unit\nKolom → TPS: ${if (xAxis != null) "breakpoint OK" else "belum ditautkan"}   Baris ↓ RPM: ${if (yAxis != null) "breakpoint OK" else "belum ditautkan"}\nSel awal: baris ${selectedCellIndex / table.cols + 1}, kolom ${selectedCellIndex % table.cols + 1}$rangeText • perubahan: ${if (dirty) "ada" else "belum ada"}.".format(addr)
        mapGrid.removeAllViews()
        /* Map editing and block-operation controls are intentionally absent in read-only mode. */
        val vertical = object : ScrollView(this) {
    override fun dispatchTouchEvent(
        event: android.view.MotionEvent
    ): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN,
            android.view.MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }

        val handled = super.dispatchTouchEvent(event)

        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return handled
    }
}.apply {
    isFillViewport = true
    isSmoothScrollingEnabled = true
}
        val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = true; isFillViewport = false }
        val grid = TableLayout(this).apply { isStretchAllColumns = false }
        val header = TableRow(this)
        header.addView(axisCell("RPM ↓ / TPS →", 84, true))
        for (col in 0 until table.cols) header.addView(axisCell(xAxis?.getOrNull(col)?.let { fmtAxis(it) } ?: "TPS ${col + 1}", 84, true))
        grid.addView(header)
        for (rowIndex in 0 until table.rows) {
            val row = TableRow(this)
            row.addView(axisCell(yAxis?.getOrNull(rowIndex)?.let { fmtAxis(it) } ?: "RPM ${rowIndex + 1}", 84, true))
            for (colIndex in 0 until table.cols) {
                val index = rowIndex * table.cols + colIndex
                val position = addr + index * width
                val raw = readRaw(bytes, position, width, table.lsbFirst)
                val engineering = scaleFromRaw(raw, table.equation)
                val shown = engineering?.let { String.format(java.util.Locale.US, "% .${table.decimals.coerceIn(0, 4)}f", it) } ?: "RAW $raw"
                val button = Button(this).apply {
                    text = shown
                    isAllCaps = false
                    textSize = 11f
                    minWidth = 0
                    setPadding(2, 4, 2, 4)
                    val end = rangeEndIndex
                    val inRange = if (end == null) false else { val r1 = selectedCellIndex / table.cols; val c1 = selectedCellIndex % table.cols; val r2 = end / table.cols; val c2 = end % table.cols; rowIndex in minOf(r1, r2)..maxOf(r1, r2) && colIndex in minOf(c1, c2)..maxOf(c1, c2) }
                    setBackgroundColor(if (inRange) 0xFF9BC7F3.toInt() else if (index == selectedCellIndex) 0xFFB7D7F5.toInt() else 0xFFE1E5EA.toInt())
                    layoutParams = TableRow.LayoutParams(dp(84), dp(54)).apply { setMargins(2, 2, 2, 2) }
                    isClickable = false
                    isLongClickable = false
                    isFocusable = false
                }
                row.addView(button)
            }
            grid.addView(row)
        }
        horizontal.addView(grid)
        vertical.addView(horizontal)
        mapGrid.addView(vertical, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(560)))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun axisCell(label: String, widthDp: Int, header: Boolean): TextView = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 11f; setTextColor(0xFF17324D.toInt())
        setBackgroundColor(if (header) 0xFFC9D9E8.toInt() else 0xFFE1E5EA.toInt())
        layoutParams = TableRow.LayoutParams(dp(widthDp), dp(48)).apply { setMargins(2, 2, 2, 2) }
    }

    private fun fmtAxis(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(java.util.Locale.US, "%.1f", value)

    private fun axisValues(linkId: String?, count: Int, bytes: ByteArray): List<Double>? {
        if (linkId.isNullOrBlank()) return null
        val linked = xdfTables.firstOrNull { it.uniqueId.equals(linkId, true) } ?: return null
        val address = linked.address ?: return null
        val width = (linked.bits / 8).coerceAtLeast(1)
        if (address < 0 || address + count * width > bytes.size) return null
        return (0 until count).map { i ->
            val raw = readRaw(bytes, address + i * width, width, linked.lsbFirst)
            scaleFromRaw(raw, linked.equation) ?: raw.toDouble()
        }
    }

    private fun editSelectedCell(table: XdfTable) {
        val bytes = binBytes ?: return
        val address = table.address ?: return
        val index = selectedCellIndex.coerceIn(0, table.rows * table.cols - 1)
        val width = (table.bits / 8).coerceAtLeast(1)
        val position = address + index * width
        val raw = readRaw(bytes, position, width, table.lsbFirst)
        editEngineeringCell(table, index, position, width, raw, scaleFromRaw(raw, table.equation))
    }

    private fun copySelectedBlock(table: XdfTable) {
        val bytes = binBytes ?: return
        val address = table.address ?: return
        val end = rangeEndIndex
        val r1 = selectedCellIndex / table.cols
        val c1 = selectedCellIndex % table.cols
        val r2 = end?.div(table.cols) ?: r1
        val c2 = end?.rem(table.cols) ?: c1
        val top = minOf(r1, r2); val bottom = maxOf(r1, r2)
        val left = minOf(c1, c2); val right = maxOf(c1, c2)
        val width = (table.bits / 8).coerceAtLeast(1)
        val text = (top..bottom).joinToString("\n") { row ->
            (left..right).joinToString("\t") { col ->
                val raw = readRaw(bytes, address + (row * table.cols + col) * width, width, table.lsbFirst)
                val eng = scaleFromRaw(raw, table.equation)
                eng?.let { String.format(java.util.Locale.US, "% .${table.decimals.coerceIn(0, 4)}f", it).trim() } ?: raw.toString()
            }
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("${table.name} map block", text))
        toast("Blok ${bottom - top + 1} × ${right - left + 1} disalin")
    }

    private fun pasteBlock(table: XdfTable) {
        val input = EditText(this).apply {
            hint = "Contoh: 12.0\t12.5\t13.0\n14.0\t14.5\t15.0"
            minLines = 5; gravity = Gravity.TOP or Gravity.START; textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        AlertDialog.Builder(this).setTitle("Tempel blok nilai • ${table.name}")
            .setMessage("Tempel angka dari spreadsheet. TAB = kolom, Enter = baris. Mulai dari sel terpilih (baris ${selectedCellIndex / table.cols + 1}, kolom ${selectedCellIndex % table.cols + 1}).")
            .setView(input).setNegativeButton("Batal", null)
            .setPositiveButton("Terapkan blok") { _, _ ->
                val text = input.text.toString().trim()
                val rows = text.lines().filter { it.isNotBlank() }.map { line -> line.trim().split(Regex("\\s*\\t\\s*|\\s{2,}")) }
                if (rows.isEmpty()) { toast("Blok kosong"); return@setPositiveButton }
                val bytes = binBytes ?: return@setPositiveButton
                val baseRow = selectedCellIndex / table.cols
                val baseCol = selectedCellIndex % table.cols
                val width = (table.bits / 8).coerceAtLeast(1)
                val maxRaw = if (width >= 4) 0xFFFFFFFFL else (1L shl (width * 8)) - 1L
                var changed = 0
                var problem: String? = null
                loop@ for ((r, cells) in rows.withIndex()) for ((c, cell) in cells.withIndex()) {
                    val targetRow = baseRow + r; val targetCol = baseCol + c
                    if (targetRow >= table.rows || targetCol >= table.cols) { problem = "Blok melewati batas tabel pada baris ${r + 1}, kolom ${c + 1}"; break@loop }
                    val value = cell.trim().replace(',', '.').toDoubleOrNull()
                    val raw = value?.let { rawFromEngineering(it, table.equation) }
                    if (raw == null || raw < 0 || raw > maxRaw) { problem = "Nilai tidak valid: $cell"; break@loop }
                    writeRaw(bytes, table.address!! + (targetRow * table.cols + targetCol) * width, width, table.lsbFirst, raw)
                    changed++
                }
                if (problem != null) { toast("Tidak semua blok diterapkan: $problem. Periksa nilai dan batas tabel.") }
                else { dirty = true; selectedCellIndex = baseRow * table.cols + baseCol; showMap(table); status.text = "Ada perubahan BIN yang belum disimpan."; toast("$changed sel diperbarui") }
            }.show()
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
            .setTitle("Edit ${table.name} • baris ${index / table.cols + 1}, kolom ${index % table.cols + 1}")
            .setMessage("Rumus XDF: ${table.equation}. Data mentah: $raw")
            .setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Terapkan") { _, _ ->
                val value = input.text.toString().trim().toDoubleOrNull()
                val encoded = value?.let { rawFromEngineering(it, table.equation) }
                val maxRaw = if (width >= 4) 0xFFFFFFFFL else (1L shl (width * 8)) - 1L
                if (encoded == null || encoded < 0 || encoded > maxRaw) toast("Nilai tidak valid atau rumus XDF belum didukung")
                else { binBytes?.let { writeRaw(it, position, width, table.lsbFirst, encoded) }; dirty = true; selectedCellIndex = index; showMap(table); status.text = "Ada perubahan BIN yang belum disimpan."; toast("Nilai teknik diperbarui") }
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
    val lsbFirst: Boolean = false,
    val uniqueId: String = "",
    val xAxisLink: String? = null,
    val yAxisLink: String? = null
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
        var uniqueId = ""
        var address: Int? = null
        var rows = 1
        var cols = 1
        var bits = 8
        var axis = ""
        var equation = "X"
        var decimals = 0
        var units = ""
        var lsbFirst = false
        var xLink: String? = null
        var yLink: String? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "DEFAULTS" -> lsbFirst = parser.getAttributeValue(null, "lsbfirst") == "1"
                    "XDFTABLE" -> {
                        inTable = true; title = "Untitled"; uniqueId = parser.getAttributeValue(null, "uniqueid") ?: ""
                        address = null; rows = 1; cols = 1; bits = 8; equation = "X"; decimals = 0; units = ""; xLink = null; yLink = null
                    }
                    "title" -> if (inTable) title = parser.nextText().trim()
                    "XDFAXIS" -> if (inTable) axis = parser.getAttributeValue(null, "id") ?: ""
                    "embedinfo" -> if (inTable) {
                        val link = parser.getAttributeValue(null, "linkobjid")
                        if (axis == "x") xLink = link
                        if (axis == "y") yLink = link
                    }
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
                    "XDFTABLE" -> if (inTable) { out.add(XdfTable(title, address, rows.coerceAtLeast(1), cols.coerceAtLeast(1), bits.coerceAtLeast(1), equation, decimals, units, lsbFirst, uniqueId, xLink, yLink)); inTable = false }
                    "XDFAXIS" -> axis = ""
                }
            }
            parser.next()
        }
        return out
    }
}
