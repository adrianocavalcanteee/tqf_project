package com.tqfapp.android

import com.tqfapp.android.business.RatingFormat
import com.tqfapp.android.data.model.Review
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RatingFormatTest {

    private fun review(rating: Int) =
        Review(id = "r$rating", serviceId = "s", providerId = "p", clientId = "c", rating = rating, comment = "", date = "")

    @Test
    fun summaryUsesOnlyGivenReviewsInPortugueseFormat() {
        assertEquals("4,5 (2 avaliações)", RatingFormat.summary(listOf(review(4), review(5))))
        assertEquals("5,0 (1 avaliação)", RatingFormat.summary(listOf(review(5))))
    }

    @Test
    fun noReviewsMeansEmptyStateNotFakeRating() {
        assertNull(RatingFormat.summary(emptyList()))
        assertNull(RatingFormat.average(emptyList()))
    }
}
