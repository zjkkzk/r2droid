package top.wsdx233.r2droid.core.data.parser

import org.junit.Assert.*
import org.junit.Test
import top.wsdx233.r2droid.core.data.model.DecompilationAnnotation

class CLexerTest {

    // ========== Keyword Tests ==========

    @Test
    fun `tokenize recognizes if keyword`() {
        val annotations = CLexer.tokenize("if (x) {}")
        val keywordAnn = annotations.find { it.syntaxHighlight == "keyword" && it.type == "syntax_highlight" }
        assertNotNull("Should find keyword annotation for 'if'", keywordAnn)
        assertEquals("if", "if (x) {}".substring(keywordAnn!!.start, keywordAnn.end))
    }

    @Test
    fun `tokenize recognizes else keyword`() {
        val annotations = CLexer.tokenize("} else {")
        val keywordAnn = annotations.find { it.syntaxHighlight == "keyword" }
        assertNotNull(keywordAnn)
        assertEquals("else", "} else {".substring(keywordAnn!!.start, keywordAnn.end))
    }

    @Test
    fun `tokenize recognizes return keyword`() {
        val annotations = CLexer.tokenize("return 0;")
        val keywordAnn = annotations.find { it.syntaxHighlight == "keyword" }
        assertNotNull(keywordAnn)
        assertEquals("return", "return 0;".substring(keywordAnn!!.start, keywordAnn.end))
    }

    @Test
    fun `tokenize recognizes while keyword`() {
        val annotations = CLexer.tokenize("while (1) {}")
        val keywordAnn = annotations.find { it.syntaxHighlight == "keyword" }
        assertNotNull(keywordAnn)
        assertEquals("while", "while (1) {}".substring(keywordAnn!!.start, keywordAnn.end))
    }

    @Test
    fun `tokenize recognizes for keyword`() {
        val annotations = CLexer.tokenize("for (i = 0; i < 10; i++) {}")
        val keywordAnn = annotations.find { it.syntaxHighlight == "keyword" }
        assertNotNull(keywordAnn)
        assertEquals("for", "for (i = 0; i < 10; i++) {}".substring(keywordAnn!!.start, keywordAnn.end))
    }

    @Test
    fun `tokenize recognizes NULL keyword`() {
        val annotations = CLexer.tokenize("ptr = NULL;")
        val keywordAnn = annotations.find { it.syntaxHighlight == "keyword" }
        assertNotNull(keywordAnn)
        assertEquals("NULL", "ptr = NULL;".substring(keywordAnn!!.start, keywordAnn.end))
    }

    @Test
    fun `tokenize recognizes break and continue keywords`() {
        val code = "break; continue;"
        val annotations = CLexer.tokenize(code)
        val keywords = annotations.filter { it.syntaxHighlight == "keyword" }.map { code.substring(it.start, it.end) }
        assertTrue("Should contain 'break'", keywords.contains("break"))
        assertTrue("Should contain 'continue'", keywords.contains("continue"))
    }

    // ========== Datatype Tests ==========

    @Test
    fun `tokenize recognizes int datatype`() {
        val annotations = CLexer.tokenize("int x = 0;")
        val dtAnn = annotations.find { it.syntaxHighlight == "datatype" }
        assertNotNull(dtAnn)
        assertEquals("int", "int x = 0;".substring(dtAnn!!.start, dtAnn.end))
    }

    @Test
    fun `tokenize recognizes void datatype`() {
        val annotations = CLexer.tokenize("void func() {}")
        val dtAnn = annotations.find { it.syntaxHighlight == "datatype" }
        assertNotNull(dtAnn)
        assertEquals("void", "void func() {}".substring(dtAnn!!.start, dtAnn.end))
    }

    @Test
    fun `tokenize recognizes size_t datatype`() {
        val annotations = CLexer.tokenize("size_t len = 0;")
        val dtAnn = annotations.find { it.syntaxHighlight == "datatype" }
        assertNotNull(dtAnn)
        assertEquals("size_t", "size_t len = 0;".substring(dtAnn!!.start, dtAnn.end))
    }

