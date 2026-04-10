package top.wsdx233.r2droid.core.data.model

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GraphDataTest {

    // ========== fromAgrj Tests ==========

    @Test
    fun `fromAgrj parses basic graph`() {
        val json = JSONObject("""
            {
                "nodes": [
                    {"id": 0, "title": "0x401000", "body": "push rbp", "out_nodes": [1]},
                    {"id": 1, "title": "0x401010", "body": "ret", "out_nodes": []}
                ]
            }
        """.trimIndent())
        val graph = GraphData.fromAgrj(json)
        assertEquals(2, graph.nodes.size)
        assertEquals(0, graph.nodes[0].id)
        assertEquals("0x401000", graph.nodes[0].title)
        assertEquals(listOf(1), graph.nodes[0].outNodes)
        assertEquals(listOf<Int>(), graph.nodes[1].outNodes)
    }

    @Test
    fun `fromAgrj handles empty nodes`() {
        val json = JSONObject("""{"nodes": []}""")
        val graph = GraphData.fromAgrj(json)
        assertTrue(graph.nodes.isEmpty())
    }

    @Test
    fun `fromAgrj handles missing nodes key`() {
        val json = JSONObject("""{}""")
        val graph = GraphData.fromAgrj(json)
        assertTrue(graph.nodes.isEmpty())
    }

    @Test
    fun `fromAgrj handles multiple outgoing edges`() {
        val json = JSONObject("""
            {
                "nodes": [
                    {"id": 0, "title": "0x401000", "body": "", "out_nodes": [1, 2]},
                    {"id": 1, "title": "0x401010", "body": "", "out_nodes": []},
                    {"id": 2, "title": "0x401020", "body": "", "out_nodes": []}
                ]
            }
        """.trimIndent())
        val graph = GraphData.fromAgrj(json)
        assertEquals(listOf(1, 2), graph.nodes[0].outNodes)
    }

    // ========== fromAgj Tests ==========

    @Test
    fun `fromAgj parses function graph with blocks`() {
        val json = JSONArray("""
            [
                {
                    "name": "sym.main",
                    "blocks": [
                        {
                            "addr": 4194560,
                            "ops": [
                                {"addr": 4194560, "opcode": "push rbp", "disasm": "push rbp", "type": "push", "bytes": "55"},
                                {"addr": 4194561, "opcode": "mov rbp, rsp", "disasm": "mov rbp, rsp", "type": "mov", "bytes": "4889e5"}
                            ]
                        },
                        {
                            "addr": 4194570,
                            "jump": 4194560,
                            "fail": 4194580,
                            "ops": [
                                {"addr": 4194570, "opcode": "je 0x401000", "disasm": "je 0x401000", "type": "cjmp", "bytes": "740e", "jump": 4194560, "fail": 4194580}
                            ]
                        }
                    ]
                }
            ]
        """.trimIndent())
        val graph = GraphData.fromAgj(json)
        assertEquals("sym.main", graph.title)
        assertEquals(2, graph.nodes.size)

        // First block has instructions
        assertEquals(2, graph.nodes[0].instructions.size)
        assertEquals("push rbp", graph.nodes[0].instructions[0].disasm)

        // Second block has jump/fail edges
        assertTrue(graph.nodes[1].outNodes.contains(0)) // jump to first block
    }

    @Test
    fun `fromAgj handles empty blocks array`() {
        val json = JSONArray("""[{"name": "sym.empty", "blocks": []}]""")
        val graph = GraphData.fromAgj(json)
        assertTrue(graph.nodes.isEmpty())
        assertEquals("sym.empty", graph.title)
    }

    @Test
    fun `fromAgj handles empty input array`() {
        val json = JSONArray("[]")
        val graph = GraphData.fromAgj(json)
        assertTrue(graph.nodes.isEmpty())
    }

    @Test
    fun `fromAgj adds implicit fallthrough for non-terminal blocks`() {
        val json = JSONArray("""
            [
                {
                    "name": "sym.fallthrough",
                    "blocks": [
                        {
                            "addr": 100,
                            "ops": [{"addr": 100, "opcode": "mov eax, 0", "disasm": "mov eax, 0", "type": "mov", "bytes": "b800000000"}]
                        },
                        {
                            "addr": 200,
                            "ops": [{"addr": 200, "opcode": "ret", "disasm": "ret", "type": "ret", "bytes": "c3"}]
                        }
                    ]
                }
            ]
        """.trimIndent())
        val graph = GraphData.fromAgj(json)
        // First block should have implicit fallthrough to second block
        assertEquals(listOf(1), graph.nodes[0].outNodes)
    }

    @Test
    fun `fromAgj does NOT add implicit fallthrough for terminal ret blocks`() {
        val json = JSONArray("""
            [
                {
                    "name": "sym.terminal",
                    "blocks": [
                        {
                            "addr": 100,
                            "ops": [{"addr": 100, "opcode": "nop", "disasm": "nop", "type": "nop", "bytes": "90"}]
                        },
                        {
                            "addr": 200,
                            "ops": [{"addr": 200, "opcode": "ret", "disasm": "ret", "type": "ret", "bytes": "c3"}]
                        }
                    ]
                }
            ]
        """.trimIndent())
        val graph = GraphData.fromAgj(json)
        // Second block (ret) should have no outgoing edges
        assertTrue(graph.nodes[1].outNodes.isEmpty())
    }

    @Test
    fun `fromAgj does NOT add implicit fallthrough for jmp blocks`() {
        val json = JSONArray("""
            [
                {
                    "name": "sym.jmp_test",
                    "blocks": [
                        {
                            "addr": 100,
                            "ops": [{"addr": 100, "opcode": "jmp 0x100", "disasm": "jmp 0x100", "type": "jmp", "bytes": "eb00", "jump": 256}]
                        },
                        {
                            "addr": 200,
                            "ops": [{"addr": 200, "opcode": "nop", "disasm": "nop", "type": "nop", "bytes": "90"}]
                        }
                    ]
                }
            ]
        """.trimIndent())
        val graph = GraphData.fromAgj(json)
        // First block has explicit jump, no fallthrough to second block (addr 200 not in block map)
        // The jmp target is 256 which isn't in the block map, so outNodes should be empty
        assertTrue("Jmp block should not fall through to next", graph.nodes[0].outNodes.isEmpty() || !graph.nodes[0].outNodes.contains(1))
    }

    // ========== fromCallGraph Tests ==========

    @Test
    fun `fromCallGraph parses call graph with imports`() {
        val json = JSONArray("""
            [
                {"name": "main", "imports": ["printf", "exit"]},
                {"name": "helper", "imports": ["malloc"]}
            ]
        """.trimIndent())
        val graph = GraphData.fromCallGraph(json)
        // Should have 5 nodes: main, helper, printf, exit, malloc
        assertEquals(5, graph.nodes.size)

        val mainNode = graph.nodes.find { it.title == "main" }
        assertNotNull(mainNode)
        assertEquals(listOf("printf", "exit"), mainNode!!.outNodes.mapNotNull { id ->
            graph.nodes.find { it.id == id }?.title
        })
    }

    @Test
    fun `fromCallGraph handles leaf nodes that are only imports`() {
        val json = JSONArray("""
            [{"name": "main", "imports": ["printf"]}]
        """.trimIndent())
        val graph = GraphData.fromCallGraph(json)
        assertEquals(2, graph.nodes.size)

        val printfNode = graph.nodes.find { it.title == "printf" }
        assertNotNull("printf should be a leaf node", printfNode)
        assertTrue(printfNode!!.outNodes.isEmpty())
    }

    @Test
    fun `fromCallGraph handles empty array`() {
        val json = JSONArray("[]")
        val graph = GraphData.fromCallGraph(json)
        assertTrue(graph.nodes.isEmpty())
    }

    // ========== fromFunctionInfo Tests ==========

    @Test
    fun `fromFunctionInfo parses function list`() {
        val json = JSONArray("""
            [
                {"name": "main", "addr": 4194560},
                {"name": "helper", "addr": 4194620}
            ]
        """.trimIndent())
        val graph = GraphData.fromFunctionInfo(json)
        assertEquals(2, graph.nodes.size)
        assertEquals("main", graph.nodes[0].title)
        assertEquals("helper", graph.nodes[1].title)
        assertEquals(4194560L, graph.nodes[0].address)
    }

    @Test
    fun `fromFunctionInfo delegates to fromCallGraph when imports present`() {
        val json = JSONArray("""
            [{"name": "main", "imports": ["printf"]}]
        """.trimIndent())
        val graph = GraphData.fromFunctionInfo(json)
        // Should use fromCallGraph path
        assertTrue(graph.nodes.size > 1)
    }

    @Test
    fun `fromFunctionInfo uses address as title when name is empty`() {
        val json = JSONArray("""[{"addr": 4194560}]""")
        val graph = GraphData.fromFunctionInfo(json)
        assertEquals(1, graph.nodes.size)
        assertTrue(graph.nodes[0].title.startsWith("0x"))
    }

    // ========== GraphBlockInstruction Tests ==========

    @Test
    fun `GraphBlockInstruction fromJson parses correctly`() {
        val json = JSONObject("""
            {
                "addr": 4194560,
                "opcode": "push rbp",
                "disasm": "push rbp",
                "type": "push",
                "bytes": "55",
                "jump": 4194304,
                "fail": 4194570
            }
        """.trimIndent())
        val instr = GraphBlockInstruction.fromJson(json)
        assertEquals(4194560L, instr.addr)
        assertEquals("push rbp", instr.opcode)
        assertEquals(4194304L, instr.jump)
        assertEquals(4194570L, instr.fail)
    }

    @Test
    fun `GraphBlockInstruction fromJson falls back to opcode for disasm`() {
        val json = JSONObject("""{"addr": 100, "opcode": "nop", "type": "nop", "bytes": "90"}""")
        val instr = GraphBlockInstruction.fromJson(json)
        assertEquals("nop", instr.disasm)
    }

    // ========== GraphNode Tests ==========

    @Test
    fun `GraphNode data class equality works`() {
        val node1 = GraphNode(id = 0, title = "0x100", address = 256L, body = "", outNodes = listOf(1))
        val node2 = GraphNode(id = 0, title = "0x100", address = 256L, body = "", outNodes = listOf(1))
        assertEquals(node1, node2)
    }

    @Test
    fun `GraphNode with different outNodes are not equal`() {
        val node1 = GraphNode(id = 0, title = "A", outNodes = listOf(1, 2))
        val node2 = GraphNode(id = 0, title = "A", outNodes = listOf(1))
        assertNotEquals(node1, node2)
    }
}
