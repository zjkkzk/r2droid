package top.wsdx233.r2droid.feature.ai.data

import org.junit.Assert.*
import org.junit.Test

class R2ActionExecutorTest {

    private val executor = R2ActionExecutor()

    // ========== R2 Command Parsing Tests ==========

    @Test
    fun `parseResponse extracts single R2 command`() {
        val text = "Let me analyze this. [[afl]] Here are the functions."
        val result = executor.parseResponse(text)
        assertEquals(1, result.actions.size)
        assertEquals(ActionType.R2Command, result.actions[0].type)
        assertEquals("afl", result.actions[0].content)
    }

    @Test
    fun `parseResponse extracts multiple R2 commands`() {
        val text = "[[aaa]] then [[afl]] and [[pdf @ main]]"
        val result = executor.parseResponse(text)
        assertEquals(3, result.actions.size)
        assertEquals("aaa", result.actions[0].content)
        assertEquals("afl", result.actions[1].content)
        assertEquals("pdf @ main", result.actions[2].content)
    }

    @Test
    fun `parseResponse handles R2 command with complex arguments`() {
        val text = "[[s 0x080484d0; pdf]]"
        val result = executor.parseResponse(text)
        assertEquals(1, result.actions.size)
        assertEquals("s 0x080484d0; pdf", result.actions[0].content)
    }

    @Test
    fun `parseResponse ignores ask command`() {
        val text = "I need more info. [[ask]] Let me know."
        val result = executor.parseResponse(text)
        assertEquals(0, result.actions.size)
        assertTrue(result.hasAsk)
    }

    @Test
    fun `parseResponse separates ask from other commands`() {
        val text = "[[aaa]] [[ask]] [[afl]]"
        val result = executor.parseResponse(text)
        assertEquals(2, result.actions.size)
        assertEquals("aaa", result.actions[0].content)
        assertEquals("afl", result.actions[1].content)
        assertTrue(result.hasAsk)
    }

    // ========== JavaScript Parsing Tests ==========

    @Test
    fun `parseResponse extracts JavaScript block`() {
        val text = "Let me run some code: <js>console.log('hello')</js> done."
        val result = executor.parseResponse(text)
        assertEquals(1, result.actions.size)
        assertEquals(ActionType.JavaScript, result.actions[0].type)
        assertEquals("console.log('hello')", result.actions[0].content)
    }

    @Test
    fun `parseResponse handles multiline JavaScript`() {
        val text = "<js>\nvar x = 1;\nconsole.log(x);\n</js>"
        val result = executor.parseResponse(text)
        assertEquals(1, result.actions.size)
        assertEquals(ActionType.JavaScript, result.actions[0].type)
        assertTrue(result.actions[0].content.contains("var x = 1"))
        assertTrue(result.actions[0].content.contains("console.log(x)"))
    }

    // ========== Frida Script Parsing Tests ==========

    @Test
    fun `parseResponse extracts Frida script block`() {
        val text = "Hook this: <frida>Interceptor.attach(ptr, {onEnter: function(args) {}});</frida>"
        val result = executor.parseResponse(text)
        assertEquals(1, result.actions.size)
        assertEquals(ActionType.FridaScript, result.actions[0].type)
        assertTrue(result.actions[0].content.contains("Interceptor.attach"))
    }

    @Test
    fun `parseResponse handles multiline Frida script`() {
        val text = "<frida>\nJava.perform(function() {\n  // hook code\n});\n</frida>"
        val result = executor.parseResponse(text)
        assertEquals(1, result.actions.size)
        assertEquals(ActionType.FridaScript, result.actions[0].type)
        assertTrue(result.actions[0].content.contains("Java.perform"))
    }

    // ========== Mixed Action Type Tests ==========

    @Test
    fun `parseResponse handles mixed action types`() {
        val text = "First [[aaa]] then <js>r2.cmd('afl')</js> and <frida>Interceptor.attach()</frida>"
        val result = executor.parseResponse(text)
        assertEquals(3, result.actions.size)
        assertEquals(ActionType.R2Command, result.actions[0].type)
        assertEquals(ActionType.JavaScript, result.actions[1].type)
        assertEquals(ActionType.FridaScript, result.actions[2].type)
    }