    @Test
    fun `tokenize recognizes uint32_t datatype`() {
        val annotations = CLexer.tokenize("uint32_t val;")
        val dtAnn = annotations.find { it.syntaxHighlight == "datatype" }
        assertNotNull(dtAnn)
        assertEquals("uint32_t", "uint32_t val;".substring(dtAnn!!.start, dtAnn.end))
    }

    @Test
    fun `tokenize recognizes Windows types like DWORD and HANDLE`() {
        val code = "DWORD val; HANDLE h;"
        val annotations = CLexer.tokenize(code)
        val datatypes = annotations.filter { it.syntaxHighlight == "datatype" }.map { code.substring(it.start, it.end) }
        assertTrue("Should contain 'DWORD'", datatypes.contains("DWORD"))
        assertTrue("Should contain 'HANDLE'", datatypes.contains("HANDLE"))
    }

    // ========== String Literal Tests ==========

    @Test
    fun `tokenize recognizes double-quoted string`() {
        val code = """printf("hello world");"""
        val annotations = CLexer.tokenize(code)
        val stringAnn = annotations.find { it.syntaxHighlight == "string" }
        assertNotNull(stringAnn)
        assertEquals("\"hello world\"", code.substring(stringAnn!!.start, stringAnn.end))
    }

    @Test
    fun `tokenize recognizes single-quoted char`() {
        val code = "char c = 'x';"
        val annotations = CLexer.tokenize(code)
        val stringAnn = annotations.find { it.syntaxHighlight == "string" && code.substring(it.start, it.end).contains("'") }
        assertNotNull(stringAnn)
    }

    @Test
    fun `tokenize handles escaped quotes in strings`() {
        val code = """printf("say \"hi\"");"""
        val annotations = CLexer.tokenize(code)
        val stringAnn = annotations.find { it.syntaxHighlight == "string" }
        assertNotNull(stringAnn)
        // The string should include the escaped quotes
        val str = code.substring(stringAnn!!.start, stringAnn.end)
        assertTrue("String should start with quote", str.startsWith("\""))
    }

    @Test
    fun `tokenize handles empty string`() {
        val code = """char *s = "";"""
        val annotations = CLexer.tokenize(code)
        val stringAnn = annotations.find { it.syntaxHighlight == "string" }
        assertNotNull(stringAnn)
        assertEquals("\"\"", code.substring(stringAnn!!.start, stringAnn.end))
    }

    // ========== Comment Tests ==========

    @Test
    fun `tokenize recognizes line comment`() {
        val code = "int x = 0; // this is a comment\nint y = 1;"
        val annotations = CLexer.tokenize(code)
        val commentAnn = annotations.find { it.syntaxHighlight == "comment" }
        assertNotNull(commentAnn)
        val comment = code.substring(commentAnn!!.start, commentAnn.end)
        assertTrue("Comment should contain '//'", comment.startsWith("//"))
        assertTrue("Comment should contain text", comment.contains("this is a comment"))
    }

    @Test
    fun `tokenize recognizes block comment`() {
        val code = "int x = /* block comment */ 0;"
        val annotations = CLexer.tokenize(code)
        val commentAnn = annotations.find { it.syntaxHighlight == "comment" }
        assertNotNull(commentAnn)
        val comment = code.substring(commentAnn!!.start, commentAnn.end)
        assertTrue("Should start with /*", comment.startsWith("/*"))
        assertTrue("Should end with */", comment.endsWith("*/"))
    }

    @Test
    fun `tokenize handles multiline block comment`() {
        val code = "/* line1\nline2\nline3 */"
        val annotations = CLexer.tokenize(code)
        val commentAnn = annotations.find { it.syntaxHighlight == "comment" }
        assertNotNull(commentAnn)
        val comment = code.substring(commentAnn!!.start, commentAnn.end)
        assertTrue(comment.contains("line1"))
        assertTrue(comment.contains("line3"))
    }

    @Test
    fun `tokenize handles unterminated block comment`() {
        val code = "int x; /* never ends"
        val annotations = CLexer.tokenize(code)
        val commentAnn = annotations.find { it.syntaxHighlight == "comment" }
        assertNotNull("Should still create annotation for unterminated block comment", commentAnn)
    }

