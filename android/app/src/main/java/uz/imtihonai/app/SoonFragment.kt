package uz.imtihonai.app

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Hali yozilmagan bo'lim: soxta funksiya o'rniga ochiq xabar ko'rsatadi. */
class SoonFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ctx = requireContext()
        val pad = (24 * resources.displayMetrics.density).toInt()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            addView(TextView(ctx).apply {
                setText(R.string.soon_title)
                textSize = 24f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(ctx).apply {
                setText(requireArguments().getInt("msg"))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, pad / 2, 0, 0)
            })
        }
    }

    companion object {
        fun newInstance(msg: Int) = SoonFragment().apply {
            arguments = Bundle().apply { putInt("msg", msg) }
        }
    }
}
