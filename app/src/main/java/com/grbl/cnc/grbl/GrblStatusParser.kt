package com.grbl.cnc.grbl

object GrblStatusParser {

    fun parse(line: String): GrblStatus? {

        if (!line.startsWith("<") || !line.endsWith(">")) return null

        return try {
            val clean = line.substring(1, line.length - 1)
            val parts = clean.split("|")

            val state = parts[0]

            var mpos = listOf(0.0, 0.0, 0.0)
            var wpos = listOf(0.0, 0.0, 0.0)
            var feed = 0
            var spindle = 0

            for (p in parts) {
                when {
                    p.startsWith("MPos:") ->
                        mpos = p.substring(5).split(",").map { it.toDouble() }

                    p.startsWith("WPos:") ->
                        wpos = p.substring(5).split(",").map { it.toDouble() }

                    p.startsWith("FS:") -> {
                        val fs = p.substring(3).split(",")
                        feed = fs[0].toInt()
                        spindle = fs[1].toInt()
                    }
                }
            }


            GrblStatus(
                state,
                mpos[0], mpos[1], mpos[2],
                wpos[0], wpos[1], wpos[2],
                feed, spindle
            )

        } catch (e: Exception) {
            null
        }
    }
}
