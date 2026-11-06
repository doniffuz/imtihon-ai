package uz.imtihonai.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs(this).onboardingDone) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            show(
                when (item.itemId) {
                    R.id.nav_home -> HomeFragment()
                    R.id.nav_tests -> TestsFragment()
                    R.id.nav_results -> ResultsFragment()
                    else -> ProfileFragment()
                }
            )
            true
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = R.id.nav_home
            // Ilova yopilib qayta ochilganda: tugallanmagan vaqtli imtihon bo'lsa davom ettiriladi
            // (vaqti tugagan bo'lsa TestActivity uni avtomatik yakunlab natijani ko'rsatadi).
            val open = Db.get(this).unfinished()
            if (open != null && open.durationMs > 0L) {
                startActivity(Intent(this, TestActivity::class.java).putExtra("attempt_id", open.id))
            }
        }
    }

    fun select(itemId: Int) {
        findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = itemId
    }

    private fun show(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, f)
            .commit()
    }
}
