package com.watermelon.ui.screens

import com.watermelon.ui.R

enum class VideoRatio(val labelRes: Int, val ratio: Float?) {
    FILL(R.string.video_ratio_fill, null),
    ORIGINAL(R.string.video_ratio_original, null),
    RATIO_16_9(R.string.video_ratio_16_9, 16f / 9f),
    RATIO_4_3(R.string.video_ratio_4_3, 4f / 3f),
    RATIO_21_9(R.string.video_ratio_21_9, 21f / 9f)
}

enum class ScreenOrientation(val iconRes: Int, val labelRes: Int) {
    AUTO(R.drawable.ic_orientation_auto, R.string.orientation_auto),
    PORTRAIT(R.drawable.ic_orientation_portrait, R.string.orientation_portrait),
    LANDSCAPE(R.drawable.ic_orientation_landscape, R.string.orientation_landscape)
}
