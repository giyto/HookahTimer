package ru.hznik.hookahtimer.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object AppIcons {
    val Add: ImageVector by lazy {
        materialIcon("Add", "M19,13H13v6h-2v-6H5v-2h6V5h2v6h6v2z")
    }
    val Check: ImageVector by lazy {
        materialIcon("Check", "M9,16.17L4.83,12l-1.42,1.41L9,19 21,7l-1.41-1.41z")
    }
    val Edit: ImageVector by lazy {
        materialIcon(
            "Edit",
            "M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25z " +
                "M20.71,7.04c0.39,-0.39 0.39,-1.02 0,-1.41l-2.34,-2.34" +
                "c-0.39,-0.39 -1.02,-0.39 -1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z",
        )
    }
    val Fullscreen: ImageVector by lazy {
        materialIcon(
            "Fullscreen",
            "M7,14H5v5h5v-2H7v-3z M5,10h2V7h3V5H5v5z " +
                "M17,17h-3v2h5v-5h-2v3z M14,5v2h3v3h2V5h-5z",
        )
    }
    val FullscreenExit: ImageVector by lazy {
        materialIcon(
            "Fullscreen exit",
            "M5,16h3v3h2v-5H5v2z M8,8H5v2h5V5H8v3z " +
                "M14,19h2v-3h3v-2h-5v5z M16,8V5h-2v5h5V8h-3z",
        )
    }
    val Close: ImageVector by lazy {
        materialIcon(
            "Close",
            "M18.3,5.71 12,12l6.3,6.29 -1.41,1.42L10.59,13.41 4.29,19.71 " +
                "2.88,18.29 9.17,12 2.88,5.71 4.29,4.29 10.59,10.59 16.89,4.29z",
        )
    }
    val Delete: ImageVector by lazy {
        materialIcon(
            "Delete",
            "M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12z " +
                "M8,9h8v10H8V9z M15.5,4l-1,-1h-5l-1,1H5v2h14V4z",
        )
    }
}

private fun materialIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }.build()
