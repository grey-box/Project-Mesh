package com.greybox.projectmesh.ui.theme

import androidx.annotation.StringRes
import com.greybox.projectmesh.R

// User text size, multiplied onto the system font scale in ProjectMeshTheme
enum class TextSizeOption(val multiplier: Float, @StringRes val labelRes: Int) {
    SMALL(0.85f, R.string.text_size_small),
    DEFAULT(1.0f, R.string.text_size_default),
    LARGE(1.15f, R.string.text_size_large),
    EXTRA_LARGE(1.3f, R.string.text_size_extra_large),
}
