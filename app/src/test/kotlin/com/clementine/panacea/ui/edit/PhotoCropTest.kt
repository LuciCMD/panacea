package com.clementine.panacea.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoCropTest {
    @Test
    fun aPortraitPhotoInATallerViewIsCroppedAtTheSides() {
        // 3000×4000 photo filling a 1080×2400 viewfinder: scaled by 0.6, so 1800 px of width shows.
        // The guide is 0.72 × 1080 = 777.6 px on screen, 1296 px in the photo.
        assertEquals(Square(852, 1352, 1296), PhotoCrop.guide(3000, 4000, 1080, 2400))
    }

    @Test
    fun aPhotoTheSameShapeAsTheViewScalesStraight() {
        assertEquals(Square(140, 140, 720), PhotoCrop.guide(1000, 1000, 500, 500, fraction = 0.72f))
    }

    @Test
    fun theCropNeverLeavesThePhoto() {
        assertEquals(Square(0, 0, 100), PhotoCrop.guide(100, 100, 1000, 1000, fraction = 2f))
    }
}
