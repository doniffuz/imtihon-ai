package uz.imtihonai.app

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class TestActivity : AppCompatActivity() {

    private lateinit var db: Db
    private var attemptId = -1L
    private var baseElapsed = 0
    private var resumedAt = 0L
    private var finishing = false
    private var meta: AttemptMeta? = null
    private var lastWarn = 0
    private var dialog: androidx.appcompat.app.AlertDialog? = null
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
            if (finishing) return
            if (tick()) handler.postDelayed(this, 250)
        }
    }

    /** Taymerni yangilaydi. Vaqt tugagan bo'lsa imtihonni yakunlaydi va false qaytaradi. */
    private fun tick(): Boolean {
        val m = meta ?: return false
        if (m.timed) {
            val rem = m.remainingMs()
            tvTimer.text = "⏱ " + formatTime(ExamTimer.displaySeconds(rem))
            if (rem <= 0L) { autoFinish(); return false }
            val level = ExamTimer.warnLevel(rem)
            tvTimer.setTextColor(if (level == 1) 0xFFB3261E.toInt() else getColor(R.color.app_on_surface))
            if (level != 0 && (lastWarn == 0 || level < lastWarn)) {
                Snackbar.make(findViewById(android.R.id.content), getString(R.string.time_warning, level), Snackbar.LENGTH_SHORT).show()
            }
            lastWarn = level
        } else {
            tvTimer.text = "⏱ " + formatTime(currentElapsed())
        }
        return true
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
        val m = if (attempt != null) db.attemptMeta(attemptId) else null
        if (attempt == null || m == null) {
            Toast.makeText(this, R.string.test_load_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        // Yakunlangan/qulflangan imtihonga qaytib bo'lmaydi: faqat natija ko'rsatiladi.
        if (!m.isOpen) { showResultAndFinish(); return }
        // Ilova yopiq turganda vaqt tugagan bo'lsa — avtomatik yakunlanadi.
        if (m.timed && m.remainingMs() <= 0L) { meta = m; autoFinish(); return }
        meta = m
        lastWarn = ExamTimer.warnLevel(m.remainingMs()) // ochilganda o'tib ketgan bosqichlar uchun ogohlantirmaydi
        baseElapsed = db.elapsed(attemptId)
        items.addAll(db.items(attemptId))
        if (items.isEmpty()) {
            Toast.makeText(this, R.string.test_load_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val firstOpen = items.indexOfFirst { it.selected < 0 }.coerceAtLeast(0)
        index = (savedInstanceState?.getInt("index") ?: if (m.curQ in items.indices) m.curQ else firstOpen)
            .coerceIn(0, items.size - 1)

        tvCounter = findViewById(R.id.tv_counter)
        tvTimer = findViewById(R.id.tv_timer)
        tvQuestion = findViewById(R.id.tv_question)
        options = findViewById(R.id.options)
        navigator = findViewById(R.id.navigator)
        btnPrev = findViewById(R.id.btn_prev)
        btnMark = findViewById(R.id.btn_mark)
        btnNext = findViewById(R.id.btn_next)

        btnPrev.setOnClickListener { if (!finishing && index > 0) { index--; render() } }
        btnNext.setOnClickListener {
            if (finishing) return@setOnClickListener
            if (index < items.size - 1) { index++; render() } else confirmFinish()
        }
        btnMark.setOnClickListener {
            if (index !in items.indices) return@setOnClickListener
            val it = items[index]
            val m = !it.marked
            if (!db.setMarked(attemptId, it.question.id, m)) { lockedNow(); return@setOnClickListener }
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

    private fun currentElapsed(): Int =
        baseElapsed + ((SystemClock.elapsedRealtime() - resumedAt) / 1000).toInt()

    override fun onResume() {
        super.onResume()
        if (items.isNotEmpty() && !finishing) {
            resumedAt = SystemClock.elapsedRealtime()
            if (tick()) handler.post(ticker)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        if (items.isNotEmpty() && !finishing) {
            // Vaqtli imtihonda vaqt boshlanish momentidan hisoblanadi; bu faqat vaqtsiz rejim uchun faol vaqt.
            baseElapsed = currentElapsed()
            resumedAt = SystemClock.elapsedRealtime()
            db.setElapsed(attemptId, baseElapsed)
            db.setCurrentQuestion(attemptId, index)
        }
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    private fun render() {
        val item = items[index]
        db.setCurrentQuestion(attemptId, index)
        tvCounter.text = getString(R.string.question_of, index + 1, items.size)
        tvQuestion.text = item.question.text

        options.setOnCheckedChangeListener(null)
        options.removeAllViews()
        val letters = listOf("A", "B", "C", "D")
        // Variantlar tartibi imtihon yaratilganda saqlangan: qayta ochilsa o'zgarmaydi.
        // Ko'rsatilgan i-o'rinda asl item.order[i]-variant turadi; saqlanadigan javob — asl indeks.
        item.order.forEachIndexed { i, original ->
            options.addView(RadioButton(this).apply {
                id = OPTION_ID + i
                this.text = "${letters[i]}.  ${item.question.options[original]}"
                textSize = 17f
                minHeight = dp(56)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            })
        }
        if (item.selected >= 0) {
            val shown = item.order.indexOf(item.selected)
            if (shown >= 0) options.check(OPTION_ID + shown)
        }
        options.setOnCheckedChangeListener { _, checkedId ->
            val shown = checkedId - OPTION_ID
            if (finishing || shown !in 0..3 || index !in items.indices) return@setOnCheckedChangeListener
            val cur = items[index]
            val original = cur.order[shown]
            if (db.setAnswer(attemptId, cur.question.id, original)) {
                items[index] = cur.copy(selected = original)
                renderNavigator()
            } else {
                lockedNow() // vaqt tugagan yoki imtihon yakunlangan: javob qabul qilinmaydi
            }
        }

        btnPrev.isEnabled = index > 0
        btnMark.setText(if (item.marked) R.string.btn_unmark else R.string.btn_mark)
        btnNext.setText(if (index == items.size - 1) R.string.btn_finish else R.string.btn_next_q)
        renderNavigator()
    }

    /** Javob/belgi qabul qilinmadi: imtihon yakunlangan bo'lsa natijaga o'tamiz. */
    private fun lockedNow() {
        val m = db.attemptMeta(attemptId)
        if (m == null || !m.isOpen) showResultAndFinish()
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
        if (finishing || dialog?.isShowing == true) return
        val unanswered = items.count { it.selected < 0 }
        val msg = if (unanswered > 0) getString(R.string.finish_unanswered, unanswered)
        else getString(R.string.finish_all)
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.finish_title)
            .setMessage(msg)
            .setPositiveButton(R.string.dialog_yes) { _, _ -> finishTest() }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }

    private fun finishTest() {
        if (finishing) return
        finishing = true
        handler.removeCallbacks(ticker)
        db.setElapsed(attemptId, currentElapsed())
        db.finishAttempt(attemptId, "manual")
        showResultAndFinish()
    }

    /** Vaqt 00:00: javoblar saqlangan, javobsizlar skipped, imtihon qulflanadi, natija hisoblanadi, Result ochiladi. */
    private fun autoFinish() {
        if (finishing) return
        finishing = true
        handler.removeCallbacks(ticker)
        dialog?.dismiss()
        db.finishAttempt(attemptId, "timeout")
        showResultAndFinish()
    }

    private fun showResultAndFinish() {
        finishing = true
        startActivity(Intent(this, ResultActivity::class.java).putExtra("attempt_id", attemptId))
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val OPTION_ID = 1000
    }
}
