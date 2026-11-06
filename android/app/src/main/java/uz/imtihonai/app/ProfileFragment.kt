package uz.imtihonai.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class ProfileFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        val prefs = Prefs(ctx)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt(), (20 * d).toInt())
        }
        fun row(label: Int, value: String) {
            root.addView(TextView(ctx).apply {
                setText(label)
                textSize = 13f
                setPadding(0, (16 * d).toInt(), 0, 0)
            })
            root.addView(TextView(ctx).apply {
                text = value
                textSize = 18f
                setTextColor(ctx.getColor(R.color.app_on_surface))
            })
        }

        val goalName = when (prefs.goal) {
            "otm" -> getString(R.string.goal_otm)
            "school" -> getString(R.string.goal_school)
            "certificate" -> getString(R.string.goal_certificate)
            else -> getString(R.string.goal_knowledge)
        }
        val slugs = resources.getStringArray(R.array.subject_slugs)
        val names = resources.getStringArray(R.array.subject_names)
        val subjects = slugs.indices.filter { slugs[it] in prefs.subjects }.joinToString(", ") { names[it] }

        row(R.string.profile_name, prefs.name.ifBlank { getString(R.string.guest_name) })
        row(R.string.profile_goal, goalName)
        row(R.string.profile_subjects, subjects)
        row(R.string.profile_daily, getString(R.string.profile_daily_value, prefs.dailyGoal))

        root.addView(MaterialButton(ctx).apply {
            setText(R.string.profile_reset)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (56 * d).toInt()
            ).apply { topMargin = (32 * d).toInt() }
            setOnClickListener {
                prefs.clear()
                startActivity(Intent(ctx, OnboardingActivity::class.java))
                requireActivity().finish()
            }
        })
        return root
    }
}
