package org.example.project.util

import androidx.compose.ui.graphics.Color
import com.google.devrel.gmscore.tools.apk.arsc.Chunk
import com.google.devrel.gmscore.tools.apk.arsc.PackageChunk

val Chunk.color: Color
    get() = when (val chunk = this) {
        is PackageChunk -> Color.Blue
        else -> Color.Black
    }
