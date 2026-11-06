package uz.imtihonai.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class TestsFragment : Fragment() {

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
        val db = Db.get(ctx)
        val prefs = Prefs(ctx)
        root.removeAllViews()

        root.addView(TextView(ctx).apply {
            setText(R.string.tests_title)
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ctx.getColor(R.color.app_on_surface))
        })
        root.addView(TextView(ctx).apply {
            setText(R.string.tests_subtitle)
            textSize = 16f
            setTextColor(ctx.getColor(R.color.app_on_surface))
            setPadding(0, (4 * d).toInt(), 0, (12 * d).toInt())
        })

        val names = Subjects.names(ctx)

        db.unfinished()?.let { att ->
            val card = card(ctx, d, ctx.getColor(R.color.brand_container))
            val inner = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((20 * d).toInt(), (16 * d).toInt(), (20 * d).toInt(), (16 * d).toInt())
            }
            inner.addView(TextView(ctx).apply {
                text = getString(R.string.tests_resume_title) + " · " + (names[att.subject] ?: att.subject)
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ctx.getColor(R.color.brand_on_container))
            })
            inner.addView(MaterialButton(ctx).apply {
                setText(R.string.tests_resume_btn)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (52 * d).toInt())
                    .apply { topMargin = (12 * d).toInt() }
                setOnClickListener {
                    startActivity(Intent(ctx, TestActivity::class.java).putExtra("attempt_id", att.id))
                }
            })
            card.addView(inner)
            root.addView(card)
        }

        val weak = db.weakTopics()
        if (weak.isNotEmpty()) {
            val card = card(ctx, d, ctx.getColor(R.color.brand_container))
            card.isClickable = true
            card.isFocusable = true
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((20 * d).toInt(), (16 * d).toInt(), (20 * d).toInt(), (16 * d).toInt())
            }
            col.addView(TextView(ctx).apply {
                setText(R.string.weak_practice_title)
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ctx.getColor(R.color.brand_on_container))
            })
            col.addView(TextView(ctx).apply {
                text = weak.joinToString("\n") { "${it.first} — ${it.second}%" }
                textSize = 14f
                setTextColor(ctx.getColor(R.color.brand_on_container))
            })
            card.addView(col)
            card.setOnClickListener {
                startActivity(Intent(ctx, TestActivity::class.java).putExtra("weak", true))
            }
            root.addView(card)
        }

        // Foydalanuvchi tanlagan fanlar birinchi turadi.
        val chosen = prefs.subjects
        val order = names.keys.filter { it != "weak" }.sortedBy { if (it in chosen) 0 else 1 }
        for (slug in order) {
            val count = db.questionCount(slug)
            val card = card(ctx, d, ctx.getColor(R.color.app_surface_variant))
            card.isClickable = true
            card.isFocusable = true
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = (64 * d).toInt()
                gravity = Gravity.CENTER_VERTICAL
                setPadding((20 * d).toInt(), (12 * d).toInt(), (20 * d).toInt(), (12 * d).toInt())
            }
            row.addView(TextView(ctx).apply {
                text = names[slug]
                textSize = 18f
                setTextColor(ctx.getColor(R.color.app_on_surface))
            })
            row.addView(TextView(ctx).apply {
                text = if (count > 0) getString(R.string.tests_questions_count, count)
                else getString(R.string.tests_no_questions)
                textSize = 14f
                setTextColor(ctx.getColor(R.color.app_on_surface))
            })
            card.addView(row)
            card.setOnClickListener {
                if (count == 0) {
                    Toast.makeText(ctx, R.string.tests_no_questions, Toast.LENGTH_SHORT).show()
                } else {
                    startActivity(Intent(ctx, TestActivity::class.java).putExtra("subject", slug))
                }
            }
            root.addView(card)
        }
    }

    private fun card(ctx: android.content.Context, d: Float, color: Int) = MaterialCardView(ctx).apply {
        radius = 16 * d
        cardElevation = 0f
        setCardBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (10 * d).toInt() }
    }
}