    @Test
    fun `parseResponse orders actions by position in text`() {
        val text = "<frida>hook()</frida> then [[afl]] then <js>code()</js>"
        val result = executor.parseResponse(text)
        assertEquals(3, result.actions.size)
        assertEquals(ActionType.FridaScript, result.actions[0].type)
        assertEquals(ActionType.R2Command, result.actions[1].type)
        assertEquals(ActionType.JavaScript, result.actions[2].type)
    }

    // ========== End Marker Tests ==========

    @Test
    fun `parseResponse detects end marker`() {
        val text = "Here is the answer. [end]"
        val result = executor.parseResponse(text)
        assertTrue(result.isComplete)
    }

    @Test
    fun `parseResponse detects end marker case insensitive`() {
        val text = "Done. [END]"
        val result = executor.parseResponse(text)
        assertTrue(result.isComplete)
    }

    @Test
    fun `parseResponse no end marker means not complete`() {
        val text = "I'm still thinking about [[aaa]]"
        val result = executor.parseResponse(text)
        assertFalse(result.isComplete)
    }

    // ========== Edge Case Tests ==========

    @Test
    fun `parseResponse handles empty string`() {
        val result = executor.parseResponse("")
        assertTrue(result.actions.isEmpty())
        assertFalse(result.hasAsk)
        assertFalse(result.isComplete)
    }

    @Test
    fun `parseResponse handles plain text with no actions`() {
        val text = "This is just regular text with no commands."
        val result = executor.parseResponse(text)
        assertTrue(result.actions.isEmpty())
        assertFalse(result.hasAsk)
        assertFalse(result.isComplete)
    }

    @Test
    fun `parseResponse handles incomplete markers gracefully`() {
        val text = "This has [[ but no closing brackets"
        val result = executor.parseResponse(text)
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun `parseResponse handles empty R2 command`() {
        val text = " [[]] "
        val result = executor.parseResponse(text)
        // Empty command should still be parsed but content is empty
        val r2Actions = result.actions.filter { it.type == ActionType.R2Command }
        // The regex requires at least one character between [[ and ]], so this should not match
        assertTrue("Empty [[ ]] should not produce an action", r2Actions.isEmpty())
    }

    @Test
    fun `parseResponse handles nested brackets`() {
        val text = "[[afl]] some text [[pdf @ sym.main]]"
        val result = executor.parseResponse(text)
        assertEquals(2, result.actions.size)
        assertEquals("afl", result.actions[0].content)
        assertEquals("pdf @ sym.main", result.actions[1].content)
    }

    @Test
    fun `parseResponse trims whitespace from command content`() {
        val text = "[[  afl  ]]"
        val result = executor.parseResponse(text)
        assertEquals(1, result.actions.size)
        assertEquals("afl", result.actions[0].content)
    }

    @Test
    fun `parseResponse handles R2 command with address`() {
        val text = "[[s 0x00401234; pdf]]"
        val result = executor.parseResponse(text)
        assertEquals(1, result.actions.size)
        assertEquals("s 0x00401234; pdf", result.actions[0].content)
    }

    @Test
    fun `parseResponse handles combined ask and end`() {
        val text = "[[aaa]] [[ask]] waiting for response [end]"
        val result = executor.parseResponse(text)
        assertTrue(result.hasAsk)
        assertTrue(result.isComplete)
        assertEquals(1, result.actions.size) // only aaa, not ask
    }

    @Test
    fun `parseResponse startIndex reflects actual position`() {
        val text = "prefix [[aaa]] suffix"
        val result = executor.parseResponse(text)
        assertEquals(1, result.actions.size)
        assertEquals(7, result.actions[0].startIndex) // position of [[
    }

    @Test
    fun `parseResponse handles JavaScript with special characters`() {
        val text = "<js>var obj = {key: \"value\"}; console.log(obj);</js>"
        val result = executor.parseResponse(text)
        assertEquals(1, result.actions.size)
        assertEquals(ActionType.JavaScript, result.actions[0].type)
        assertTrue(result.actions[0].content.contains("var obj"))
    }

    @Test
    fun `parseResponse handles Frida script with Java perform`() {
        val text = "<frida>Java.perform(function() { var c = Java.use('com.test.Class'); c.method.implementation = function() {} });</frida>"
        val result = executor.parseResponse(text)
        assertEquals(1, result.actions.size)
        assertEquals(ActionType.FridaScript, result.actions[0].type)
        assertTrue(result.actions[0].content.contains("Java.perform"))
    }
}
