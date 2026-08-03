package org.cyclingcommons.scout.recording

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.cyclingcommons.scout.domain.PoiType
import org.cyclingcommons.scout.domain.QueuedTag
import org.cyclingcommons.scout.domain.RideSessionSnapshot
import org.cyclingcommons.scout.domain.TagTallies
import org.cyclingcommons.scout.domain.TimerState
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RideRecoveryInstrumentedTest {
    private lateinit var store: RideRecoveryStore
    private lateinit var ridesDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = RideRecoveryStore(context)
        store.clear()
        ridesDir = RideFiles.dir(context)
    }

    @Test
    fun saveAndLoadPendingRecovery() {
        val fit = File(ridesDir, "scout-recovery-test.fit")
        fit.writeText("partial")
        val session = RideSessionSnapshot(
            timer = TimerState.RUNNING,
            queuedTags = listOf(QueuedTag(PoiType.DANGER, 0)),
            tallies = TagTallies().snapshot(),
            elapsedMs = 60_000L,
            sampleCount = 12,
            carCount = 1,
            lastCarSpeedKph = 30,
        )
        store.save(fit.absolutePath, session)
        val pending = store.load()
        assertNotNull(pending)
        assert(pending!!.file.exists())
        assert(pending.session.sampleCount == 12L)
        store.clear()
        assertNull(store.load())
        fit.delete()
    }
}
