package uz.imtihonai.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

class HomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = Prefs(requireContext())
        val name = prefs.name.ifBlank { getString(R.string.guest_name) }
        val db = Db.get(requireContext())
        val solved = db.solvedToday()
        val goal = prefs.dailyGoal

        view.findViewById<TextView>(R.id.greeting).text = getString(R.string.greeting, name)
        view.findViewById<TextView>(R.id.progress_text).text = getString(R.string.today_progress, solved, goal)
        view.findViewById<LinearProgressIndicator>(R.id.progress_bar).progress = minOf(100, solved * 100 / goal)
        view.findViewById<TextView>(R.id.streak_text).text = getString(R.string.streak, db.streak())
        db.weakestTopic()?.let { (topic, pct) ->
            view.findViewById<TextView>(R.id.recommendation).text = getString(R.string.rec_weak, topic, pct)
        }

        val main = requireActivity() as MainActivity
        view.findViewById<MaterialButton>(R.id.btn_start_test).setOnClickListener { main.select(R.id.nav_tests) }

        val cards = view.findViewById<LinearLayout>(R.id.cards)
        addCard(cards, R.string.card_exam) { main.select(R.id.nav_tests) }
        addCard(cards, R.string.card_ai) { main.select(R.id.nav_ai) }
        addCard(cards, R.string.card_subjects) { main.select(R.id.nav_tests) }
        addCard(cards, R.string.card_results) { main.select(R.id.nav_results) }
        addCard(cards, R.string.card_scan) { main.select(R.id.nav_ai) }
    }

    private fun addCard(parent: LinearLayout, label: Int, onClick: () -> Unit) {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val card = MaterialCardView(ctx).apply {
            radius = 16 * d
            cardElevation = 0f
            setCardBackgroundColor(ctx.getColor(R.color.app_surface_variant))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * d).toInt() }
        }
        card.addView(TextView(ctx).apply {
            setText(label)
            textSize = 17f
            setTextColor(ctx.getColor(R.color.app_on_surface))
            minHeight = (56 * d).toInt()
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((20 * d).toInt(), 0, (20 * d).toInt(), 0)
        })
        parent.addView(card)
    }
}
