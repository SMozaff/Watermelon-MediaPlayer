package com.watermelon.mediatools.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Notification-routing coverage (WM-403): the ongoing progress notification lands on Jobs
 * itself, a result notification lands on the exact finished item, and unrelated intents do
 * not navigate. Pure function — no Intent/Context needed on the JVM.
 */
class JobsDeepLinkTest {

    @Test
    fun `active outcome opens the Jobs surface itself`() {
        assertEquals(
            JobsDeepLink.Destination.Jobs,
            JobsDeepLink.destination(JobsDeepLink.ACTION_OPEN_JOBS, null),
        )
    }

    @Test
    fun `terminal outcome focuses the finished job`() {
        val destination = JobsDeepLink.destination(JobsDeepLink.ACTION_OPEN_JOBS, "job-123")

        assertTrue(destination is JobsDeepLink.Destination.JobsItem)
        assertEquals("job-123", (destination as JobsDeepLink.Destination.JobsItem).jobId)
    }

    @Test
    fun `a blank focus id degrades to the plain Jobs destination`() {
        assertEquals(
            JobsDeepLink.Destination.Jobs,
            JobsDeepLink.destination(JobsDeepLink.ACTION_OPEN_JOBS, "   "),
        )
    }

    @Test
    fun `unrelated actions never navigate`() {
        assertNull(JobsDeepLink.destination(null, "job-123"))
        assertNull(JobsDeepLink.destination("android.intent.action.MAIN", "job-123"))
    }
}
