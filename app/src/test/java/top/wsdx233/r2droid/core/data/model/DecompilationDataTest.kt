package top.wsdx233.r2droid.core.data.model

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for DecompilationData parsing including CLexer integration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DecompilationDataTest {

    @Test
    fun `DecompilationData fromJson parses basic decompilation`() {
        val json = JSONObject("""
            {
                "code": "int main() { return 0; }",
                "annotations": []
            }
        """.trimIndent())
        val data = DecompilationData.fromJson(json)
        assertEquals("int main() { return 0; }", data.code)
        // Should have CLexer-generated annotations since no syntax_highlight in input
        assertTrue("Should have syntax annotations from CLexer", data.annotations.isNotEmpty())
    }

    @Test
    fun `DecompilationData fromJson preserves existing syntax annotations`() {
        val json = JSONObject("""
            {
                "code": "int x;",
                "annotations": [
                    {"start": 0, "end": 3, "type": "syntax_highlight", "syntax_highlight": "datatype"}
                ]
            }
        """.trimIndent())
        val data = DecompilationData.fromJson(json)
        // Should NOT add CLexer annotations since existing ones have syntax_highlight
        val syntaxAnns = data.annotations.filter { it.type == "syntax_highlight" && it.syntaxHighlight != null }
        // The existing annotation should be preserved
        assertTrue("Should have at least one annotation", syntaxAnns.isNotEmpty())
    }

    @Test
    fun `DecompilationData fromJson handles empty code`() {
        val json = JSONObject("""{"code": "", "annotations": []}""")
        val data = DecompilationData.fromJson(json)
        assertEquals("", data.code)
        assertTrue(data.annotations.isEmpty())
    }

    @Test
    fun `DecompilationData fromJson handles missing annotations`() {
        val json = JSONObject("""{"code": "int x = 0;"}""")
        val data = DecompilationData.fromJson(json)
        assertEquals("int x = 0;", data.code)
        // Should add CLexer annotations
        assertTrue(data.annotations.isNotEmpty())
    }

    @Test
    fun `DecompilationData fromPddj parses line-based decompilation`() {
        val json = JSONObject("""
            {
                "lines": [
                    {"str": "int main() {", "offset": 4194560},
                    {"str": "    return 0;"},
                    {"str": "}"}
                ]
            }
        """.trimIndent())
        val data = DecompilationData.fromPddj(json)
        assertTrue(data.code.contains("int main()"))
        assertTrue(data.code.contains("return 0"))
        // Should have offset annotations
        val offsetAnns = data.annotations.filter { it.offset > 0 }
        assertTrue("Should have offset annotations", offsetAnns.isNotEmpty())
    }

    @Test
    fun `DecompilationData fromPddj handles empty lines`() {
        val json = JSONObject("""{"lines": []}""")
        val data = DecompilationData.fromPddj(json)
        assertEquals("", data.code)
    }

    @Test
    fun `DecompilationData fromPddj handles missing lines`() {
        val json = JSONObject("{}")
        val data = DecompilationData.fromPddj(json)
        assertEquals("", data.code)
    }

    @Test
    fun `DecompilationData fromPddj joins lines with newlines`() {
        val json = JSONObject("""
            {
                "lines": [
                    {"str": "line1"},
                    {"str": "line2"},
                    {"str": "line3"}
                ]
            }
        """.trimIndent())
        val data = DecompilationData.fromPddj(json)
        assertEquals("line1\nline2\nline3", data.code)
    }
}
