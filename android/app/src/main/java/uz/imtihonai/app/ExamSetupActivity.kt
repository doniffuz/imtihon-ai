package uz.imtihonai.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Fan → imtihon turi → savollar soni → vaqt tanlash ekrani. */
class ExamSetupActivity : AppCompatActivity() {

    private lateinit var db: Db
    private var subject = ""
    private var mode = ExamMode.TIMED_EXAM
    private var topics: List<Triple<String, String, Int>> = emptyList()
    private var topicId: String? = null
    private var count = 0
    private var minutes = 60 // 0 = vaqtsiz

    private lateinit var root: LinearLayout
    private lateinit var topicBox: LinearLayout
    private lateinit var countGroup: RadioGroup
    private lateinit var timeGroup: RadioGroup
    private lateinit var availableText: TextView
    private lateinit var startBtn: MaterialButton

    // Hozircha ekranda ko'rsatiladigan turlar (qolganlari arxitekturada tayyor: ExamMode).
    private val modes = listOf(ExamMode.TIMED_EXAM, ExamMode.PRACTICE, ExamMode.TOPIC_TEST, ExamMode.RANDOM_TEST)
    private val counts = listOf(10, 20, 30, 50, 100)
    private val times = listOf(15, 30, 60, 90)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db.get(this)
        subject = intent.getStringExtra("subject").orEmpty()
        val name = Subjects.names(this)[subject] ?: subject
        topics = db.topicsOf(subject)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })

        label(name, 26f, bold = true)
        availableText = label("", 14f)

        label(getString(R.string.setup_mode), 18f, bold = true, top = 20)
        val modeGroup = RadioGroup(this)
        modes.forEach { m ->
            modeGroup.addView(radio(modeLabel(m), m.ordinal))
        }
        modeGroup.check(mode.ordinal + ID_BASE)
        modeGroup.setOnCheckedChangeListener { _, id ->
            mode = ExamMode.values()[id - ID_BASE]
            onModeChanged()
        }
        root.addView(modeGroup)

        topicBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        topicBox.addView(label(getString(R.string.setup_topic), 18f, bold = true, top = 20, add = false))
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            topics.map { "${it.second} (${it.third})" })
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                topicId = topics.getOrNull(pos)?.first
                rebuildCounts()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        topicBox.addView(spinner)
        root.addView(topicBox)

        label(getString(R.string.setup_count), 18f, bold = true, top = 20)
        countGroup = RadioGroup(this)
        root.addView(countGroup)

        label(getString(R.string.setup_time), 18f, bold = true, top = 20)
        timeGroup = RadioGroup(this)
        root.addView(timeGroup)

        startBtn = MaterialButton(this).apply {
            setText(R.string.setup_start)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(28) }
            setOnClickListener { start() }
        }
        root.addView(startBtn)
        onModeChanged()
    }

    private fun modeLabel(m: ExamMode) = getString(
        when (m) {
            ExamMode.PRACTICE -> R.string.mode_practice
            ExamMode.TIMED_EXAM -> R.string.mode_timed
            ExamMode.TOPIC_TEST -> R.string.mode_topic
            ExamMode.RANDOM_TEST -> R.string.mode_random
            ExamMode.EXAM -> R.string.mode_exam
            ExamMode.FULL_EXAM -> R.string.mode_full
        }
    )

    private fun effectiveSubject(): String? = if (mode == ExamMode.RANDOM_TEST) null else subject
    private fun effectiveTopic(): String? = if (mode == ExamMode.TOPIC_TEST) topicId else null

    private fun onModeChanged() {
        topicBox.visibility = if (mode == ExamMode.TOPIC_TEST && topics.isNotEmpty()) View.VISIBLE else View.GONE
        if (mode == ExamMode.TOPIC_TEST && topicId == null) topicId = topics.firstOrNull()?.first
        if (mode.timeRequired && minutes == 0) minutes = 60
        rebuildTimes()
        rebuildCounts()
    }

    private fun rebuildTimes() {
        timeGroup.setOnCheckedChangeListener(null)
        timeGroup.removeAllViews()
        times.forEach { timeGroup.addView(radio(getString(R.string.setup_minutes, it), it)) }
        if (!mode.timeRequired) timeGroup.addView(radio(getString(R.string.setup_untimed), 0))
        if (timeGroup.findViewById<View>(minutes + ID_BASE) == null) minutes = times[2]
        timeGroup.check(minutes + ID_BASE)
        timeGroup.setOnCheckedChangeListener { _, id -> minutes = id - ID_BASE }
    }

    private fun rebuildCounts() {
        val available = db.availableFor(effectiveSubject(), effectiveTopic())
        availableText.text = getString(R.string.setup_available, available)
        countGroup.setOnCheckedChangeListener(null)
        countGroup.removeAllViews()
        val opts = counts.filter { it <= available }.toMutableList()
        if (available > 0 && available < counts.last() && available !in opts) opts.add(available) // "barchasi"
        opts.sort()
        opts.forEach { countGroup.addView(radio(getString(R.string.setup_questions, it), it)) }
        if (opts.isEmpty()) { count = 0; startBtn.isEnabled = false; return }
        startBtn.isEnabled = true
        if (count !in opts) count = if (20 in opts) 20 else opts.last()
        countGroup.check(count + ID_BASE)
        countGroup.setOnCheckedChangeListener { _, id -> count = id - ID_BASE }
    }

    private fun start() {
        if (count <= 0) { Toast.makeText(this, R.string.tests_no_questions, Toast.LENGTH_SHORT).show(); return }
        val duration = if (mode.timeRequired) maxOf(minutes, 1) * 60_000L else minutes * 60_000L
        val id = db.createExam(ExamConfig(effectiveSubject(), effectiveTopic(), mode, count, duration))
        if (id < 0) { Toast.makeText(this, R.string.test_load_error, Toast.LENGTH_LONG).show(); return }
        startActivity(Intent(this, TestActivity::class.java).putExtra("attempt_id", id))
        finish()
    }

    private fun radio(text: String, value: Int) = RadioButton(this).apply {
        id = value + ID_BASE
        this.text = text
        textSize = 16f
        minHeight = dp(48)
    }

    private fun label(s: String, size: Float, bold: Boolean = false, top: Int = 0, add: Boolean = true): TextView {
        val tv = TextView(this).apply {
            text = s
            textSize = size
            setTextColor(getColor(R.color.app_on_surface))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(top), 0, dp(4))
        }
        if (add) root.addView(tv)
        return tv
    }

    companion object { private const val ID_BASE = 1000 }
}
