package com.grbl.cnc.grbl

object GrblStatusParser {
    private var mpos = doubleArrayOf(0.0, 0.0, 0.0)
    private var wpos = doubleArrayOf(0.0, 0.0, 0.0)
    private var wco = doubleArrayOf(0.0, 0.0, 0.0)
    private var ovFeed = 100
    private var ovRapid = 100
    private var ovSpindle = 100

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
            var pin: String? = null

            var plannerAvailable = 16
            var rxAvailable = 0

            var flood = false
            var mist = false
            var spindleDirection = SpindleDirection.OFF

            /**var ovFeed = 100
            var ovRapid = 100
            var ovSpindle = 100**/

            /**var pinX = false
            var pinY = false
            var pinZ = false
            var pinProbe = false
            var pinDoor = false
            var pinHold = false
            var pinReset = false
            var pinStart = false**/

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
                    p.startsWith("F:") -> {
                        feed = p.substring(2).toIntOrNull() ?: feed
                    }
                    p.startsWith("S:") -> {
                        spindle = p.substring(2).toIntOrNull() ?: spindle
                    }
                    p.startsWith("SD:") -> {
                        spindleDirection = when (p.substring(3)) {
                            "CW" -> SpindleDirection.CW
                            "CCW" -> SpindleDirection.CCW
                            else -> SpindleDirection.OFF
                        }
                    }
                    p.startsWith("Pn:") -> {
                        pin = p.substring(3)

                        /**val s = pin ?: ""

                        pinX = s.contains("X")
                        pinY = s.contains("Y")
                        pinZ = s.contains("Z")
                        pinProbe = s.contains("P")
                        pinDoor = s.contains("D")
                        pinHold = s.contains("H")
                        pinReset = s.contains("R")
                        pinStart = s.contains("S")**/
                    }
                    p.startsWith("Bf:") -> {
                        val bf = p.substring(3).split(",")

                        plannerAvailable = bf.getOrNull(0)?.toIntOrNull() ?: plannerAvailable
                        rxAvailable = bf.getOrNull(1)?.toIntOrNull() ?:  rxAvailable
                    }
                    p.startsWith("Buf:") -> {
                        plannerAvailable = p.substring(4).toIntOrNull() ?: plannerAvailable
                    }

                    p.startsWith("RX:") -> {
                        rxAvailable = p.substring(3).toIntOrNull() ?: rxAvailable
                    }
                    p.startsWith("A:") -> {
                        val acc = p.substring(2)

                        flood = acc.contains("F")
                        mist = acc.contains("M")
                        spindleDirection = when {
                            acc.contains("S") -> SpindleDirection.CW
                            acc.contains("C") -> SpindleDirection.CCW
                            else -> SpindleDirection.OFF
                        }
                    }
                    p.startsWith("Ov:") -> {
                        val ov = p.substring(3).split(",")

                        ovFeed = ov.getOrNull(0)?.toIntOrNull() ?: ovFeed
                        ovRapid = ov.getOrNull(1)?.toIntOrNull() ?: ovRapid
                        ovSpindle = ov.getOrNull(2)?.toIntOrNull() ?: ovSpindle
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
                feed, spindle, pin,
                plannerAvailable,
                rxAvailable,
                flood,
                mist,
                spindleDirection,
                ovFeed,
                ovRapid,
                ovSpindle
            )

        } catch (_: Exception) {
            null
        }
    }
}
