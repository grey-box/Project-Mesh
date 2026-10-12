package com.greybox.projectmesh.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner radii (05 · Spacing, Size, Shape, Elevation). Access via MeshTheme.shapes.
//
// This is a plain M3 Shapes, not a MeshShapes data class, because MaterialTheme already
// carries a shapes slot. Passing it there means Card, Button and Dialog pick these up on
// their own, with no call site needing to ask.
//
// These five values are identical to Material 3 1.2.1's own ShapeTokens (verified against
// the library bytecode: 4/8/12/16/28), so handing them to MaterialTheme is a no-op at
// render time. That is deliberate. The scale is stated in our source so it is reviewable
// and greppable instead of being inherited invisibly from the library, and so a later
// change to it is a visible diff rather than a surprise.
//
// Buttons deliberately do NOT use `small`: they keep ButtonDefaults.shape (the M3
// stadium). The old TransparentButton was the only 8dp-cornered button in an app whose
// other buttons were 12dp, and that inconsistency gets removed rather than tokenised.
//
// Avatars and status dots use CircleShape directly. A circle is not a step on a radius
// scale, and naming it one would invite someone to "adjust" it.
val MeshShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // attachment chip inside a bubble
    small = RoundedCornerShape(8.dp),        // log rows
    medium = RoundedCornerShape(12.dp),      // DEFAULT: cards and message bubbles
    large = RoundedCornerShape(16.dp),       // sheets and dialogs
    extraLarge = RoundedCornerShape(28.dp),  // full-screen sheets
)
