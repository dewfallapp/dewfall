package io.github.dewfallapp.core.download

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.Operation
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** A future that is already done, with the operation's success, or failed with [error]. */
private class DoneFuture(private val error: Throwable?) :
    ListenableFuture<Operation.State.SUCCESS> {
    override fun addListener(listener: Runnable, executor: Executor) = executor.execute(listener)

    override fun cancel(mayInterruptIfRunning: Boolean) = false

    override fun isCancelled() = false

    override fun isDone() = true

    override fun get(): Operation.State.SUCCESS =
        if (error == null) Operation.SUCCESS else throw ExecutionException(error)

    override fun get(timeout: Long, unit: TimeUnit): Operation.State.SUCCESS = get()
}

/** An operation that ended, failed with [error] when it isn't null. */
private class EndedOperation(private val error: Throwable?) : Operation {
    override fun getState(): LiveData<Operation.State> = MutableLiveData()

    override fun getResult(): ListenableFuture<Operation.State.SUCCESS> = DoneFuture(error)
}

class EnqueuedTest {
    @Test
    fun `an operation that succeeds enqueued the work`() = runTest {
        assertTrue(EndedOperation(error = null).enqueued())
    }

    @Test
    fun `an operation that fails didn't, so the next start schedules again`() = runTest {
        assertFalse(EndedOperation(IllegalStateException("The database threw")).enqueued())
    }
}
