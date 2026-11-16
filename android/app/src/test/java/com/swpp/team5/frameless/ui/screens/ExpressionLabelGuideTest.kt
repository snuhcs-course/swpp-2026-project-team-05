package com.swpp.team5.frameless.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Oracle: specs/testing/first-run-tutorial.spec.md (label guide). */
class ExpressionLabelGuideTest {
    @Test
    fun guideListsEveryRubricCategoryOnceWithTheChipLabel() {
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G"), ExpressionLabelExamples.map { it.code })
        ExpressionLabelExamples.forEach { example ->
            assertNotEquals("표현 특징", categoryLabel(example.code))
        }
    }
}
