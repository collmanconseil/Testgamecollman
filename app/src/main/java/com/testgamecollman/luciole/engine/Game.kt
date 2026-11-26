package com.testgamecollman.luciole.engine

import kotlin.random.Random

open class Mover(var x: Int, var y: Int) {
    var dir = Dir.NONE
    /** Avancement (0..1) entre la case (x, y) et la suivante dans la direction [dir]. */
    var progress = 0f

    val fx: Float get() = x + dir.dx * progress
    val fy: Float get() = y + dir.dy * progress

    fun place(nx: Int, ny: Int, d: Dir = Dir.NONE) {
        x = nx; y = ny; dir = d; progress = 0f
    }

    fun reverse(maze: Maze) {
        if (dir == Dir.NONE) return
        if (progress > 0f) {
            x = maze.wrapX(x + dir.dx)
            y += dir.dy
            progress = 1f - progress
        }
        dir = dir.opposite
    }
}

enum class SpiderKind { HUNTER, AMBUSHER, WANDERER, PATROLLER }
enum class SpiderMode { WAITING, EXITING, ROAMING }

class Spider(val kind: SpiderKind) : Mover(0, 0) {
    var mode = SpiderMode.WAITING
    var frightened = false
    var wait = 0f
}

class Game(private val rng: Random = Random.Default) {

    enum class State { READY, PLAYING, PAUSED, DYING, LEVEL_CLEAR, GAME_OVER }

    var maze = Maze()
        private set
    val player = Mover(0, 0)
    val spiders = SpiderKind.values().map { Spider(it) }

    var state = State.READY
        private set
    var stateTimer = 0f
        private set
    var score = 0
        private set
    var lives = START_LIVES
        private set
    var level = 1
        private set
    var frightTimer = 0f
        private set
    var time = 0f
        private set

    /** Appelé avec un des codes SND_* quand un bruitage doit être joué. */
    var onSound: (Int) -> Unit = {}

    private var wanted = Dir.NONE
    private var combo = 0

    init {
        newGame()
    }

    fun newGame() {
        score = 0
        lives = START_LIVES
        level = 1
        maze = Maze()
        resetActors()
        enter(State.READY, READY_TIME)
    }

    val frightDuration: Float get() = (7f - level).coerceAtLeast(2f)
    private val playerSpeed: Float get() = 6f
    private val spiderSpeed: Float get() = (4.6f + 0.3f * level).coerceAtMost(6.4f)

    private fun enter(s: State, timer: Float = 0f) {
        state = s
        stateTimer = timer
    }

    private fun resetActors() {
        val (px, py) = maze.playerStart
        player.place(px, py)
        wanted = Dir.NONE
        frightTimer = 0f
        combo = 0
        spiders.forEachIndexed { i, s ->
            s.frightened = false
            if (i == 0) {
                s.place(maze.exit.first, maze.exit.second, Dir.LEFT)
                s.mode = SpiderMode.ROAMING
                s.wait = 0f
            } else {
                val (nx, ny) = maze.nest[(i - 1) % maze.nest.size]
                s.place(nx, ny)
                s.mode = SpiderMode.WAITING
                s.wait = 1f + 3f * (i - 1)
            }
        }
    }

    // --- Entrées ---

    fun steer(d: Dir) {
        if (d == Dir.NONE) return
        if (state == State.PAUSED) enter(State.PLAYING)
        if (state != State.PLAYING && state != State.READY) return
        wanted = d
        if (state == State.PLAYING && d == player.dir.opposite) player.reverse(maze)
    }

    fun tap() {
        when (state) {
            State.PLAYING -> enter(State.PAUSED)
            State.PAUSED -> enter(State.PLAYING)
            State.GAME_OVER -> newGame()
            else -> {}
        }
    }

    fun pause() {
        if (state == State.PLAYING) enter(State.PAUSED)
    }

    // --- Simulation ---

    fun update(dt: Float) {
        when (state) {
            State.PAUSED, State.GAME_OVER -> return
            State.READY -> {
                time += dt
                stateTimer -= dt
                if (stateTimer <= 0f) enter(State.PLAYING)
            }
            State.DYING -> {
                time += dt
                stateTimer -= dt
                if (stateTimer <= 0f) {
                    lives--
                    if (lives <= 0) enter(State.GAME_OVER) else {
                        resetActors()
                        enter(State.READY, READY_TIME)
                    }
                }
            }
            State.LEVEL_CLEAR -> {
                time += dt
                stateTimer -= dt
                if (stateTimer <= 0f) {
                    level++
                    maze = Maze()
                    resetActors()
                    enter(State.READY, READY_TIME)
                }
            }
            State.PLAYING -> {
                time += dt
                step(dt)
            }
        }
    }

