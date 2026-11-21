package com.swpp.team5.frameless.ui.viewmodel

import com.swpp.team5.frameless.data.TutorialStore
import com.swpp.team5.frameless.ui.components.coachTooltipGoesBelow
import com.swpp.team5.frameless.ui.components.safeCoachPadding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Oracle: specs/testing/first-run-tutorial.spec.md */
class TutorialViewModelTest {
    private class FakeTutorialStore(
        override var introSeen: Boolean = false,
        override var readerCoachSeen: Boolean = false
    ) : TutorialStore

    @Test
    fun freshInstallShowsIntroAndFinishingItPersists() {
        val store = FakeTutorialStore()
        val viewModel = TutorialViewModel(store)

        assertTrue(viewModel.showIntro)
        viewModel.finishIntro()

        assertFalse(viewModel.showIntro)
        assertTrue(store.introSeen)
        assertFalse(TutorialViewModel(store).showIntro)
    }

    @Test
    fun replayShowsIntroAgainAndRearmsTheReaderCoach() {
        val store = FakeTutorialStore(introSeen = true, readerCoachSeen = true)
        val viewModel = TutorialViewModel(store)

        viewModel.replay()
        assertTrue(viewModel.showIntro)
        assertFalse(store.readerCoachSeen)

        viewModel.finishIntro()
        viewModel.onReaderEligible()
        assertEquals(ReaderCoachStep.TAP_FINDING, viewModel.readerStep)
    }

    @Test
    fun readerCoachWaitsUntilIntroIsFinished() {
        val viewModel = TutorialViewModel(FakeTutorialStore())

        viewModel.onReaderEligible()
        assertNull(viewModel.readerStep)

        viewModel.finishIntro()
        viewModel.onReaderEligible()
        assertEquals(ReaderCoachStep.TAP_FINDING, viewModel.readerStep)
    }

    @Test
    fun fullReaderCoachWithComparisonsAdvancesThroughThreeStepsAndPersists() {
        val store = FakeTutorialStore(introSeen = true)
        val viewModel = TutorialViewModel(store)

        viewModel.onReaderEligible()
        viewModel.onFindingOpened()
        assertEquals(ReaderCoachStep.ANNOTATION_DOCK, viewModel.readerStep)
        viewModel.onAnnotationNext(hasComparisons = true)
        assertEquals(ReaderCoachStep.COMPARISON, viewModel.readerStep)
        assertFalse(store.readerCoachSeen)

        viewModel.finishReaderCoach()
        assertNull(viewModel.readerStep)
        assertTrue(store.readerCoachSeen)
    }

    @Test
    fun findingWithoutComparisonsEndsCoachAtAnnotationDock() {
        val store = FakeTutorialStore(introSeen = true)
        val viewModel = TutorialViewModel(store)

        viewModel.onReaderEligible()
        viewModel.onFindingOpened()
        viewModel.onAnnotationNext(hasComparisons = false)

        assertNull(viewModel.readerStep)
        assertTrue(store.readerCoachSeen)
    }

    @Test
    fun skippingAtFirstStepIsRememberedAndCoachDoesNotRestart() {
        val store = FakeTutorialStore(introSeen = true)
        val viewModel = TutorialViewModel(store)

        viewModel.onReaderEligible()
        viewModel.finishReaderCoach()
        viewModel.onReaderEligible()

        assertNull(viewModel.readerStep)
        assertTrue(store.readerCoachSeen)
        TutorialViewModel(store).also {
            it.onReaderEligible()
            assertNull(it.readerStep)
        }
    }

    @Test
    fun outOfOrderEventsDoNotSkipSteps() {
        val viewModel = TutorialViewModel(FakeTutorialStore(introSeen = true))

        viewModel.onFindingOpened()
        viewModel.onAnnotationNext(hasComparisons = true)
        assertNull(viewModel.readerStep)

        viewModel.onReaderEligible()
        viewModel.onAnnotationNext(hasComparisons = true)
        assertEquals(ReaderCoachStep.TAP_FINDING, viewModel.readerStep)
    }

    @Test
    fun dismissingWithoutAnActiveCoachDoesNotMarkItSeen() {
        val store = FakeTutorialStore(introSeen = true)
        val viewModel = TutorialViewModel(store)

        viewModel.finishReaderCoach()

        assertFalse(store.readerCoachSeen)
        viewModel.onReaderEligible()
        assertEquals(ReaderCoachStep.TAP_FINDING, viewModel.readerStep)
    }

    @Test
    fun tooltipSitsBelowSpotlightInUpperHalfAndAboveInLowerHalf() {
        assertTrue(coachTooltipGoesBelow(spotlightCenterY = 200f, containerHeight = 800f))
        assertFalse(coachTooltipGoesBelow(spotlightCenterY = 600f, containerHeight = 800f))
        assertFalse(coachTooltipGoesBelow(spotlightCenterY = 400f, containerHeight = 800f))
        assertTrue(coachTooltipGoesBelow(spotlightCenterY = 10f, containerHeight = 0f))
    }

    @Test
    fun tooltipPaddingNeverPassesNegativeCoordinatesToCompose() {
        assertEquals(0f, safeCoachPadding(-24f))
        assertEquals(18f, safeCoachPadding(18f))
    }
}
