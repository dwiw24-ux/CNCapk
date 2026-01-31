package com.grbl.cnc.grbl

object GrblStatusParser {
    private var mpos = doubleArrayOf(0.0, 0.0, 0.0)
    private var wpos = doubleArrayOf(0.0, 0.0, 0.0)
    private var wco = doubleArrayOf(0.0, 0.0, 0.0)
    fun parse(line: String): GrblStatus? {

        if (!line.startsWith("<") || !line.endsWith(">")) return null

        return try {
            val clean = line.substring(1, line.length - 1)
            val parts = clean.split("|")

            val state = parts[0]

            var hasMPos = false
            var hasWPos = false
            var feed = 0
            var spindle = 0

            for (p in parts) {
                when {
                    p.startsWith("MPos:") -> {
                        val v = p.substring(5).split(",")
                        mpos[0] = v[0].toDouble()
                        mpos[1] = v[1].toDouble()
                        mpos[2] = v[2].toDouble()
                        hasMPos = true
                    }

                    p.startsWith("WPos:") -> {
                        val v = p.substring(5).split(",")
                        wpos[0] = v[0].toDouble()
                        wpos[1] = v[1].toDouble()
                        wpos[2] = v[2].toDouble()
                        hasWPos = true
                    }

                    p.startsWith("WCO:") -> {
                        val v = p.substring(4).split(",")
                        wco[0] = v[0].toDouble()
                        wco[1] = v[1].toDouble()
                        wco[2] = v[2].toDouble()
                    }
                    p.startsWith("FS:") -> {
                        val fs = p.substring(3).split(",")
                        feed = fs[0].toInt()
                        spindle = fs[1].toInt()
                    }
                }
            }

            if (hasMPos && !hasWPos) {
                wpos[0] = mpos[0] - wco[0]
                wpos[1] = mpos[1] - wco[1]
                wpos[2] = mpos[2] - wco[2]
            }

            if (hasWPos && !hasMPos) {
                mpos[0] = wpos[0] + wco[0]
                mpos[1] = wpos[1] + wco[1]
                mpos[2] = wpos[2] + wco[2]
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
