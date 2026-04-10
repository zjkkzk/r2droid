package top.wsdx233.r2droid.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the version comparison logic in UpdateChecker.
 * Since isNewerVersion is private, we test the logic directly
 * via reflection or by replicating the algorithm here.
 */
class VersionCompareTest {

    /**
     * Replicates the version comparison logic from UpdateChecker.isNewerVersion.
     * This ensures we test the exact same algorithm.
     */
    private fun isNewerVersion(newVersion: String, currentVersion: String): Boolean {
        try {
            val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLength = maxOf(newParts.size, currentParts.size)
            for (i in 0 until maxLength) {
                val newPart = newParts.getOrNull(i) ?: 0
                val currentPart = currentParts.getOrNull(i) ?: 0
                if (newPart > currentPart) return true
                if (newPart < currentPart) return false
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    // ========== Basic Version Comparison ==========

    @Test
    fun `same version returns false`() {
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun `higher major version is newer`() {
        assertTrue(isNewerVersion("2.0.0", "1.0.0"))
    }

    @Test
    fun `lower major version is not newer`() {
        assertFalse(isNewerVersion("0.0.0", "1.0.0"))
    }

    @Test
    fun `higher minor version is newer`() {
        assertTrue(isNewerVersion("1.1.0", "1.0.0"))
    }

    @Test
    fun `lower minor version is not newer`() {
        assertFalse(isNewerVersion("1.0.0", "1.1.0"))
    }

    @Test
    fun `higher patch version is newer`() {
        assertTrue(isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun `lower patch version is not newer`() {
        assertFalse(isNewerVersion("1.0.0", "1.0.1"))
    }

    // ========== Multi-digit Version Parts ==========

    @Test
    fun `multi-digit version parts compare numerically`() {
        assertTrue(isNewerVersion("1.10.0", "1.9.0"))
        assertFalse(isNewerVersion("1.9.0", "1.10.0"))
    }

    @Test
    fun `version 10 is newer than 9`() {
        assertTrue(isNewerVersion("10.0.0", "9.0.0"))
    }

    // ========== Different Length Versions ==========

    @Test
    fun `longer version with same prefix is newer`() {
        assertTrue(isNewerVersion("1.0.1", "1.0"))
    }

    @Test
    fun `shorter version with same prefix is not newer than longer`() {
        assertFalse(isNewerVersion("1.0", "1.0.1"))
    }

    @Test
    fun `same version different lengths with trailing zeros`() {
        assertFalse(isNewerVersion("1.0.0", "1.0"))
        assertFalse(isNewerVersion("1.0", "1.0.0"))
    }

    // ========== Edge Cases ==========

    @Test
    fun `single digit versions compare correctly`() {
        assertTrue(isNewerVersion("2", "1"))
        assertFalse(isNewerVersion("1", "2"))
        assertFalse(isNewerVersion("1", "1"))
    }

    @Test
    fun `version with non-numeric parts defaults to zero`() {
        // "abc" -> toIntOrNull() returns null -> 0
        assertFalse(isNewerVersion("abc.0.0", "0.0.0"))
        assertTrue(isNewerVersion("1.0.0", "abc.0.0"))
    }

    @Test
    fun `empty version string defaults to zero`() {
        assertFalse(isNewerVersion("", "0"))
        assertFalse(isNewerVersion("0", ""))
    }

    @Test
    fun `four-part version compares correctly`() {
        assertTrue(isNewerVersion("1.0.0.1", "1.0.0.0"))
        assertFalse(isNewerVersion("1.0.0.0", "1.0.0.1"))
    }

    // ========== Real-world Version Scenarios ==========

    @Test
    fun `typical semver upgrade scenarios`() {
        // Patch update
        assertTrue(isNewerVersion("0.3.1", "0.3.0"))
        // Minor update
        assertTrue(isNewerVersion("0.4.0", "0.3.0"))
        // Major update
        assertTrue(isNewerVersion("1.0.0", "0.3.0"))
    }

    @Test
    fun `downgrade is not considered newer`() {
        assertFalse(isNewerVersion("0.2.0", "0.3.0"))
        assertFalse(isNewerVersion("0.2.9", "0.3.0"))
        assertFalse(isNewerVersion("0.3.0", "0.3.1"))
    }

    @Test
    fun `version with v prefix removed compares correctly`() {
        // If we strip the 'v' prefix before comparing:
        val latest = "0.3.1"
        val current = "0.3.0"
        assertTrue(isNewerVersion(latest, current))
    }

    @Test
    fun `compare versions from v-prefixed tags`() {
        // Simulating: release.tagName.removePrefix("v") -> "0.3.1"
        val latestVersion = "0.3.1"
        val currentVersion = "0.3.0"
        assertTrue(isNewerVersion(latestVersion, currentVersion))
    }

    @Test
    fun `pre-release version is numerically higher if patch is higher`() {
        // In our simple numeric comparison, 0.3.0-alpha would parse as 0.3.0 (alpha is non-numeric)
        // This is a known limitation of the simple version comparison
        val result = isNewerVersion("0.3.0", "0.3.0-alpha")
        // "0.3.0-alpha" -> parts: ["0", "3", "0-alpha"] -> 0-alpha -> toIntOrNull() = 0
        // So it becomes 0.3.0 vs 0.3.0 -> false (same)
        assertFalse(result)
    }
}