    // ========== Preprocessor Directive Tests ==========

    @Test
    fun `tokenize recognizes preprocessor include`() {
        val code = "#include <stdio.h>"
        val annotations = CLexer.tokenize(code)
        val ppAnn = annotations.find { it.syntaxHighlight == "keyword" }
        assertNotNull(ppAnn)
        val directive = code.substring(ppAnn!!.start, ppAnn.end)
        assertTrue("Should start with #", directive.startsWith("#include"))
    }

    @Test
    fun `tokenize recognizes preprocessor define`() {
        val code = "#define MAX 100"
        val annotations = CLexer.tokenize(code)
        val ppAnn = annotations.find { it.syntaxHighlight == "keyword" }
        assertNotNull(ppAnn)
        val directive = code.substring(ppAnn!!.start, ppAnn.end)
        assertTrue("Should start with #", directive.startsWith("#define"))
    }

    // ========== Number Tests ==========

    @Test
    fun `tokenize recognizes decimal number`() {
        val code = "int x = 42;"
        val annotations = CLexer.tokenize(code)
        val numAnn = annotations.find { it.syntaxHighlight == "offset" }
        assertNotNull(numAnn)
        assertEquals("42", code.substring(numAnn!!.start, numAnn.end))
    }

    @Test
    fun `tokenize recognizes hexadecimal number`() {
        val code = "int addr = 0xDEADBEEF;"
        val annotations = CLexer.tokenize(code)
        val numAnn = annotations.find { it.syntaxHighlight == "offset" }
        assertNotNull(numAnn)
        assertEquals("0xDEADBEEF", code.substring(numAnn!!.start, numAnn.end))
    }

    @Test
    fun `tokenize recognizes binary number`() {
        val code = "int flags = 0b101010;"
        val annotations = CLexer.tokenize(code)
        val numAnn = annotations.find { it.syntaxHighlight == "offset" }
        assertNotNull(numAnn)
        assertEquals("0b101010", code.substring(numAnn!!.start, numAnn.end))
    }

    @Test
    fun `tokenize recognizes floating point number`() {
        val code = "float pi = 3.14;"
        val annotations = CLexer.tokenize(code)
        val numAnn = annotations.find { it.syntaxHighlight == "offset" }
        assertNotNull(numAnn)
        assertEquals("3.14", code.substring(numAnn!!.start, numAnn.end))
    }

    @Test
    fun `tokenize recognizes number with suffix`() {
        val code = "unsigned long x = 42UL;"
        val annotations = CLexer.tokenize(code)
        val numAnn = annotations.find { it.syntaxHighlight == "offset" }
        assertNotNull(numAnn)
        val num = code.substring(numAnn!!.start, numAnn.end)
        assertTrue("Number should contain 42", num.startsWith("42"))
    }

    // ========== Function Name Tests ==========

    @Test
    fun `tokenize recognizes function call as function_name`() {
        val code = "printf(\"hello\");"
        val annotations = CLexer.tokenize(code)
        val funcAnn = annotations.find { it.syntaxHighlight == "function_name" }
        assertNotNull(funcAnn)
        assertEquals("printf", code.substring(funcAnn!!.start, funcAnn.end))
    }

    @Test
    fun `tokenize does not mark variable as function_name when not followed by paren`() {
        val code = "int value = count;"
        val annotations = CLexer.tokenize(code)
        val funcAnn = annotations.find { it.syntaxHighlight == "function_name" }
        assertNull("'count' without () should not be a function_name", funcAnn)
    }

    @Test
    fun `tokenize recognizes function with space before paren`() {
        val code = "malloc (size);"
        val annotations = CLexer.tokenize(code)
        val funcAnn = annotations.find { it.syntaxHighlight == "function_name" }
        assertNotNull(funcAnn)
        assertEquals("malloc", code.substring(funcAnn!!.start, funcAnn.end))
    }

    // ========== Mixed Code Tests ==========

