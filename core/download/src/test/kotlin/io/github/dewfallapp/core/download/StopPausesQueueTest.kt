package io.github.dewfallapp.core.download

import android.app.job.JobParameters
import androidx.work.WorkInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StopPausesQueueTest {
    @Test
    fun `WorkManager's stop by the user has the same value as JobScheduler's`() {
        kotlin.test.assertEquals(
            JobParameters.STOP_REASON_USER,
            androidx.work.WorkInfo.STOP_REASON_USER,
        )
    }

    @Test
    fun `a stop by the user pauses the queue`() {
        assertTrue(isStopByUser(JobParameters.STOP_REASON_USER))
    }

    @Test
    fun `other stops leave it to run again`() {
        for (reason in
            listOf(
                JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY,
                JobParameters.STOP_REASON_TIMEOUT,
                JobParameters.STOP_REASON_QUOTA,
                JobParameters.STOP_REASON_SYSTEM_PROCESSING,
                JobParameters.STOP_REASON_UNDEFINED,
            )) {
            assertFalse(isStopByUser(reason))
        }
    }
}
