package uz.imtihonai.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultsFragment : Fragment() {

    private lateinit var root: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val d = resources.displayMetrics.density
        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        }
        return ScrollView(requireContext()).apply { addView(root) }
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val list = Db.get(ctx).finishedAttempts()
        val names = Subjects.names(ctx)
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US)
        root.removeAllViews()

        root.addView(TextView(ctx).apply {
            setText(R.string.results_title)
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ctx.getColor(R.color.app_on_surface))
            setPadding(0, 0, 0, (8 * d).toInt())
        })

        if (list.isEmpty()) {
            root.addView(TextView(ctx).apply {
                setText(R.string.results_empty)
                textSize = 16f
                setTextColor(ctx.getColor(R.color.app_on_surface))
                setPadding(0, (24 * d).toInt(), 0, 0)
            })
            return
        }

        val db = Db.get(ctx)
        val t = db.totals()
        fun line(s: String, size: Float, bold: Boolean = false, top: Int = 4) {
            root.addView(TextView(ctx).apply {
                text = s
                textSize = size
                setTextColor(ctx.getColor(R.color.app_on_surface))
                if (bold) setTypeface(typeface, Typeface.BOLD)
                setPadding(0, (top * d).toInt(), 0, 0)
            })
        }
        val accuracy = if (t[2] > 0) t[1] * 100 / t[2] else 0
        line(getString(R.string.stats_title), 20f, bold = true, top = 8)
        line(getString(R.string.stats_tests, t[0]), 16f)
        line(getString(R.string.stats_solved, t[2]), 16f)
        line(getString(R.string.stats_accuracy, accuracy), 16f)
        val avg = list.map { if (it.total > 0) it.correct * 100 / it.total else 0 }.average().toInt()
        line(getString(R.string.stats_avg, avg), 16f)
        line(getString(R.string.stats_time, formatTime(t[3])), 16f)
        line(getString(R.string.stats_by_subject), 20f, bold = true, top = 20)
        for ((slug, ok, total) in db.subjectStats()) {
            line("${names[slug] ?: slug} — ${ok * 100 / total}%  ($ok/$total)", 16f, top = 6)
        }
        line(getString(R.string.history_title), 20f, bold = true, top = 20)

        for (a in list) {
            val percent = if (a.total > 0) a.correct * 100 / a.total else 0
            val card = MaterialCardView(ctx).apply {
                radius = 16 * d
                cardElevation = 0f
                setCardBackgroundColor(ctx.getColor(R.color.app_surface_variant))
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (10 * d).toInt() }
                setOnClickListener {
                    startActivity(Intent(ctx, ResultActivity::class.java).putExtra("attempt_id", a.id))
                }
            }
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((20 * d).toInt(), (14 * d).toInt(), (20 * d).toInt(), (14 * d).toInt())
            }
            col.addView(TextView(ctx).apply {
                text = names[a.subject] ?: a.subject
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ctx.getColor(R.color.app_on_surface))
            })
            col.addView(TextView(ctx).apply {
                text = getString(R.string.result_row, a.correct, a.total, percent)
                textSize = 16f
                setTextColor(ctx.getColor(R.color.app_on_surface))
            })
            col.addView(TextView(ctx).apply {
                text = fmt.format(Date(a.finished))
                textSize = 13f
                setTextColor(ctx.getColor(R.color.app_on_surface))
            })
            card.addView(col)
            root.addView(card)
        }
    }
}
