package uz.imtihonai.app

import android.content.Context

/** Lokal sozlamalar. Hisob (account) yo'q: foydalanuvchi mehmon sifatida ishlaydi. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("imtihon_ai", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(v) = sp.edit().putBoolean("onboarding_done", v).apply()

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v).apply()

    var goal: String
        get() = sp.getString("goal", "") ?: ""
        set(v) = sp.edit().putString("goal", v).apply()

    var subjects: Set<String>
        get() = sp.getStringSet("subjects", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("subjects", v).apply()

    var dailyGoal: Int
        get() = sp.getInt("daily_goal", 20)
        set(v) = sp.edit().putInt("daily_goal", v).apply()

    fun clear() = sp.edit().clear().apply()
}
