package io.github.dewfallapp.feature.takeoutimport.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.dewfallapp.core.data.takeout.TakeoutImportRepository
import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.TakeoutImportFailure
import io.github.dewfallapp.core.model.TakeoutImportResult
import io.github.dewfallapp.feature.takeoutimport.logic.ImportedChannelRow
import io.github.dewfallapp.feature.takeoutimport.logic.TakeoutImportIntent
import io.github.dewfallapp.feature.takeoutimport.logic.TakeoutImportIntent.FilePicked
import io.github.dewfallapp.feature.takeoutimport.logic.TakeoutImportState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

// Made-up files and channels: no real channel has this ID or name.
private const val FILE = "content://example.invalid/document/1"
private const val OTHER_FILE = "content://example.invalid/document/2"
private val BAKERY =
    Channel(ChannelId("UCtakeouttest00000000001"), "Made-up Bakery", emptyList(), "")

/** One import the fake repository got. It waits until the test completes its result. */
private class FakeImport(val file: String) {
    val result = CompletableDeferred<TakeoutImportResult>()
    var wasCancelled = false
}

/** A repository whose imports wait for the test, so a test decides when each one finishes. */
private class FakeRepository : TakeoutImportRepository {
    val imports = mutableListOf<FakeImport>()

    override suspend fun importFrom(file: String): TakeoutImportResult {
        val import = FakeImport(file).also { imports += it }
        try {
            return import.result.await()
        } catch (cancelled: CancellationException) {
            import.wasCancelled = true
            throw cancelled
        }
    }
}

/** A store that holds [viewModel], so the test can clear it as navigation does. */
private fun storeOf(viewModel: TakeoutImportViewModel): ViewModelStore {
    val store = ViewModelStore()
    ViewModelProvider.create(store, viewModelFactory { initializer { viewModel } })[
            TakeoutImportViewModel::class]
    return store
}

/**
 * Tests which imports the ViewModel starts, and that their results come back through the reducer.
 * The reducer's own tests cover the rest.
 */
// setMain, resetMain and UnconfinedTestDispatcher are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class TakeoutImportViewModelTest {
    private val repository = FakeRepository()

    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which local tests don't have. An unconfined
        // dispatcher starts each import at once, so the test sees it running right after dispatch.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a picked file is imported once, and its result shows`() = runTest {
        val viewModel = TakeoutImportViewModel(repository)

        viewModel.dispatch(FilePicked(FILE))
        assertEquals(TakeoutImportState.Reading, viewModel.state.value)
        assertEquals(listOf(FILE), repository.imports.map { it.file })
        repository.imports
            .single()
            .result
            .complete(TakeoutImportResult.Imported(listOf(BAKERY), added = 1, unreadableLines = 0))

        assertEquals(
            TakeoutImportState.Imported(
                listOf(ImportedChannelRow(BAKERY.id, "Made-up Bakery")),
                added = 1,
                alreadySubscribed = 0,
                unreadableLines = 0,
            ),
            viewModel.state.value,
        )
    }

    @Test
    fun `a second file picked while reading starts no second import`() = runTest {
        val viewModel = TakeoutImportViewModel(repository)

        viewModel.dispatch(FilePicked(FILE))
        viewModel.dispatch(FilePicked(OTHER_FILE))

        assertEquals(listOf(FILE), repository.imports.map { it.file })
    }

    @Test
    fun `after a failure another file starts a new import`() = runTest {
        val viewModel = TakeoutImportViewModel(repository)
        viewModel.dispatch(FilePicked(FILE))
        repository.imports
            .single()
            .result
            .complete(TakeoutImportResult.Failed(TakeoutImportFailure.NO_CHANNELS))
        assertEquals(
            TakeoutImportState.Failed(TakeoutImportFailure.NO_CHANNELS),
            viewModel.state.value,
        )

        viewModel.dispatch(FilePicked(OTHER_FILE))

        assertEquals(listOf(FILE, OTHER_FILE), repository.imports.map { it.file })
        assertEquals(TakeoutImportState.Reading, viewModel.state.value)
    }

    @Test
    fun `leaving the screen cancels the running import`() = runTest {
        val viewModel = TakeoutImportViewModel(repository)
        val store = storeOf(viewModel)
        viewModel.dispatch(FilePicked(FILE))

        store.clear()

        assertTrue(repository.imports.single().wasCancelled)
    }

    @Test
    fun `leaving cancels the running import before the ViewModel clears`() = runTest {
        val viewModel = TakeoutImportViewModel(repository)
        viewModel.dispatch(FilePicked(FILE))

        viewModel.dispatch(TakeoutImportIntent.Left)

        assertTrue(repository.imports.single().wasCancelled)
    }

    @Test
    fun `leaving with no import running changes nothing`() = runTest {
        val viewModel = TakeoutImportViewModel(repository)

        viewModel.dispatch(TakeoutImportIntent.Left)

        assertEquals(TakeoutImportState.Idle, viewModel.state.value)
        assertEquals(emptyList(), repository.imports)
    }
}
