package uz.imtihonai.app

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Db.get(this)
        val id = intent.getLongExtra("attempt_id", -1L)
        val att = db.attempt(id)
        if (att == null) { finish(); return }
        val items = db.items(id)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val scroll = ScrollView(this).apply {
            fitsSystemWindows = true
            addView(root)
        }
        setContentView(scroll)

        fun text(s: String, size: Float, bold: Boolean = false, top: Int = 0, color: Int = getColor(R.color.app_on_surface)) {
            root.addView(TextView(this).apply {
                this.text = s
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(top), 0, 0)
            })
        }

        val answered = att.total - att.skipped
        // Foiz = javob berilgan savollar ichida to'g'rilari (35/42 = 83.3%). Ball = jami savollarga nisbatan.
        val accuracy = percentOf(att.correct, answered)
        val score = percentOf(att.correct, att.total)
        val subjectName = Subjects.names(this)[att.subject] ?: att.subject
        val timeout = att.timedOut

        text(getString(R.string.result_finished_title), 16f, bold = true)
        text(subjectName, 16f, top = 2)
        text(getString(R.string.result_score_big, Math.round(score).toInt()), 48f, bold = true, top = 4, color = getColor(R.color.brand_primary))
        text(getString(R.string.result_accuracy, percentText(accuracy)), 20f, bold = true, top = 2)
        text(getString(R.string.result_total, att.total), 18f, top = 8)
        text(getString(R.string.result_answered, answered), 18f, top = 4)
        text(getString(R.string.result_unanswered, att.skipped), 18f, top = 4)
        text(getString(R.string.result_correct, att.correct), 18f, top = 8)
        text(getString(R.string.result_wrong, att.wrong), 18f, top = 4)
        text(getString(R.string.result_points, att.correct, att.total, percentText(percentOf(att.correct, att.total))), 18f, top = 4)
        text(getString(R.string.result_time, formatTime(att.timeSec)), 18f, top = 4)
        text(
            getString(R.string.result_reason, getString(if (timeout) R.string.reason_timeout else R.string.reason_manual)),
            18f, bold = true, top = 8,
            color = if (timeout) 0xFFB3261E.toInt() else getColor(R.color.app_on_surface)
        )

        text(getString(R.string.result_topics), 20f, bold = true, top = 28)
        for ((topic, ok, total) in db.topicBreakdown(id)) {
            text("$topic — ${ok * 100 / total}%  ($ok/$total)", 16f, top = 8)
        }

        text(getString(R.string.result_mistakes), 20f, bold = true, top = 28)
        val mistakes = items.filter { it.selected != it.question.answer }
        if (mistakes.isEmpty()) {
            text(getString(R.string.result_no_mistakes), 16f, top = 8)
        } else {
            for (m in mistakes) {
                val q = m.question
                text("${m.pos + 1}. ${q.text}", 17f, bold = true, top = 18)
                val yours = if (m.selected >= 0) q.options[m.selected] else getString(R.string.no_answer)
                text(getString(R.string.your_answer, yours), 16f, top = 6, color = 0xFFB3261E.toInt())
                text(getString(R.string.correct_answer, q.options[q.answer]), 16f, top = 4, color = 0xFF1B7F3B.toInt())
                if (q.explanation.isNotBlank()) text(getString(R.string.explanation, q.explanation), 15f, top = 4)
            }
        }

        root.addView(MaterialButton(this).apply {
            setText(R.string.btn_retry)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(28) }
            setOnClickListener {
                val newId = if (att.subject == "weak") db.createWeakAttempt(att.total.coerceAtLeast(10))
                else db.createExam(
                    ExamConfig(
                        if (att.subject == "all") null else att.subject, null,
                        ExamMode.from(att.mode), att.total, att.durationMs
                    )
                )
                if (newId > 0) {
                    startActivity(android.content.Intent(this@ResultActivity, TestActivity::class.java).putExtra("attempt_id", newId))
                    finish()
                }
            }
        })
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(R.string.btn_close)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(12) }
            setOnClickListener { finish() }
        })
    }
}
