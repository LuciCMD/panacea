package com.clementine.panacea.ui.edit

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Square(val left: Int, val top: Int, val size: Int)

/** Where the on-screen guide square falls in the photo the camera took. Pure, so it can be tested. */
object PhotoCrop {
    /** The guide's side as a share of the viewfinder's shorter side. */
    const val GUIDE = 0.72f

    /**
     * The viewfinder fills its [viewW] × [viewH] box from a centred [imageW] × [imageH] image, cropping
     * whatever overflows. The guide is a centred square, so the crop is too.
     */
    fun guide(imageW: Int, imageH: Int, viewW: Int, viewH: Int, fraction: Float = GUIDE): Square {
        val scale = max(viewW.toFloat() / imageW, viewH.toFloat() / imageH)
        val side = (fraction * min(viewW, viewH) / scale).roundToInt().coerceIn(1, min(imageW, imageH))
        return Square((imageW - side) / 2, (imageH - side) / 2, side)
    }
}
