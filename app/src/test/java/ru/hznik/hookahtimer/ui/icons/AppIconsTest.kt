package ru.hznik.hookahtimer.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIconsTest {
    @Test
    fun allMaterialIconPathsAreValid() {
        assertEquals("Add", AppIcons.Add.name)
        assertEquals("Check", AppIcons.Check.name)
        assertEquals("Edit", AppIcons.Edit.name)
        assertEquals("Fullscreen", AppIcons.Fullscreen.name)
        assertEquals("Fullscreen exit", AppIcons.FullscreenExit.name)
        assertEquals("Close", AppIcons.Close.name)
        assertEquals("Delete", AppIcons.Delete.name)
    }
}