    private fun step(dt: Float) {
        if (frightTimer > 0f) {
            frightTimer -= dt
            if (frightTimer <= 0f) {
                frightTimer = 0f
                combo = 0
                spiders.forEach { it.frightened = false }
            }
        }

        advance(player, playerSpeed * dt, ::choosePlayerDir, ::onPlayerArrive)
        if (state != State.PLAYING) return

        for (s in spiders) {
            if (s.mode == SpiderMode.WAITING) {
                s.wait -= dt
                if (s.wait <= 0f) s.mode = SpiderMode.EXITING
                continue
            }
            val speed = if (s.frightened) spiderSpeed * 0.6f else spiderSpeed
            advance(s, speed * dt, { chooseSpiderDir(s) }, {
                if (s.mode == SpiderMode.EXITING && s.x == maze.exit.first && s.y == maze.exit.second) {
                    s.mode = SpiderMode.ROAMING
                }
            })
        }

        for (s in spiders) {
            if (s.mode == SpiderMode.WAITING) continue
            val dx = s.fx - player.fx
            val dy = s.fy - player.fy
            if (dx * dx + dy * dy < 0.5f) {
                if (s.frightened) {
                    combo++
                    onSound(SND_CHASE)
                    score += 100 * (1 shl combo.coerceAtMost(4))
                    val (nx, ny) = maze.nest[maze.nest.size / 2]
                    s.place(nx, ny)
                    s.mode = SpiderMode.WAITING
                    s.frightened = false
                    s.wait = 3f
                } else {
                    enter(State.DYING, DYING_TIME)
                    onSound(SND_DIE)
                    return
                }
            }
        }
    }

    private inline fun advance(m: Mover, distance: Float, choose: () -> Unit, arrive: () -> Unit) {
        var d = distance
        var guard = 0
        while (d > 0f && guard++ < 4) {
            if (m.dir == Dir.NONE) {
                choose()
                if (m.dir == Dir.NONE) return
            }
            m.progress += d
            if (m.progress < 1f) return
            d = m.progress - 1f
            val nx = maze.wrapX(m.x + m.dir.dx)
            val ny = m.y + m.dir.dy
            val keep = m.dir
            m.place(nx, ny, keep)
            arrive()
            if (state != State.PLAYING) return
            choose()
        }
    }

    private fun choosePlayerDir() {
        val p = player
        p.dir = when {
            wanted != Dir.NONE && maze.passable(p.x + wanted.dx, p.y + wanted.dy) -> wanted
            p.dir != Dir.NONE && maze.passable(p.x + p.dir.dx, p.y + p.dir.dy) -> p.dir
            else -> Dir.NONE
        }
    }

    private fun onPlayerArrive() {
        when (maze.collect(player.x, player.y)) {
            '.' -> {
                score += 10
                onSound(SND_POLLEN)
            }
            'o' -> {
                score += 50
                onSound(SND_NECTAR)
                frightTimer = frightDuration
                combo = 0
                for (s in spiders) if (s.mode != SpiderMode.WAITING) {
                    s.frightened = true
                    if (s.mode == SpiderMode.ROAMING) s.reverse(maze)
                }
            }
        }
        if (maze.pollenLeft == 0) {
            enter(State.LEVEL_CLEAR, CLEAR_TIME)
            onSound(SND_CLEAR)
        }
    }

    private fun chooseSpiderDir(s: Spider) {
        val nestAccess = s.mode == SpiderMode.EXITING
        val options = Dir.MOVES.filter {
            it != s.dir.opposite && maze.passable(s.x + it.dx, s.y + it.dy, nestAccess)
        }
        if (options.isEmpty()) {
            val back = s.dir.opposite
            s.dir = if (back != Dir.NONE && maze.passable(s.x + back.dx, s.y + back.dy, nestAccess)) back else Dir.NONE
            return
        }
        val target: Pair<Int, Int>? = when {
            nestAccess -> maze.exit
            s.frightened -> null
            else -> when (s.kind) {
                SpiderKind.HUNTER -> player.x to player.y
                SpiderKind.AMBUSHER -> (player.x + 3 * player.dir.dx) to (player.y + 3 * player.dir.dy)
                SpiderKind.WANDERER -> null
                SpiderKind.PATROLLER -> {
                    val corners = listOf(
                        1 to 1, maze.width - 2 to 1,
                        maze.width - 2 to maze.height - 2, 1 to maze.height - 2,
                    )
                    corners[(time / 6f).toInt() % corners.size]
                }
            }
        }
        s.dir = if (target == null) options[rng.nextInt(options.size)] else options.minByOrNull {
            val ax = s.x + it.dx - target.first
            val ay = s.y + it.dy - target.second
            ax * ax + ay * ay
        }!!
    }

    companion object {
        const val START_LIVES = 3
        const val READY_TIME = 1.5f
        const val DYING_TIME = 1.2f
        const val CLEAR_TIME = 1.8f
        const val SND_POLLEN = 0
        const val SND_NECTAR = 1
        const val SND_CHASE = 2
        const val SND_DIE = 3
        const val SND_CLEAR = 4
    }
}
