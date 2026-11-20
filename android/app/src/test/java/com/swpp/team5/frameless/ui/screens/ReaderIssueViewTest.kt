package com.swpp.team5.frameless.ui.screens

import com.swpp.team5.frameless.data.FrameFront
import com.swpp.team5.frameless.data.FrameVoice
import com.swpp.team5.frameless.data.IssueOutlet
import org.junit.Assert.assertEquals
import org.junit.Test

/** specs/features/event-issue-view.md: the front line and the outlet names show stored words only. */
class ReaderIssueViewTest {
    @Test
    fun frontTextShowsThePositionTheFactOrTheSpeakersWords() {
        assertEquals("거부권 행사해야", frontText(FrameFront("position", label = "거부권 행사해야")))
        assertEquals("«교섭에 진전 없으면 파업 돌입»", frontText(FrameFront("fact", phrase = "교섭에 진전 없으면 파업 돌입")))
        assertEquals(
            "더불어민주당 «내란면죄부»  ·  국민의힘 «정치보복»",
            frontText(FrameFront("voices", voices = listOf(FrameVoice("더불어민주당", "내란면죄부"), FrameVoice("국민의힘", "정치보복"))))
        )
        assertEquals("«말»", frontText(FrameFront("voices", voices = listOf(FrameVoice("", "말")))))
    }

    @Test
    fun outletTextNamesTheQuotedSpeakerInParentheses() {
        assertEquals("MBC (더불어민주당)", outletText(IssueOutlet("m", "MBC", "더불어민주당")))
        assertEquals("연합뉴스", outletText(IssueOutlet("y", "연합뉴스", null)))
    }
}
