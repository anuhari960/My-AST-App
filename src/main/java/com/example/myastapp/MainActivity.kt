package com.example.myastapp

import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter

data class AstResult(
    var discNo: Int, val id: String, val organism: String,
    val drugFull: String, val clsiRef: String, val diameter: Int, var interpretation: String,
    val drugCode: String
)

class MainActivity : AppCompatActivity() {
    private lateinit var ivPetriDish: ImageView
    private lateinit var ivZoom: ImageView
    private lateinit var zoneOverlay: ZoneOverlayView
    private lateinit var seekBarSize: SeekBar
    private lateinit var seekBarZone: SeekBar
    private lateinit var rootScrollView: ScrollView
    private lateinit var tvDisplay: TextView
    private lateinit var calLayout: LinearLayout
    private lateinit var resLayout: LinearLayout
    private lateinit var tableHeader: TableLayout
    private lateinit var tableResults: TableLayout
    private lateinit var etAstId: EditText
    private lateinit var zoomFrame: FrameLayout
    private lateinit var spOrganism: Spinner
    private lateinit var spAntibiotic: Spinner
    private lateinit var btnCalibrate: Button
    private lateinit var btnView: Button
    private lateinit var btnEpidemView: Button

    private var pixelsPerMm: Float = 0f
    private val resultsList = mutableListOf<AstResult>()
    private val MASTER_FILE = "AST_Research_Master.csv"
    private var isResearchMode = false
    private var viewModeIndex = 0
    private var originalBitmap: Bitmap? = null
    private var selectedOrganism: String = ""
    private var selectedDrugFullName: String = ""
    private var selectedDrugCode: String = ""
    private var tempImageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { processSelectedImage(it) } }
    private val takeImage = registerForActivityResult(ActivityResultContracts.TakePicture()) { if (it) tempImageUri?.let { uri -> processSelectedImage(uri) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootScrollView = findViewById(R.id.rootScrollView)
        ivPetriDish = findViewById(R.id.ivPetriDish); ivZoom = findViewById(R.id.ivZoom)
        zoneOverlay = findViewById(R.id.zoneOverlay); seekBarSize = findViewById(R.id.seekBarSize)
        seekBarZone = findViewById(R.id.seekBarZone); tvDisplay = findViewById(R.id.tvConsolidatedDisplay)
        calLayout = findViewById(R.id.calibrationControls); resLayout = findViewById(R.id.researchControls)
        tableHeader = findViewById(R.id.tableHeader); tableResults = findViewById(R.id.tableResults)
        etAstId = findViewById(R.id.etAstId); zoomFrame = findViewById(R.id.zoomFrame)
        spOrganism = findViewById(R.id.spOrganism); spAntibiotic = findViewById(R.id.spAntibiotic)
        btnCalibrate = findViewById(R.id.btnCalibrate); btnView = findViewById(R.id.btnView)
        btnEpidemView = findViewById(R.id.btnEpidemView)
        zoomFrame.visibility = View.GONE

        setupSpinners()
        findViewById<Button>(R.id.btnLoadImage).setOnClickListener { showLoadOptions() }
        btnCalibrate.setOnClickListener { calibrate() }
        btnView.setOnClickListener { viewModeIndex = (viewModeIndex + 1) % 3; applyViewMode() }
        btnEpidemView.setOnClickListener { showEpidemDialog() }
        findViewById<Button>(R.id.btnUndoTop).setOnClickListener { returnToFirstPage() }
        findViewById<Button>(R.id.btnNextDisc).setOnClickListener { saveZone() }
        findViewById<Button>(R.id.btnAddToMaster).setOnClickListener { saveToCSV() }
        findViewById<Button>(R.id.btnShareMaster).setOnClickListener { shareCSV() }

        setupTouchHandlers()
        drawTableHeaders()
    }

    private fun setupTouchHandlers() {
        val touchFix = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> rootScrollView.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_MOVE -> {
                    rootScrollView.requestDisallowInterceptTouchEvent(true)
                    updateZoom(zoneOverlay.getCurrentRadius())
                }
                MotionEvent.ACTION_UP -> rootScrollView.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        zoneOverlay.setOnTouchListener(touchFix)
        seekBarSize.setOnTouchListener(touchFix)
        seekBarZone.setOnTouchListener(touchFix)

        zoneOverlay.changeListener = object : ZoneOverlayView.OnCircleChangedListener {
            override fun onCircleChanged(centerX: Float, centerY: Float, radius: Float) { updateZoom(radius) }
        }

        seekBarZone.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (pixelsPerMm > 0f) {
                    val mm = 6.0f + (p.toFloat() / 100.0f) * 44.0f
                    zoneOverlay.updateRadius((mm * pixelsPerMm) / 2.0f)
                    tvDisplay.text = String.format("Reading: %.1f mm", mm)
                    updateZoom(zoneOverlay.getCurrentRadius())
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        seekBarSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                zoneOverlay.updateRadius(10f + (p * 2.0f))
                updateZoom(zoneOverlay.getCurrentRadius())
            }
            override fun onStartTrackingTouch(s: SeekBar?) { zoneOverlay.showCircle() }; override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        findViewById<Button>(R.id.btnMinus).setOnClickListener { if (seekBarSize.progress > 0) seekBarSize.progress--; updateZoom(zoneOverlay.getCurrentRadius()) }
        findViewById<Button>(R.id.btnPlus).setOnClickListener { if (seekBarSize.progress < seekBarSize.max) seekBarSize.progress++; updateZoom(zoneOverlay.getCurrentRadius()) }
    }

    private fun updateZoom(radiusPx: Float) {
        try {
            ivZoom.setImageBitmap(null)
            zoomFrame.visibility = View.GONE
        } catch (e: Exception) {}
    }

    private fun calibrate() {
        val rPx = zoneOverlay.getCurrentRadius()
        if (rPx > 0) {
            pixelsPerMm = (rPx * 2) / 6.0f; isResearchMode = true
            calLayout.visibility = View.GONE; resLayout.visibility = View.VISIBLE
            btnCalibrate.visibility = View.GONE; btnView.visibility = View.VISIBLE; zoomFrame.visibility = View.GONE
            zoneOverlay.updateRadius((6.0f * pixelsPerMm) / 2.0f); seekBarZone.progress = 0; tvDisplay.text = "Reading: 6.0 mm"
        }
    }

    private fun getCurrentPatterns(): String {
        var patterns = ""
        val nonSusceptible = resultsList.filter { it.interpretation.contains("R") || it.interpretation.contains("I") }

        if (selectedOrganism.contains("Staph") && nonSusceptible.any { it.drugCode == "FOX" || it.drugCode == "CX" }) patterns += "MRSA "

        val cazResult = resultsList.find { it.drugCode == "CAZ" }
        val cacResult = resultsList.find { it.drugCode == "CAC" }
        if (cazResult != null && cacResult != null) {
            if (getESBLStatus(cazResult.diameter, cacResult.diameter) == "ESBL POSITIVE") patterns += "ESBL "
        }

        val imiResult = resultsList.find { it.drugCode == "IMI" || it.drugCode == "IMP" || it.drugCode == "MEM" || it.drugCode == "MERO" }
        val ieResult = resultsList.find { it.drugCode == "IE" }
        if (imiResult != null && ieResult != null) {
            if (getMBLStatus(imiResult.diameter, ieResult.diameter, selectedOrganism) == "MBL POSITIVE") patterns += "MBL "
        }

        val classes = mutableSetOf<String>()
        nonSusceptible.forEach {
            when (it.drugCode) {
                "P", "CX", "CZ", "CAZ", "CPM", "FOX", "SAM", "PIT", "ATM", "CD" -> classes.add("BL")
                "IMP", "MEM", "IMI", "MERO", "ETP" -> classes.add("CP")
                "GEN", "AK", "TOB", "NETIL", "GENTA" -> classes.add("AG")
                "CIP", "LE", "LEVO", "OFX", "OFLOX", "NOR" -> classes.add("QN")
                "TE", "DO", "TET", "DOXY", "DOX" -> classes.add("TC")
                "SXT", "COT" -> classes.add("FA")
            }
        }
        if (classes.size >= 3) patterns += "MDR "
        return if (patterns.isEmpty()) "Normal Profile" else patterns.trim()
    }

    private fun showEpidemDialog() { AlertDialog.Builder(this).setTitle("Epidemiological Results").setMessage("Detected Patterns:\n\n${getCurrentPatterns()}").setPositiveButton("OK", null).show() }

    private fun redrawTable() {
        tableResults.removeAllViews()
        resultsList.forEachIndexed { i, r ->
            val row = TableRow(this).apply { setBackgroundColor(if (i % 2 == 0) Color.WHITE else Color.parseColor("#F5F5F5"))
                setOnClickListener { resultsList.removeAt(i); redrawTable() } }
            listOf(r.discNo.toString(), r.drugFull, r.clsiRef, "${r.diameter}mm", r.interpretation).forEach { t ->
                row.addView(TextView(this).apply { text = t; setPadding(12, 12, 12, 12); setTextColor(if (t.contains("R (")) Color.RED else Color.BLACK); setTextSize(14f) })
            }; tableResults.addView(row)
        }
    }

    private fun saveZone() {
        if (pixelsPerMm <= 0f || selectedDrugCode.isEmpty() || selectedDrugFullName.contains("Select")) return

        if (resultsList.any { it.drugFull == selectedDrugFullName }) {
            Toast.makeText(this, "Already exists", Toast.LENGTH_SHORT).show()
            return
        }

        val mm = ((zoneOverlay.getCurrentRadius() * 2) / pixelsPerMm).toInt()
        val isAtcc = selectedOrganism.contains("ATCC")
        var ref = "N/A"; var interp = "Unknown"

        if (isAtcc) {
            val limits = selectedDrugFullName.substringAfterLast("(").substringBefore(")").split("-")
            if (limits.size == 2) {
                val low = limits[0].toInt(); val high = limits[1].toInt()
                ref = "$low-$high mm"; interp = if (mm in low..high) "Within Range" else "Out of Range"
            }
        } else {
            // NECESSARY CHANGE: Added CAC here so it is treated as an inhibitor disk
            if (selectedDrugCode == "IE" || selectedDrugCode == "CAC" || selectedDrugCode == "CEC") {
                ref = "Inhibitor"; interp = "Tested"
            } else if (selectedDrugCode == "VA" && selectedOrganism.contains("Staph")) {
                resultsList.add(AstResult(resultsList.size + 1, etAstId.text.toString(), selectedOrganism, selectedDrugFullName, "N/A", mm, "Do MIC", "VA"))
                redrawTable(); zoneOverlay.hideCircle(); return
            } else {
                val breakpoints = resources.getStringArray(if (selectedOrganism.contains("coli")) R.array.clsi_breakpoints_enterobacterales else R.array.clsi_breakpoints_staph)
                for (line in breakpoints) {
                    val parts = line.split(",")
                    if (parts[0] == selectedDrugCode) {
                        val s = parts[1].toInt(); val r = parts[2].toInt()
                        ref = "S≥$s, R≤$r"; interp = when { mm >= s -> "S"; mm <= r -> "R"; else -> "I" }; break
                    }
                }
            }
        }

        resultsList.add(AstResult(resultsList.size + 1, etAstId.text.toString(), selectedOrganism, selectedDrugFullName, ref, mm, interp, selectedDrugCode))
        updateInterpretationsWithPatterns()
        redrawTable()
        zoneOverlay.hideCircle()
    }

    private fun updateInterpretationsWithPatterns() {
        val patterns = getCurrentPatterns()
        resultsList.forEach { item ->
            val base = if (item.interpretation.contains(" ")) item.interpretation.split(" ")[0] else item.interpretation
            var tag = ""

            if (patterns.contains("MBL") && (item.drugCode == "IMP" || item.drugCode == "IMI" || item.drugCode == "MERO" || item.drugCode == "MEM" || item.drugCode == "IE")) {
                tag = "MBL"
            }
            if (patterns.contains("ESBL") && (item.drugCode == "CAZ" || item.drugCode == "CAC" || item.drugCode == "CTX" || item.drugCode == "CEC" || item.drugCode == "CRO")) {
                tag = if (tag.isEmpty()) "ESBL" else "$tag, ESBL"
            }
            if (patterns.contains("MRSA") && (item.drugCode == "FOX" || item.drugCode == "CX")) {
                tag = if (tag.isEmpty()) "MRSA" else "$tag, MRSA"
            }

            if (tag.isNotEmpty() && (item.interpretation.startsWith("R") || item.interpretation.startsWith("I") || item.interpretation == "Tested")) {
                item.interpretation = "$base ($tag)"
            } else {
                item.interpretation = base
            }
        }
    }

    private fun returnToFirstPage() { isResearchMode = false; calLayout.visibility = View.VISIBLE; resLayout.visibility = View.GONE; btnCalibrate.visibility = View.VISIBLE; btnView.visibility = View.GONE; zoomFrame.visibility = View.GONE; viewModeIndex = 0; applyViewMode() }
    private fun saveToCSV() { try { val file = File(getExternalFilesDir(null), MASTER_FILE); val writer = FileWriter(file, true); resultsList.forEach { writer.append("${it.discNo},${it.id},${it.organism},${it.drugFull},${it.clsiRef},${it.diameter},${it.interpretation}\n") }; writer.flush(); writer.close(); resultsList.clear(); redrawTable(); returnToFirstPage(); Toast.makeText(this, "Master Saved", Toast.LENGTH_SHORT).show() } catch (e: Exception) { } }
    private fun drawTableHeaders() { tableHeader.removeAllViews(); val row = TableRow(this).apply { setPadding(0, 4, 0, 4) }; listOf("No.", "Drug Name", "CLSI Ref", "Zone", "Result").forEach { t -> row.addView(TextView(this).apply { text = t; setPadding(12, 12, 12, 12); setTextColor(Color.WHITE); setTextSize(14f); setTypeface(null, Typeface.BOLD) }) }; tableHeader.addView(row) }
    private fun updateDrugSpinner(o: String) {
        val drugs = when (o) {
            "S. aureus ATCC 25923" -> arrayOf("Erythromycin (E) 15µg (22-30)", "Cefoxitin (CX) 30µg (23-29)", "Chloramphenicol (C) 30µg (19-26)", "Ciprofloxacin (CIP) 5µg (22-30)", "Doxycycline (DOX) 30µg (23-29)", "Levofloxacin (LEVO) 5µg (25-30)", "Linezolid (LZD) 30µg (24-30)", "Penicillin (P) 10U (26-37)", "Tetracycline (TET) 30µg (24-30)", "Co-Trimoxazole (SXT) 25µg (24-32)", "Vancomycin (VANCO) 30µg (17-21)", "Nitrofurantoin (NF) 300µg (18-22)", "Norfloxacin (NOR) 10µg (17-28)", "Clindamycin (CD) 2µg (24-30)", "Gentamicin (GENTA) 10µg (19-27)")
            "E. coli ATCC 25922" -> arrayOf("Cefepime (CPM) 30µg (31-37)", "Cefoxitin (CX) 30µg (23-29)", "Cefazolin (CZ) 30µg (26-34)", "Ceftazidime (CAZ) 30µg (25-32)", "Ciprofloxacin (CIP) 5µg (29-38)", "Doxycycline (DOXY) 30µg (18-24)", "Ertapenem (ETP) 10µg (29-36)", "Gentamicin (GENTA) 10µg (19-26)", "Imipenem (IMI) 10µg (26-32)", "Levofloxacin (LEVO) 5µg (29-37)", "Meropenem (MERO) 10µg (28-35)", "Netilmicin (NETIL) 30µg (22-30)", "Nitrofurantoin (NF) 300µg (20-25)", "Norfloxacin (NOR) 10µg (28-35)", "Ofloxacin (OFLOX) 5µg (29-33)", "Tetracycline (TET) 30µg (18-25)", "Tobramycin (TOB) 10µg (18-26)", "Co-Trimoxazole (SXT) 25µg (23-29)", "Ampicillin/Sulbactam (SAM) 10/10µg (19-24)", "Aztreonam (ATM) 30µg (28-36)", "Piperacillin/Tazobactam (PIT) 100/10µg (24-30)", "Amikacin (AK) 30µg (19-26)", "Fosfomycin (FOS) 200µg (22-30)")
            "Staph aureus" -> resources.getStringArray(R.array.staph_antibiotics)

            // CHANGE: Remove the hardcoded list that was here and use this line
            else -> resources.getStringArray(R.array.enterobacterales_antibiotics)
        }

        // EVERYTHING BELOW THIS LINE MUST BE CLEAN - NO DANGLING DRUG NAMES
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, drugs)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spAntibiotic.adapter = adapter

        spAntibiotic.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                selectedDrugFullName = drugs[pos]
                selectedDrugCode = if (selectedOrganism.contains("ATCC")) {
                    selectedDrugFullName.substringAfter("(").substringBefore(")")
                } else {
                    selectedDrugFullName.substringAfterLast("(", "").substringBefore(")", "")
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }
    private fun setupSpinners() { val organisms = arrayOf("E. coli ATCC 25922", "S. aureus ATCC 25923", "Staph aureus", "E. coli"); val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, organisms); adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spOrganism.adapter = adapter; spOrganism.onItemSelectedListener = object : AdapterView.OnItemSelectedListener { override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) { selectedOrganism = organisms[pos]; updateDrugSpinner(selectedOrganism) }; override fun onNothingSelected(p0: AdapterView<*>?) {} } }
    private fun showLoadOptions() { val options = arrayOf("New Plate (Clear Data)", "Continue with Existing Plate"); AlertDialog.Builder(this).setTitle("Load Options").setItems(options) { _, which -> if (which == 0) { resultsList.clear(); etAstId.setText(""); redrawTable() }; showImageSourceOptions() }.show() }
    private fun showImageSourceOptions() { val sources = arrayOf("Camera", "Gallery"); AlertDialog.Builder(this).setTitle("Select Source").setItems(sources) { _, which -> if (which == 0) launchCamera() else pickImage.launch("image/*") }.show() }
    private fun launchCamera() { val file = File(getExternalFilesDir(null), "temp_ast.jpg"); tempImageUri = FileProvider.getUriForFile(this, "$packageName.provider", file); takeImage.launch(tempImageUri) }
    private fun processSelectedImage(uri: Uri) { val stream = contentResolver.openInputStream(uri); originalBitmap = BitmapFactory.decodeStream(stream); ivPetriDish.setImageBitmap(originalBitmap); pixelsPerMm = 0f; zoneOverlay.showCircle(); updateZoom(zoneOverlay.getCurrentRadius()) }
    private fun applyViewMode() { val bmp = originalBitmap ?: return; val processed = when (viewModeIndex) { 1 -> toGrayscale(bmp); 2 -> toHighContrast(bmp); else -> bmp }; ivPetriDish.setImageBitmap(processed) }
    private fun toGrayscale(src: Bitmap): Bitmap { val dest = Bitmap.createBitmap(src.width, src.height, src.config); val canvas = Canvas(dest); val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }; canvas.drawBitmap(src, 0f, 0f, paint); return dest }
    private fun toHighContrast(src: Bitmap): Bitmap { val dest = Bitmap.createBitmap(src.width, src.height, src.config); val canvas = Canvas(dest); val cm = ColorMatrix(floatArrayOf(1.5f,0f,0f,0f,-50f, 0f,1.5f,0f,0f,-50f, 0f,0f,1.5f,0f,-50f, 0f,0f,0f,1f,0f)); val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }; canvas.drawBitmap(src, 0f, 0f, paint); return dest }
    private fun shareCSV() { val file = File(getExternalFilesDir(null), MASTER_FILE); if (!file.exists()) return; val uri = FileProvider.getUriForFile(this, "$packageName.provider", file); val intent = Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; startActivity(Intent.createChooser(intent, "Share Master Sheet")) }

    private fun getMBLStatus(imiZone: Int, ieZone: Int, organism: String): String {
        val diff = ieZone - imiZone
        val cutoff = if (organism.contains("coli")) 5 else 7
        return if (diff >= cutoff) "MBL POSITIVE" else "MBL Negative"
    }

    private fun getESBLStatus(cazZone: Int, cacZone: Int): String {
        val diff = cacZone - cazZone
        return if (diff >= 5) "ESBL POSITIVE" else "ESBL Negative"
    }
}