    @Test
    fun `tokenize handles complex function definition`() {
        val code = """
            int main(int argc, char **argv) {
                printf("hello");
                return 0;
            }
        """.trimIndent()

        val annotations = CLexer.tokenize(code)

        // Should have: int (datatype), main (function_name), int (datatype),
        // char (datatype), printf (function_name), "hello" (string), return (keyword), 0 (offset)
        val highlights = annotations.map { it.syntaxHighlight }
        assertTrue("Should have at least one datatype", highlights.contains("datatype"))
        assertTrue("Should have at least one function_name", highlights.contains("function_name"))
        assertTrue("Should have at least one string", highlights.contains("string"))
        assertTrue("Should have at least one keyword", highlights.contains("keyword"))
    }

    @Test
    fun `tokenize handles code with all token types`() {
        val code = """
            #include <stdio.h>
            void func() {
                int x = 0x10; // comment
                char *s = "test";
                if (x > 0) return;
            }
        """.trimIndent()

        val annotations = CLexer.tokenize(code)
        val highlights = annotations.map { it.syntaxHighlight }.toSet()

        assertTrue("Should have keyword highlight", highlights.contains("keyword"))
        assertTrue("Should have datatype highlight", highlights.contains("datatype"))
        assertTrue("Should have comment highlight", highlights.contains("comment"))
        assertTrue("Should have string highlight", highlights.contains("string"))
        assertTrue("Should have offset highlight", highlights.contains("offset"))
    }

    // ========== Edge Case Tests ==========

    @Test
    fun `tokenize handles empty string input`() {
        val annotations = CLexer.tokenize("")
        assertTrue("Empty input should produce no annotations", annotations.isEmpty())
    }

    @Test
    fun `tokenize handles plain text without any tokens`() {
        val annotations = CLexer.tokenize("   \n\n   ")
        assertTrue("Whitespace-only input should produce no annotations", annotations.isEmpty())
    }

    @Test
    fun `tokenize annotations have valid start and end positions`() {
        val code = "int x = 42;"
        val annotations = CLexer.tokenize(code)
        for (ann in annotations) {
            assertTrue("Start should be >= 0", ann.start >= 0)
            assertTrue("End should be > start", ann.end > ann.start)
            assertTrue("End should be <= code length", ann.end <= code.length)
            assertEquals("Type should be syntax_highlight", "syntax_highlight", ann.type)
        }
    }

    @Test
    fun `tokenize annotations should not overlap`() {
        val code = """
            void main() {
                int x = 0xFF;
                printf("hello %d", x);
                // comment
                return 0;
            }
        """.trimIndent()
        val annotations = CLexer.tokenize(code).sortedBy { it.start }
        for (i in 1 until annotations.size) {
            assertTrue(
                "Annotations should not overlap: [${annotations[i - 1].start},${annotations[i - 1].end}) and [${annotations[i].start},${annotations[i].end})",
                annotations[i - 1].end <= annotations[i].start
            )
        }
    }

    @Test
    fun `tokenize does not highlight regular identifiers`() {
        val code = "variable_name = another_var;"
        val annotations = CLexer.tokenize(code)
        // Neither 'variable_name' nor 'another_var' should be highlighted
        val highlightedTexts = annotations.map { code.substring(it.start, it.end) }
        assertFalse("'variable_name' should not be highlighted", highlightedTexts.contains("variable_name"))
        assertFalse("'another_var' should not be highlighted", highlightedTexts.contains("another_var"))
    }

    @Test
    fun `tokenize handles struct keyword`() {
        val code = "struct Point { int x; int y; };"
        val annotations = CLexer.tokenize(code)
        val keywordAnn = annotations.find { it.syntaxHighlight == "keyword" && code.substring(it.start, it.end) == "struct" }
        assertNotNull("'struct' should be highlighted as keyword", keywordAnn)
    }

    @Test
    fun `tokenize handles switch-case-default`() {
        val code = "switch (x) { case 1: break; default: break; }"
        val annotations = CLexer.tokenize(code)
        val keywords = annotations.filter { it.syntaxHighlight == "keyword" }.map { code.substring(it.start, it.end) }
        assertTrue("Should contain 'switch'", keywords.contains("switch"))
        assertTrue("Should contain 'case'", keywords.contains("case"))
        assertTrue("Should contain 'break'", keywords.contains("break"))
        assertTrue("Should contain 'default'", keywords.contains("default"))
    }
}
