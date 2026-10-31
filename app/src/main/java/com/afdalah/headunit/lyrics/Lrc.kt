package com.afdalah.headunit.lyrics

/** One lyric line; [timeMs] is -1 for unsynced (plain) lyrics. */
data class LyricLine(val timeMs: Long, val text: String)

object Lrc {
    private val tag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** Parses LRC ("[mm:ss.xx] text", several tags per line allowed), sorted by time. */
    fun parse(lrc: String): List<LyricLine> = lrc.lineSequence().flatMap { raw ->
        val times = tag.findAll(raw).toList()
        if (times.isEmpty()) return@flatMap emptySequence()
        val text = raw.substring(times.last().range.last + 1).trim()
        times.asSequence().map { m ->
            val (min, sec, frac) = m.destructured
            val fracMs = when (frac.length) { 0 -> 0; 1 -> frac.toInt() * 100; 2 -> frac.toInt() * 10; else -> frac.take(3).toInt() }
            LyricLine(min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs, text)
        }
    }.sortedBy { it.timeMs }.toList()

    fun plain(text: String): List<LyricLine> = text.lines().map { LyricLine(-1, it.trim()) }

    /** Index of the line being sung at [positionMs], or -1 before the first line. */
    fun indexAt(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || lines[0].timeMs < 0) return -1
        var lo = 0; var hi = lines.lastIndex; var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }

    /** "Song - Remastered 2011", "Song (feat. X)" -> "Song". */
    fun cleanTitle(title: String): String = title
        .replace(Regex("""\s*[(\[](feat\.?|ft\.?|with|from|remaster)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+-\s+.*(remaster|version|edit|live|mix|mono|stereo).*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    /** "Enrique Iglesias, Whitney Houston" -> "Enrique Iglesias". */
    fun firstArtist(artist: String): String = artist.split(",", " & ", " feat", " x ", ";").first().trim()
}
