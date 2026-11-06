package uz.imtihonai.app

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TestActivity : AppCompatActivity() {

    private lateinit var db: Db
    private var attemptId = -1L
    private var startedAt = 0L
    private val items = ArrayList<Item>()
    private var index = 0

    private lateinit var tvCounter: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvQuestion: TextView
    private lateinit var options: RadioGroup
    private lateinit var navigator: LinearLayout
    private lateinit var btnPrev: MaterialButton
    private lateinit var btnMark: MaterialButton
    private lateinit var btnNext: MaterialButton

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            tvTimer.text = formatTime(((System.currentTimeMillis() - startedAt) / 1000).toInt())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        db = Db.get(this)

        attemptId = savedInstanceState?.getLong("attempt_id")
            ?: intent.getLongExtra("attempt_id", -1L)
        if (attemptId < 0) {
            attemptId = if (intent.getBooleanExtra("weak", false)) {
                db.createWeakAttempt(10)
            } else {
                db.createAttempt(intent.getStringExtra("subject").orEmpty(), 10)
            }
        }
        val attempt = if (attemptId > 0) db.attempt(attemptId) else null
        if (attempt == null) {
            Toast.makeText(this, R.string.test_load_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        startedAt = attempt.started
        items.addAll(db.items(attemptId))
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.test_load_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        index = savedInstanceState?.getInt("index")
            ?: items.indexOfFirst { it.selected < 0 }.coerceAtLeast(0)

        tvCounter = findViewById(R.id.tv_counter)
        tvTimer = findViewById(R.id.tv_timer)
        tvQuestion = findViewById(R.id.tv_question)
        options = findViewById(R.id.options)
        navigator = findViewById(R.id.navigator)
        btnPrev = findViewById(R.id.btn_prev)
        btnMark = findViewById(R.id.btn_mark)
        btnNext = findViewById(R.id.btn_next)

        btnPrev.setOnClickListener { if (index > 0) { index--; render() } }
        btnNext.setOnClickListener {
            if (index < items.size - 1) { index++; render() } else confirmFinish()
        }
        btnMark.setOnClickListener {
            val it = items[index]
            val m = !it.marked
            db.setMarked(attemptId, it.question.id, m)
            items[index] = it.copy(marked = m)
            render()
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("attempt_id", attemptId)
        outState.putInt("index", index)
    }

    override fun onResume() {
        super.onResume()
        if (items.isNotEmpty()) handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun render() {
        val item = items[index]
        tvCounter.text = getString(R.string.question_of, index + 1, items.size)
        tvQuestion.text = item.question.text

        options.setOnCheckedChangeListener(null)
        options.removeAllViews()
        val letters = listOf("A", "B", "C", "D")
        item.question.options.forEachIndexed { i, text ->
            options.addView(RadioButton(this).apply {
                id = OPTION_ID + i
                this.text = "${letters[i]}.  $text"
                textSize = 17f
                minHeight = dp(56)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            })
        }
        if (item.selected >= 0) options.check(OPTION_ID + item.selected)
        options.setOnCheckedChangeListener { _, checkedId ->
            val sel = checkedId - OPTION_ID
            if (sel in 0..3) {
                db.setAnswer(attemptId, items[index].question.id, sel)
                items[index] = items[index].copy(selected = sel)
                renderNavigator()
            }
        }

        btnPrev.isEnabled = index > 0
        btnMark.setText(if (item.marked) R.string.btn_unmark else R.string.btn_mark)
        btnNext.setText(if (index == items.size - 1) R.string.btn_finish else R.string.btn_next_q)
        renderNavigator()
    }

    private fun renderNavigator() {
        navigator.removeAllViews()
        items.forEachIndexed { i, it ->
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    when {
                        i == index -> getColor(R.color.brand_primary)
                        it.marked -> 0xFFF59E0B.toInt()
                        it.selected >= 0 -> getColor(R.color.brand_container)
                        else -> getColor(R.color.app_surface_variant)
                    }
                )
            }
            val chip = TextView(this).apply {
                text = (i + 1).toString()
                gravity = Gravity.CENTER
                textSize = 15f
                background = bg
                setTextColor(
                    if (i == index) getColor(R.color.brand_on_primary)
                    else if (it.marked) 0xFF000000.toInt()
                    else getColor(R.color.app_on_surface)
                )
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) }
                contentDescription = "${i + 1}"
                setOnClickListener { index = i; render() }
            }
            navigator.addView(chip)
        }
    }

    private fun confirmFinish() {
        val unanswered = items.count { it.selected < 0 }
        val msg = if (unanswered > 0) getString(R.string.finish_unanswered, unanswered)
        else getString(R.string.finish_all)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.finish_title)
            .setMessage(msg)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> finishTest() }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }

    private fun finishTest() {
        db.finishAttempt(attemptId)
        startActivity(Intent(this, ResultActivity::class.java).putExtra("attempt_id", attemptId))
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val OPTION_ID = 1000
    }
}
