package com.testgamecollman.luciole

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.testgamecollman.luciole.platformer.PlatformActivity

/** Menu d'accueil : choix du jeu. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF07130F.toInt())
            setPadding((32 * dp).toInt(), 0, (32 * dp).toInt(), 0)
        }
        root.addView(TextView(this).apply {
            text = "MINI ARCADE"
            textSize = 34f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(0xFFFFE23D.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (40 * dp).toInt())
        })
        root.addView(entry("Luciole", "Labyrinthe : ramassez le pollen, évitez les araignées", 0xFF2F9E52.toInt(), LucioleActivity::class.java))
        root.addView(entry("Bip et les rouages", "Plateforme rétro : sautez, récupérez les rouages", 0xFF3D6BFF.toInt(), PlatformActivity::class.java))
        setContentView(root)
    }

    private fun entry(title: String, subtitle: String, color: Int, target: Class<out Activity>): LinearLayout {
        val dp = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = 14 * dp
            }
            val pad = (20 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = (20 * dp).toInt() }
            addView(TextView(context).apply {
                text = title
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = subtitle
                textSize = 15f
                setTextColor(Color.WHITE)
            })
            isClickable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, target)) }
        }
    }
}
