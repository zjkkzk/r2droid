package top.wsdx233.r2droid.core.data.model

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SavedProjectTest {

    // ========== JSON Serialization Round-trip Tests ==========

    @Test
    fun `SavedProject fromJson and toJson round-trip preserves all fields`() {
        val original = SavedProject(
            id = "test-uuid-123",
            name = "test_binary",
            binaryPath = "/data/local/tmp/test_binary",
            scriptPath = "/data/local/tmp/test_binary.r2",
            createdAt = 1700000000000L,
            lastModified = 1700003600000L,
            fileSize = 1048576L,
            archType = "x86_64",
            binType = "elf",
            analysisLevel = "aaa"
        )
        val json = original.toJson()
        val restored = SavedProject.fromJson(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.binaryPath, restored.binaryPath)
        assertEquals(original.scriptPath, restored.scriptPath)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.lastModified, restored.lastModified)
        assertEquals(original.fileSize, restored.fileSize)
        assertEquals(original.archType, restored.archType)
        assertEquals(original.binType, restored.binType)
        assertEquals(original.analysisLevel, restored.analysisLevel)
    }

    @Test
    fun `SavedProject fromJson handles missing optional fields`() {
        val json = JSONObject("""{"id": "abc", "name": "test"}""")
        val project = SavedProject.fromJson(json)
        assertEquals("abc", project.id)
        assertEquals("test", project.name)
        assertEquals("", project.binaryPath)
        assertEquals("", project.scriptPath)
        assertEquals(0L, project.createdAt)
        assertEquals(0L, project.lastModified)
        assertEquals(0L, project.fileSize)
        assertEquals("", project.archType)
        assertEquals("", project.binType)
        assertEquals("", project.analysisLevel)
    }

    @Test
    fun `SavedProject fromJson handles completely empty JSON`() {
        val json = JSONObject("{}")
        val project = SavedProject.fromJson(json)
        assertEquals("", project.id)
        assertEquals("", project.name)
    }

    @Test
    fun `SavedProject toJson produces valid JSON`() {
        val project = SavedProject(
            id = "uuid",
            name = "binary",
            binaryPath = "/path/binary",
            scriptPath = "/path/binary.r2",
            createdAt = 1000L,
            lastModified = 2000L,
            fileSize = 4096L,
            archType = "arm",
            binType = "elf",
            analysisLevel = "aaaa"
        )
        val json = project.toJson()
        assertEquals("uuid", json.getString("id"))
        assertEquals("binary", json.getString("name"))
        assertEquals("/path/binary", json.getString("binaryPath"))
        assertEquals(1000L, json.getLong("createdAt"))
        assertEquals(4096L, json.getLong("fileSize"))
        assertEquals("arm", json.getString("archType"))
    }

    // ========== File Size Formatting Tests ==========

    @Test
    fun `getFormattedFileSize formats bytes`() {
        val project = SavedProject(
            id = "", name = "", binaryPath = "", scriptPath = "",
            createdAt = 0, lastModified = 0, fileSize = 512L,
            archType = "", binType = ""
        )
        assertEquals("512 B", project.getFormattedFileSize())
    }

    @Test
    fun `getFormattedFileSize formats kilobytes`() {
        val project = SavedProject(
            id = "", name = "", binaryPath = "", scriptPath = "",
            createdAt = 0, lastModified = 0, fileSize = 1536L,
            archType = "", binType = ""
        )
        val result = project.getFormattedFileSize()
        assertTrue("Should format as KB", result.contains("KB"))
        assertTrue("Should be ~1.5 KB", result.startsWith("1.50"))
    }

    @Test
    fun `getFormattedFileSize formats megabytes`() {
        val project = SavedProject(
            id = "", name = "", binaryPath = "", scriptPath = "",
            createdAt = 0, lastModified = 0, fileSize = 1048576L,
            archType = "", binType = ""
        )
        val result = project.getFormattedFileSize()
        assertTrue("Should format as MB", result.contains("MB"))
        assertTrue("Should be ~1.00 MB", result.startsWith("1.00"))
    }

    @Test
    fun `getFormattedFileSize formats gigabytes`() {
        val project = SavedProject(
            id = "", name = "", binaryPath = "", scriptPath = "",
            createdAt = 0, lastModified = 0, fileSize = 1073741824L,
            archType = "", binType = ""
        )
        val result = project.getFormattedFileSize()
        assertTrue("Should format as GB", result.contains("GB"))
        assertTrue("Should be ~1.00 GB", result.startsWith("1.00"))
    }

    @Test
    fun `getFormattedFileSize handles zero bytes`() {
        val project = SavedProject(
            id = "", name = "", binaryPath = "", scriptPath = "",
            createdAt = 0, lastModified = 0, fileSize = 0L,
            archType = "", binType = ""
        )
        assertEquals("0 B", project.getFormattedFileSize())
    }

    // ========== Date Formatting Tests ==========

    @Test
    fun `getFormattedLastModified returns formatted date`() {
        val project = SavedProject(
            id = "", name = "", binaryPath = "", scriptPath = "",
            createdAt = 0, lastModified = 1700000000000L, fileSize = 0L,
            archType = "", binType = ""
        )
        val formatted = project.getFormattedLastModified()
        // Should contain year and time components
        assertTrue("Should contain year", formatted.contains("2023"))
        assertTrue("Should contain time separator", formatted.contains(":"))
    }
}
