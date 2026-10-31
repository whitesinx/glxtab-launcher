package com.afdalah.headunit

import com.afdalah.headunit.lyrics.Lrc
import org.junit.Assert.assertEquals
import org.junit.Test

class LrcTest {
    @Test fun parsesTagsAndSorts() {
        val lines = Lrc.parse("[00:12.50] Second\n[ar:Someone]\n[00:01.00][01:02.345] Chorus\n")
        assertEquals(listOf(1_000L, 12_500L, 62_345L), lines.map { it.timeMs })
        assertEquals("Chorus", lines[0].text)
    }

    @Test fun indexAtFindsCurrentLine() {
        val lines = Lrc.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c")
        assertEquals(-1, Lrc.indexAt(lines, 500))
        assertEquals(0, Lrc.indexAt(lines, 4_999))
        assertEquals(1, Lrc.indexAt(lines, 5_000))
        assertEquals(2, Lrc.indexAt(lines, 60_000))
    }

    @Test fun cleansTitleAndArtist() {
        assertEquals("Hey Jude", Lrc.cleanTitle("Hey Jude - Remastered 2015"))
        assertEquals("Song", Lrc.cleanTitle("Song (feat. Someone)"))
        assertEquals("Enrique Iglesias", Lrc.firstArtist("Enrique Iglesias, Whitney Houston"))
    }
}
