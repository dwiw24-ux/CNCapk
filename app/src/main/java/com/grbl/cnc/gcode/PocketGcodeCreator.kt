package com.grbl.cnc.gcode

class PocketGcodeCreator {

    fun Double.g(): String =
        String.format(java.util.Locale.US, "%.3f", this)

    fun rectPocket(
        x : Double,
        y : Double,
        width : Double,
        height : Double,
        depth : Double,
        tool : Double,
        stepDown: Double,
        rampLength: Double,
        feedrate: Int,
        plungerate: Int,
        safeZ: Double = 10.000
    ): List<String> {

        val g = mutableListOf<String>()
        val stepOver = tool * 0.4
        var z = 0.000

        g += "T1M6"
        g += "G17"
        g += "G0 Z${safeZ.g()}"

        while (z > -depth) {

            val targetZ = (z - stepDown).coerceAtLeast(-depth)
            val rampZ = targetZ + (stepDown / 2)

            // 👉 POSISI AWAL RAMP
            g += "G0 X${x.g()} Y${y.g()} S12000 M3"
            g += "G1 Z${z.g()} F$plungerate"

            // 👉 RAMP MASUK
            g += "G1 X${rampLength.g()} Z${rampZ.g()}"
            g += "G1 X${x.g()} Z${targetZ.g()}"

            z = targetZ

            // 👉 POCKET RASTER
            var yy = y
            val yn = y + height
            var dir = true

            while (yy <= yn) {
                val xx = if (dir) x + width else x
                g += "G1 X${xx.g()} F$feedrate"
                yy += stepOver
                if (yy <= y + height) {
                    g += "G1 Y${yy.g()}"
                }
                dir = !dir
            }

            // CEK APAKAH SUDAH DEPTH AKHIR
            if (z <= -depth) {
                g += "G0 Z${safeZ.g()}"
                g += "G0 X0.000 Y0.000"
                g += "M30"
                break
            } else {
                // lanjut layer berikutnya
                g += "G0 Z${safeZ.g()}"
            }
        }
        return g
    }
}