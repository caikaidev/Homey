package ian.dev.zaizai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppContextTest {
    @Test
    fun packageName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("ian.dev.zaizai", appContext.packageName)
    }
}
