package com.testgamecollman.luciole.engine

enum class Dir(val dx: Int, val dy: Int) {
    NONE(0, 0), UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    val opposite: Dir
        get() = when (this) {
            UP -> DOWN
            DOWN -> UP
            LEFT -> RIGHT
            RIGHT -> LEFT
            NONE -> NONE
        }

    companion object {
        val MOVES = listOf(UP, LEFT, DOWN, RIGHT)
    }
}

/**
 * Le jardin : '#' haie, '.' pollen, 'o' nectar, 'n' nid des araignées,
 * '-' porte du nid, 'P' départ de la luciole, ' ' allée vide.
 */
class Maze {
    val rows: List<String> = HALF.map { it + it.substring(0, it.length - 1).reversed() }
    val width = rows[0].length
    val height = rows.size
    private val cells = Array(height) { rows[it].toCharArray() }

    val playerStart: Pair<Int, Int>
    val gate: Pair<Int, Int>
    val nest: List<Pair<Int, Int>>
    var pollenLeft = 0
        private set

    init {
        var start = 0 to 0
        var g = 0 to 0
        val n = ArrayList<Pair<Int, Int>>()
        for (y in 0 until height) for (x in 0 until width) {
            when (cells[y][x]) {
                'P' -> start = x to y
                '-' -> g = x to y
                'n' -> n.add(x to y)
                '.', 'o' -> pollenLeft++
            }
        }
        playerStart = start
        gate = g
        nest = n
    }

    /** Case juste au-dessus de la porte : sortie du nid. */
    val exit: Pair<Int, Int> get() = gate.first to gate.second - 1

    fun wrapX(x: Int) = ((x % width) + width) % width

    fun cell(x: Int, y: Int): Char =
        if (y < 0 || y >= height) '#' else cells[y][wrapX(x)]

    fun isWall(x: Int, y: Int) = cell(x, y) == '#'

    fun passable(x: Int, y: Int, nestAccess: Boolean = false): Boolean =
        when (cell(x, y)) {
            '#' -> false
            '-', 'n' -> nestAccess
            else -> true
        }

    /** Ramasse ce qu'il y a sur la case ; renvoie '.', 'o' ou ' '. */
    fun collect(x: Int, y: Int): Char {
        val c = cells[y][wrapX(x)]
        if (c == '.' || c == 'o') {
            cells[y][wrapX(x)] = ' '
            pollenLeft--
            return c
        }
        return ' '
    }

    companion object {
        /** Moitié gauche (colonne centrale incluse), complétée par symétrie. */
        val HALF = arrayOf(
            "########",
            "#o.....#",
            "#.##.#.#",
            "#.......",
            "#.##.###",
            "#.##....",
            "#.##.##-",
            "     #nn",
            "#.##.###",
            "#.......",
            "#.#.##.#",
            "#.#.....",
            "#.#.##.#",
            "#......#",
            "###.##.#",
            "#...#..P",
            "#.###.##",
            "#o......",
            "########",
        )
    }
}
