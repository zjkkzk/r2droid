package top.wsdx233.r2droid.util

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.wsdx233.r2droid.core.data.prefs.SettingsManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LogManagerTest {

    @Before
    fun setup() {
        // Initialize SettingsManager (required by LogManager.maxLogEntries)
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        SettingsManager.initialize(context)
        LogManager.clear()
    }

    @Test
    fun `log adds entry to logs`() {
        LogManager.log(LogType.INFO, "test message")
        val logs = LogManager.logs.value
        assertEquals(1, logs.size)
        assertEquals(LogType.INFO, logs[0].type)
        assertEquals("test message", logs[0].message)
    }

    @Test
    fun `log preserves insertion order`() {
        LogManager.log(LogType.INFO, "first")
        LogManager.log(LogType.COMMAND, "second")
        LogManager.log(LogType.OUTPUT, "third")
        val logs = LogManager.logs.value
        assertEquals(3, logs.size)
        assertEquals("first", logs[0].message)
        assertEquals("second", logs[1].message)
        assertEquals("third", logs[2].message)
    }

    @Test
    fun `log handles all log types`() {
        LogManager.log(LogType.COMMAND, "cmd")
        LogManager.log(LogType.OUTPUT, "out")
        LogManager.log(LogType.INFO, "info")
        LogManager.log(LogType.WARNING, "warn")
        LogManager.log(LogType.ERROR, "err")
        val logs = LogManager.logs.value
        assertEquals(5, logs.size)
        assertEquals(LogType.COMMAND, logs[0].type)
        assertEquals(LogType.OUTPUT, logs[1].type)
        assertEquals(LogType.INFO, logs[2].type)
        assertEquals(LogType.WARNING, logs[3].type)
        assertEquals(LogType.ERROR, logs[4].type)
    }

    @Test
    fun `clear removes all log entries`() {
        LogManager.log(LogType.INFO, "message1")
        LogManager.log(LogType.INFO, "message2")
        assertEquals(2, LogManager.logs.value.size)
        LogManager.clear()
        assertTrue(LogManager.logs.value.isEmpty())
    }

    @Test
    fun `log truncates messages exceeding max length`() {
        val longMessage = "A".repeat(3000)
        LogManager.log(LogType.INFO, longMessage)
        val logs = LogManager.logs.value
        assertEquals(1, logs.size)
        assertTrue("Message should be truncated", logs[0].message.length < longMessage.length)
        assertTrue("Truncated message should contain truncation indicator", logs[0].message.contains("truncated"))
    }

    @Test
    fun `log does not truncate short messages`() {
        val shortMessage = "Short message"
        LogManager.log(LogType.INFO, shortMessage)
        val logs = LogManager.logs.value
        assertEquals(shortMessage, logs[0].message)
    }

    @Test
    fun `log entries have unique IDs`() {
        LogManager.log(LogType.INFO, "msg1")
        LogManager.log(LogType.INFO, "msg2")
        val logs = LogManager.logs.value
        assertNotEquals("Log entries should have unique IDs", logs[0].id, logs[1].id)
    }

    @Test
    fun `log entries have valid timestamps`() {
        val before = System.currentTimeMillis()
        LogManager.log(LogType.INFO, "test")
        val after = System.currentTimeMillis()
        val logs = LogManager.logs.value
        assertTrue("Timestamp should be within time range", logs[0].timestamp in before..after)
    }

    @Test
    fun `log respects max entries limit`() {
        // Default maxLogEntries is 100
        for (i in 1..105) {
            LogManager.log(LogType.INFO, "msg $i")
        }
        val logs = LogManager.logs.value
        assertTrue("Should not exceed max log entries", logs.size <= 100)
        // Should keep the latest entries
        assertTrue("Should keep recent entries", logs.last().message.contains("msg 105"))
    }
}
