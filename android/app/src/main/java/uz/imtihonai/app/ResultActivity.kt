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

        val percent = if (att.total > 0) att.correct * 100 / att.total else 0
        val subjectName = Subjects.names(this)[att.subject] ?: att.subject

        text(getString(R.string.result_title) + " · " + subjectName, 16f)
        text(getString(R.string.result_score, percent), 56f, bold = true, top = 4, color = getColor(R.color.brand_primary))
        text(getString(R.string.result_correct, att.correct), 18f, top = 8)
        text(getString(R.string.result_wrong, att.wrong), 18f, top = 4)
        text(getString(R.string.result_skipped, att.skipped), 18f, top = 4)
        text(getString(R.string.result_time, formatTime(att.timeSec)), 18f, top = 4)

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
            setText(R.string.btn_close)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(28) }
            setOnClickListener { finish() }
        })
    }
}
