package com.testgamecollman.luciole

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import com.testgamecollman.luciole.engine.Dir
import com.testgamecollman.luciole.engine.Game
import com.testgamecollman.luciole.engine.Spider
import com.testgamecollman.luciole.engine.SpiderKind
import com.testgamecollman.luciole.engine.SpiderMode
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GameView(context: Context) : View(context) {

    private val game = Game()
    private val audio = ChipAudio(null)
    private val prefs = context.getSharedPreferences("luciole", Context.MODE_PRIVATE)
    private var best = prefs.getInt("best", 0)

    private var running = false
    private var lastNanos = 0L

    // Géométrie, recalculée à chaque image.
    private var cell = 1f
    private var originX = 0f
    private var originY = 0f
    private var hudTop = 0f
    private var hudHeight = 0f

    private var facing = Dir.RIGHT

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var touchX = 0f
    private var touchY = 0f
    private var swiped = false
    private val swipeThreshold = 18f * resources.displayMetrics.density

    init {
        game.onSound = {
            when (it) {
                Game.SND_POLLEN -> audio.sfx(700f, 880f, 0.035f)
                Game.SND_NECTAR -> audio.sfx(300f, 980f, 0.25f)
                Game.SND_CHASE -> audio.sfx(240f, 1200f, 0.2f)
                Game.SND_DIE -> audio.sfx(620f, 70f, 0.7f)
                Game.SND_CLEAR -> audio.sfx(520f, 1560f, 0.5f)
            }
        }
    }

    fun resume() {
        audio.start()
        running = true
        lastNanos = 0L
        invalidate()
    }

    fun pause() {
        running = false
        audio.stop()
        game.pause()
        saveBest()
    }

    private fun saveBest() {
        if (game.score > best) {
            best = game.score
            prefs.edit().putInt("best", best).apply()
        }
    }

    // --- Entrées ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                touchY = event.y
                swiped = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchX
                val dy = event.y - touchY
                if (abs(dx) > swipeThreshold || abs(dy) > swipeThreshold) {
                    val dir = if (abs(dx) > abs(dy)) {
                        if (dx > 0) Dir.RIGHT else Dir.LEFT
                    } else {
                        if (dy > 0) Dir.DOWN else Dir.UP
                    }
                    game.steer(dir)
                    touchX = event.x
                    touchY = event.y
                    swiped = true
                }
            }
            MotionEvent.ACTION_UP -> if (!swiped) game.tap()
        }
        return true
    }

    // --- Rendu ---

    private fun computeLayout() {
        var top = 0f
        var bottom = 0f
        val insets = rootWindowInsets
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = i.top.toFloat()
                bottom = i.bottom.toFloat()
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop.toFloat()
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom.toFloat()
            }
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val maze = game.maze
        hudTop = top
        hudHeight = w * 0.17f
        val available = h - top - bottom - hudHeight
        cell = min(w / maze.width, available / maze.height)
        originX = (w - cell * maze.width) / 2f
        originY = top + hudHeight + (available - cell * maze.height) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min((now - lastNanos) / 1e9f, 0.05f)
        lastNanos = now
        if (running) {
            game.update(dt)
            if (game.state == Game.State.GAME_OVER || game.score > best) saveBest()
        }

        computeLayout()
        canvas.drawColor(BACKGROUND)
        drawHud(canvas)

        canvas.save()
        canvas.clipRect(originX, originY, originX + cell * game.maze.width, originY + cell * game.maze.height)
        drawMaze(canvas)
        for (s in game.spiders) drawSpider(canvas, s)
        drawFirefly(canvas)
        canvas.restore()

        drawOverlay(canvas)

        if (running) postInvalidateOnAnimation()
    }

    private fun cx(fx: Float) = originX + (fx + 0.5f) * cell
    private fun cy(fy: Float) = originY + (fy + 0.5f) * cell

    private fun drawMaze(canvas: Canvas) {
        val maze = game.maze
        drawHedges(canvas, 0.14f, HEDGE_DARK)
        drawHedges(canvas, 0.27f, HEDGE_LIGHT)

        for (y in 0 until maze.height) for (x in 0 until maze.width) {
            val px = cx(x.toFloat())
            val py = cy(y.toFloat())
            when (maze.cell(x, y)) {
                '.' -> {
                    fill.color = POLLEN
                    canvas.drawCircle(px, py, cell * 0.1f, fill)
                }
                'o' -> drawFlower(canvas, px, py)
                '-' -> {
                    stroke.color = GATE
                    stroke.strokeWidth = cell * 0.12f
                    canvas.drawLine(px - cell * 0.5f, py, px + cell * 0.5f, py, stroke)
                }
            }
        }
    }

    /** Dessine les haies comme des bandes continues : chaque case rejoint ses voisines. */
    private fun drawHedges(canvas: Canvas, inset: Float, color: Int) {
        val maze = game.maze
        val m = cell * inset
        fill.color = color
        for (y in 0 until maze.height) for (x in 0 until maze.width) {
            if (!maze.isWall(x, y)) continue
            val l = originX + x * cell
            val t = originY + y * cell
            val left = if (x == 0 || maze.isWall(x - 1, y)) l - 1f else l + m
            val right = if (x == maze.width - 1 || maze.isWall(x + 1, y)) l + cell + 1f else l + cell - m
            canvas.drawRect(left, t + m, right, t + cell - m, fill)
            val up = if (maze.isWall(x, y - 1)) t - 1f else t + m
            val down = if (maze.isWall(x, y + 1)) t + cell + 1f else t + cell - m
            canvas.drawRect(l + m, up, l + cell - m, down, fill)
        }
    }

    private fun drawFlower(canvas: Canvas, px: Float, py: Float) {
        val pulse = 1f + 0.12f * sin(game.time * 5f)
        val r = cell * 0.17f * pulse
        fill.color = PETAL
        for (i in 0 until 5) {
            val a = i * (2 * Math.PI / 5).toFloat() + game.time * 0.6f
            canvas.drawCircle(px + cos(a) * r, py + sin(a) * r, r * 0.8f, fill)
        }
        fill.color = POLLEN
        canvas.drawCircle(px, py, r * 0.6f, fill)
    }

    private fun drawFirefly(canvas: Canvas) {
        val p = game.player
        if (p.dir != Dir.NONE) facing = p.dir
        var scale = 1f
        if (game.state == Game.State.DYING) {
            scale = (game.stateTimer / Game.DYING_TIME).coerceIn(0f, 1f)
        } else if (game.state == Game.State.GAME_OVER) {
            return
        }
        val boosted = game.frightTimer > 0f
        val r = cell * 0.44f * scale
        val x = cx(p.fx)
        val y = cy(p.fy)

        // Halo lumineux.
        val glow = if (boosted) GLOW_BOOST else GLOW
        val pulse = 1f + 0.1f * sin(game.time * 6f)
        fill.color = glow
        fill.alpha = 36
        canvas.drawCircle(x, y, r * 2.4f * pulse, fill)
        fill.alpha = 70
        canvas.drawCircle(x, y, r * 1.6f * pulse, fill)
        fill.alpha = 255

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(
            when (facing) {
                Dir.DOWN -> 90f
                Dir.LEFT -> 180f
                Dir.UP -> 270f
                else -> 0f
            }
        )
        // Ailes battantes.
        val flap = 0.55f + 0.45f * abs(sin(game.time * 28f))
        fill.color = Color.WHITE
        fill.alpha = 150
        canvas.drawOval(-r * 0.55f, -r * (0.3f + 0.8f * flap), r * 0.35f, -r * 0.2f, fill)
        canvas.drawOval(-r * 0.55f, r * 0.2f, r * 0.35f, r * (0.3f + 0.8f * flap), fill)
        fill.alpha = 255
        // Abdomen lumineux, corps, tête.
        fill.color = glow
        canvas.drawOval(-r, -r * 0.5f, r * 0.2f, r * 0.5f, fill)
        fill.color = BODY
        canvas.drawOval(-r * 0.15f, -r * 0.45f, r * 0.7f, r * 0.45f, fill)
        canvas.drawCircle(r * 0.62f, 0f, r * 0.36f, fill)
        fill.color = Color.WHITE
        canvas.drawCircle(r * 0.74f, -r * 0.17f, r * 0.11f, fill)
        canvas.drawCircle(r * 0.74f, r * 0.17f, r * 0.11f, fill)
        canvas.restore()
    }

    private fun drawSpider(canvas: Canvas, s: Spider) {
        if (game.state == Game.State.GAME_OVER) return
        val x = cx(s.fx)
        var y = cy(s.fy)
        if (s.mode == SpiderMode.WAITING) y += sin(game.time * 6f + s.kind.ordinal) * cell * 0.08f
        val r = cell * 0.3f

        val color = when {
            s.frightened && game.frightTimer < 1.5f && (game.time * 6f).toInt() % 2 == 0 -> Color.WHITE
            s.frightened -> SCARED
            else -> when (s.kind) {
                SpiderKind.HUNTER -> 0xFFE5484D.toInt()
                SpiderKind.AMBUSHER -> 0xFFB06CF2.toInt()
                SpiderKind.WANDERER -> 0xFFF2994A.toInt()
                SpiderKind.PATROLLER -> 0xFF3FB7E8.toInt()
            }
        }

        // Huit pattes qui gigotent.
        stroke.color = color
        stroke.strokeWidth = cell * 0.06f
        val wiggle = sin(game.time * 14f + s.kind.ordinal * 1.7f) * 0.18f
        for (side in intArrayOf(-1, 1)) {
            for (i in 0 until 4) {
                val base = (-0.9f + i * 0.6f) + (if (i % 2 == 0) wiggle else -wiggle)
                val kx = x + side * r * 1.25f
                val ky = y + base * r - r * 0.35f
                val ex = x + side * r * 1.6f
                val ey = y + base * r * 1.3f + r * 0.45f
                canvas.drawLine(x + side * r * 0.5f, y + base * r * 0.4f, kx, ky, stroke)
                canvas.drawLine(kx, ky, ex, ey, stroke)
            }
        }

        fill.color = color
        canvas.drawCircle(x, y, r, fill)

        // Yeux tournés vers la direction de marche.
        val ox = s.dir.dx * r * 0.14f
        val oy = s.dir.dy * r * 0.14f
        fill.color = if (color == Color.WHITE) SCARED else Color.WHITE
        canvas.drawCircle(x - r * 0.36f, y - r * 0.1f, r * 0.3f, fill)
        canvas.drawCircle(x + r * 0.36f, y - r * 0.1f, r * 0.3f, fill)
        fill.color = BACKGROUND
        canvas.drawCircle(x - r * 0.36f + ox, y - r * 0.1f + oy, r * 0.14f, fill)
        canvas.drawCircle(x + r * 0.36f + ox, y - r * 0.1f + oy, r * 0.14f, fill)
    }

    private fun drawHud(canvas: Canvas) {
        val w = width.toFloat()
        val pad = w * 0.04f
        val small = hudHeight * 0.2f
        val big = hudHeight * 0.34f
        val line1 = hudTop + hudHeight * 0.3f
        val line2 = hudTop + hudHeight * 0.72f

        text.textAlign = Paint.Align.LEFT
        text.textSize = small
        text.color = HUD_DIM
        canvas.drawText("SCORE", pad, line1, text)
        text.textSize = big
        text.color = Color.WHITE
        canvas.drawText(game.score.toString(), pad, line2, text)

        text.textAlign = Paint.Align.RIGHT
        text.textSize = small
        text.color = HUD_DIM
        canvas.drawText("RECORD", w - pad, line1, text)
        text.textSize = big
        text.color = POLLEN
        canvas.drawText(maxOf(best, game.score).toString(), w - pad, line2, text)

        text.textAlign = Paint.Align.CENTER
        text.textSize = small
        text.color = HUD_DIM
        canvas.drawText("NIVEAU ${game.level}", w / 2f, line1, text)
        val r = hudHeight * 0.09f
        val gap = r * 3f
        val startX = w / 2f - gap * (Game.START_LIVES - 1) / 2f
        for (i in 0 until Game.START_LIVES) {
            fill.color = if (i < game.lives) GLOW else LIFE_OFF
            canvas.drawCircle(startX + i * gap, line2 - r, r, fill)
        }
    }

    private fun drawOverlay(canvas: Canvas) {
        val title: String
        val subtitle: String?
        when (game.state) {
            Game.State.READY -> {
                title = "Prêt ?"
                subtitle = "Glissez le doigt pour vous diriger"
            }
            Game.State.PAUSED -> {
                title = "Pause"
                subtitle = "Touchez pour reprendre"
            }
            Game.State.LEVEL_CLEAR -> {
                title = "Bravo !"
                subtitle = "Niveau ${game.level} terminé"
            }
            Game.State.GAME_OVER -> {
                title = "Partie terminée"
                subtitle = "Score ${game.score} · touchez pour rejouer"
            }
            else -> return
        }
        val w = width.toFloat()
        val mid = originY + cell * game.maze.height / 2f
        val band = cell * 2.2f
        fill.color = Color.BLACK
        fill.alpha = 190
        canvas.drawRect(0f, mid - band, w, mid + band, fill)
        fill.alpha = 255

        text.textAlign = Paint.Align.CENTER
        text.color = GLOW
        text.textSize = cell * 1.3f
        canvas.drawText(title, w / 2f, mid, text)
        if (subtitle != null) {
            text.color = Color.WHITE
            text.textSize = cell * 0.6f
            canvas.drawText(subtitle, w / 2f, mid + cell * 1.2f, text)
        }
    }

    private companion object {
        val BACKGROUND = 0xFF07130F.toInt()
        val HEDGE_DARK = 0xFF14532D.toInt()
        val HEDGE_LIGHT = 0xFF2F9E52.toInt()
        val POLLEN = 0xFFFFD85A.toInt()
        val PETAL = 0xFFFF7EB6.toInt()
        val GATE = 0xFFC9A26B.toInt()
        val GLOW = 0xFFE4F55B.toInt()
        val GLOW_BOOST = 0xFFFF9D3C.toInt()
        val BODY = 0xFF5A3A1E.toInt()
        val SCARED = 0xFF8FA3C8.toInt()
        val HUD_DIM = 0xFF8FB5A0.toInt()
        val LIFE_OFF = 0xFF24382F.toInt()
    }
}
