package uz.imtihonai.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class OnboardingActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var content: LinearLayout
    private lateinit var error: TextView
    private lateinit var btnNext: MaterialButton
    private lateinit var btnBack: MaterialButton

    private var step = 0
    private var nameInput: TextInputEditText? = null
    private var goalGroup: RadioGroup? = null
    private val subjectBoxes = mutableListOf<CheckBox>()
    private var dailyGroup: RadioGroup? = null

    private var chosenName = ""
    private var chosenGoal = ""
    private var chosenSubjects = setOf<String>()
    private var chosenDaily = 20

    private val goalKeys = listOf("otm", "school", "certificate", "knowledge")
    private val dailyValues = listOf(10, 20, 30, 50)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        prefs = Prefs(this)
        title = findViewById(R.id.ob_title)
        subtitle = findViewById(R.id.ob_subtitle)
        content = findViewById(R.id.ob_content)
        error = findViewById(R.id.ob_error)
        btnNext = findViewById(R.id.btn_next)
        btnBack = findViewById(R.id.btn_back)

        step = savedInstanceState?.getInt("step") ?: 0
        chosenName = savedInstanceState?.getString("name") ?: ""
        chosenGoal = savedInstanceState?.getString("goal") ?: ""
        chosenSubjects = savedInstanceState?.getStringArray("subjects")?.toSet() ?: emptySet()
        chosenDaily = savedInstanceState?.getInt("daily") ?: 20

        btnNext.setOnClickListener { onNext() }
        btnBack.setOnClickListener {
            if (step > 0) { collect(); step--; render() }
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        collect()
        outState.putInt("step", step)
        outState.putString("name", chosenName)
        outState.putString("goal", chosenGoal)
        outState.putStringArray("subjects", chosenSubjects.toTypedArray())
        outState.putInt("daily", chosenDaily)
    }

    /** Joriy qadamdagi tanlovni xotiraga oladi. */
    private fun collect() {
        when (step) {
            0 -> nameInput?.let { chosenName = it.text?.toString()?.trim().orEmpty() }
            1 -> goalGroup?.let { g ->
                val idx = (0 until g.childCount).indexOfFirst { (g.getChildAt(it) as RadioButton).isChecked }
                chosenGoal = if (idx >= 0) goalKeys[idx] else ""
            }
            2 -> {
                val slugs = resources.getStringArray(R.array.subject_slugs)
                chosenSubjects = subjectBoxes.mapIndexedNotNull { i, b -> if (b.isChecked) slugs[i] else null }.toSet()
            }
            3 -> dailyGroup?.let { g ->
                val idx = (0 until g.childCount).indexOfFirst { (g.getChildAt(it) as RadioButton).isChecked }
                if (idx >= 0) chosenDaily = dailyValues[idx]
            }
        }
    }

    private fun onNext() {
        collect()
        error.visibility = View.GONE
        when {
            step == 1 && chosenGoal.isEmpty() -> return showError(R.string.err_choose_goal)
            step == 2 && chosenSubjects.isEmpty() -> return showError(R.string.err_choose_subject)
        }
        if (step < 4) {
            step++
            render()
        } else {
            prefs.name = chosenName
            prefs.goal = chosenGoal
            prefs.subjects = chosenSubjects
            prefs.dailyGoal = chosenDaily
            prefs.onboardingDone = true
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun showError(res: Int) {
        error.setText(res)
        error.visibility = View.VISIBLE
    }

    private fun render() {
        content.removeAllViews()
        error.visibility = View.GONE
        subjectBoxes.clear()
        btnBack.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
        btnNext.setText(if (step == 4) R.string.btn_start else R.string.btn_next)
        subtitle.visibility = View.VISIBLE

        when (step) {
            0 -> {
                title.setText(R.string.app_name)
                subtitle.setText(R.string.slogan)
                val layout = TextInputLayout(this).apply { hint = getString(R.string.ob_name_hint) }
                val input = TextInputEditText(layout.context).apply {
                    setText(chosenName)
                    setSingleLine()
                }
                layout.addView(input)
                content.addView(layout)
                nameInput = input
                content.addView(TextView(this).apply {
                    setText(R.string.disclaimer)
                    setPadding(0, dp(16), 0, 0)
                })
            }
            1 -> {
                title.setText(R.string.ob_goal_title)
                subtitle.visibility = View.GONE
                val labels = listOf(R.string.goal_otm, R.string.goal_school, R.string.goal_certificate, R.string.goal_knowledge)
                goalGroup = radioGroup(labels.map { getString(it) }, goalKeys.indexOf(chosenGoal))
            }
            2 -> {
                title.setText(R.string.ob_subjects_title)
                subtitle.visibility = View.GONE
                val names = resources.getStringArray(R.array.subject_names)
                val slugs = resources.getStringArray(R.array.subject_slugs)
                names.forEachIndexed { i, n ->
                    val box = CheckBox(this).apply {
                        text = n
                        textSize = 17f
                        minHeight = dp(52)
                        isChecked = slugs[i] in chosenSubjects
                    }
                    subjectBoxes.add(box)
                    content.addView(box)
                }
            }
            3 -> {
                title.setText(R.string.ob_daily_title)
                subtitle.visibility = View.GONE
                val labels = listOf(R.string.ob_daily_10, R.string.ob_daily_20, R.string.ob_daily_30, R.string.ob_daily_50)
                dailyGroup = radioGroup(labels.map { getString(it) }, dailyValues.indexOf(chosenDaily))
            }
            4 -> {
                title.setText(R.string.ob_ready_title)
                subtitle.setText(R.string.ob_ready_text)
            }
        }
    }

    private fun radioGroup(labels: List<String>, checked: Int): RadioGroup {
        val group = RadioGroup(this)
        labels.forEachIndexed { i, l ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = l
                textSize = 17f
                minHeight = dp(52)
            })
        }
        if (checked >= 0) (group.getChildAt(checked) as RadioButton).isChecked = true
        content.addView(group)
        return group
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
