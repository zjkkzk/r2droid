package top.wsdx233.r2droid.feature.graph.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.flow.StateFlow
import top.wsdx233.r2droid.R
import top.wsdx233.r2droid.core.data.model.GraphBlockInstruction
import top.wsdx233.r2droid.core.data.model.GraphData
import top.wsdx233.r2droid.core.data.model.GraphNode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// 1. 布局常量 - 调整了层间距，给走线留出充裕空间
private const val NODE_PADDING = 12f
private const val NODE_TITLE_HEIGHT = 32f
private const val INSTR_LINE_HEIGHT = 22f
private const val NODE_MIN_WIDTH = 200f
private const val NODE_CORNER_RADIUS = 6f
private const val LAYER_GAP_Y = 72f  // 增加层间距，防止水平线拥挤重叠
private const val NODE_GAP_X = 24f
private const val CHAR_WIDTH_APPROX = 8f
private const val ARROW_SIZE = 10f
private const val MAX_INSTRUCTIONS_PER_NODE = 40
private const val ADDR_DISASM_GAP = 12f
private const val TRACK_SPACING = 12f // 轨道间距

// 颜色定义
private val nodeBgColor = Color(0xFF1E1E1E)
private val nodeTitleBgColor = Color(0xFF333333)
private val nodeBorderColor = Color(0xFF555555)
private val nodeHighlightBorderColor = Color(0xFF42A5F5)
private val instrHighlightBgColor = Color(0x4442A5F5)
private val edgeColor = Color(0xFF888888)
private val edgeTrueColor = Color(0xFF4CAF50)   // Green Jump
private val edgeFalseColor = Color(0xFFF44336)  // Red Fail

data class LayoutNode(
    val node: GraphNode,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val layer: Int
) {
    val rect: Rect get() = Rect(x, y, x + width, y + height)
    val centerX: Float get() = x + width / 2f
    val bottomY: Float get() = y + height

    fun instrRect(index: Int): Rect {
        val iy = y + NODE_TITLE_HEIGHT + index * INSTR_LINE_HEIGHT
        return Rect(x, iy, x + width, iy + INSTR_LINE_HEIGHT)
    }
}

data class RoutedEdge(
    val points: List<Offset>,
    val color: Color
)

data class GraphLayoutResult(
    val nodes: List<LayoutNode>,
    val edges: List<RoutedEdge>
)

// 轨道分配器：用于严格分配平行的走线坐标，direction 决定是往下/往外递增还是递减
class TrackAllocator(private val base: Float, private val direction: Float = 1f) {
    private var count = 0
    fun allocate(): Float {
        val pos = base + count * direction * TRACK_SPACING
        count++
        return pos
    }
}

private fun displayInstrCount(node: GraphNode): Int {
    val total = node.instructions.size
    return if (total > MAX_INSTRUCTIONS_PER_NODE) MAX_INSTRUCTIONS_PER_NODE + 1 else total
}

private fun nodeWidth(node: GraphNode): Float {
    val titleLen = node.title.length * CHAR_WIDTH_APPROX + NODE_PADDING * 2
    val instrs = node.instructions.take(MAX_INSTRUCTIONS_PER_NODE)
    val maxInstrLen = instrs.maxOfOrNull {
        ("  %X  ".format(it.addr).length + it.disasm.length) * CHAR_WIDTH_APPROX + ADDR_DISASM_GAP
    } ?: 0f
    return max(NODE_MIN_WIDTH, max(titleLen, maxInstrLen + NODE_PADDING * 2))
}

private fun nodeHeight(node: GraphNode): Float {
    val lines = if (node.instructions.isNotEmpty()) displayInstrCount(node) else if (node.body.isNotEmpty()) 1 else 0
    return NODE_TITLE_HEIGHT + lines * INSTR_LINE_HEIGHT + NODE_PADDING
}

// ================= 核心布局算法 =================
fun layoutGraph(data: GraphData): GraphLayoutResult {
    if (data.nodes.isEmpty()) return GraphLayoutResult(emptyList(), emptyList())

    val nodeById = data.nodes.associateBy { it.id }
    val allIds = data.nodes.map { it.id }.toSet()

    val successors = mutableMapOf<Int, MutableList<Int>>()
    val predecessors = mutableMapOf<Int, MutableList<Int>>()
    allIds.forEach { successors[it] = mutableListOf(); predecessors[it] = mutableListOf() }
    data.nodes.forEach { n ->
        n.outNodes.filter { it in allIds }.forEach { t ->
            successors[n.id]!!.add(t)
            predecessors[t]!!.add(n.id)
        }
    }

    val backEdges = mutableSetOf<Pair<Int, Int>>()
    val visited = mutableSetOf<Int>()
    val onStack = mutableSetOf<Int>()
    fun dfs(u: Int) {
        visited.add(u); onStack.add(u)
        for (v in successors[u].orEmpty()) {
            if (v in onStack) backEdges.add(u to v)
            else if (v !in visited) dfs(v)
        }
        onStack.remove(u)
    }
    val roots = allIds.filter { predecessors[it]!!.isEmpty() }
    (roots.ifEmpty { listOf(data.nodes.first().id) }).forEach { if (it !in visited) dfs(it) }
    allIds.forEach { if (it !in visited) dfs(it) }

    val dagPred = mutableMapOf<Int, MutableList<Int>>()
    allIds.forEach { dagPred[it] = mutableListOf() }
    data.nodes.forEach { n ->
        n.outNodes.filter { it in allIds }.forEach { t ->
            if ((n.id to t) !in backEdges) dagPred[t]!!.add(n.id)
        }
    }

    val layer = mutableMapOf<Int, Int>()
    fun longestPath(u: Int): Int {
        layer[u]?.let { return it }
        val preds = dagPred[u].orEmpty()
        val l = if (preds.isEmpty()) 0 else preds.maxOf { longestPath(it) } + 1
        layer[u] = l
        return l
    }
    allIds.forEach { longestPath(it) }
    val maxLayer = layer.values.maxOrNull() ?: 0
    val layerNodes = Array(maxLayer + 1) { l -> allIds.filter { layer[it] == l }.toMutableList() }

    for (l in 1..maxLayer) {
        val posInPrev = mutableMapOf<Int, Int>()
        layerNodes[l - 1].forEachIndexed { i, id -> posInPrev[id] = i }
        layerNodes[l].sortBy { nodeId ->
            val preds = dagPred[nodeId].orEmpty()
            if (preds.isEmpty()) 0.0 else preds.sumOf { posInPrev[it] ?: 0 }.toDouble() / preds.size
        }
    }

    val nodeW = mutableMapOf<Int, Float>()
    val nodeH = mutableMapOf<Int, Float>()
    allIds.forEach { id ->
        val node = nodeById[id]!!
        nodeW[id] = nodeWidth(node)
        nodeH[id] = nodeHeight(node)
    }

    val nodeX = mutableMapOf<Int, Float>()
    val nodeY = mutableMapOf<Int, Float>()
    var currentY = 0f

    for (l in 0..maxLayer) {
        val ids = layerNodes[l]
        var currentX = 0f
        val maxH = ids.maxOfOrNull { nodeH[it]!! } ?: NODE_TITLE_HEIGHT
        
        for (id in ids) {
            nodeX[id] = currentX
            nodeY[id] = currentY
            currentX += nodeW[id]!! + NODE_GAP_X
        }
        
        val totalWidth = currentX - NODE_GAP_X
        val shift = -totalWidth / 2f
        for (id in ids) {
            nodeX[id] = nodeX[id]!! + shift
        }
        
        currentY += maxH + LAYER_GAP_Y
    }

    for (l in 1..maxLayer) {
        for (id in layerNodes[l]) {
            val preds = dagPred[id].orEmpty()
            if (preds.size == 1) {
                val p = preds.first()
                val targetCenter = nodeX[p]!! + nodeW[p]!! / 2f
                val desiredX = targetCenter - nodeW[id]!! / 2f
                nodeX[id] = desiredX
            }
        }
        val sorted = layerNodes[l].sortedBy { nodeX[it]!! }
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            val minX = nodeX[prev]!! + nodeW[prev]!! + NODE_GAP_X
            if (nodeX[curr]!! < minX) {
                nodeX[curr] = minX
            }
        }
    }

    val layoutNodesList = allIds.map { id ->
        LayoutNode(nodeById[id]!!, nodeX[id]!!, nodeY[id]!!, nodeW[id]!!, nodeH[id]!!, layer[id]!!)
    }
    val layoutNodeMap = layoutNodesList.associateBy { it.node.id }

    val edges = routeEdgesStrictly(data, layoutNodesList, layoutNodeMap, backEdges)
    return GraphLayoutResult(layoutNodesList, edges)
}

// ================= 全新重写的路由算法，彻底解决穿模和箭头重叠 =================
private fun routeEdgesStrictly(
    data: GraphData,
    nodes: List<LayoutNode>,
    nodeMap: Map<Int, LayoutNode>,
    backEdges: Set<Pair<Int, Int>>
): List<RoutedEdge> {
    val routedEdges = mutableListOf<RoutedEdge>()
    if (nodes.isEmpty()) return emptyList()

    val graphLeft = nodes.minOf { it.x } - TRACK_SPACING * 3
    val graphRight = nodes.maxOf { it.x + it.width } + TRACK_SPACING * 3

    // 1. 获取每层的物理边界 (Y坐标)
    val layerTops = mutableMapOf<Int, Float>()
    val layerBottoms = mutableMapOf<Int, Float>()
    nodes.groupBy { it.layer }.forEach { (l, lst) ->
        layerTops[l] = lst.minOf { it.y }
        layerBottoms[l] = lst.maxOf { it.bottomY }
    }

    // 2. 准备各类轨道分配器
    val hExitAllocators = mutableMapOf<Int, TrackAllocator>()
    val hEntryAllocators = mutableMapOf<Int, TrackAllocator>()
    
    // 出口轨道：在层的最底部往下排 (Direction = 1f)
    layerBottoms.forEach { (l, bottom) -> 
        hExitAllocators[l] = TrackAllocator(bottom + 12f, 1f) 
    }
    // 入口轨道：在层的最顶部往上排 (Direction = -1f)，确保线在节点上方
    layerTops.forEach { (l, top) -> 
        hEntryAllocators[l] = TrackAllocator(top - 16f, -1f) 
    }

    val leftOuterTracks = TrackAllocator(graphLeft, -1f)
    val rightOuterTracks = TrackAllocator(graphRight, 1f)

    data class EdgeTask(val src: LayoutNode, val tgt: LayoutNode, val isJump: Boolean, val isFail: Boolean, val isBack: Boolean)
    val tasks = mutableListOf<EdgeTask>()

    for (node in data.nodes) {
        val srcNode = nodeMap[node.id] ?: continue
        val lastInstr = node.instructions.lastOrNull()
        val hasBranch = lastInstr?.jump != null && lastInstr.fail != null
        
        for (tgtId in node.outNodes) {
            val tgtNode = nodeMap[tgtId] ?: continue
            val isBack = (node.id to tgtId) in backEdges || tgtNode.layer <= srcNode.layer
            
            var isJump = false
            var isFail = false
            if (hasBranch) {
                if (tgtNode.node.address == lastInstr?.jump) isJump = true
                else if (tgtNode.node.address == lastInstr?.fail) isFail = true
            }
            tasks.add(EdgeTask(srcNode, tgtNode, isJump, isFail, isBack))
        }
    }

    // 记录进入目标节点的线的数量，用于分配 X 轴偏移量（防止箭头重叠）
    val targetEntryCount = mutableMapOf<Int, Int>()

    for (task in tasks) {
        val src = task.src
        val tgt = task.tgt
        val pts = mutableListOf<Offset>()
        
        val color = when {
            task.isJump -> edgeTrueColor
            task.isFail -> edgeFalseColor
            else -> edgeColor
        }

        // --- 起点 X 轴分配 (底部) ---
        // Jump 偏左出，Fail 偏右出，无条件居中
        val exitOffset = if (task.isJump) -12f else if (task.isFail) 12f else 0f
        val startX = src.centerX + exitOffset
        val startY = src.bottomY

        // --- 终点 X 轴分配 (顶部) ---
        // 动态分配偏移量，顺序: 0, -10, +10, -20, +20... 完美防止箭头糊在一起
        val entryIdx = targetEntryCount.getOrDefault(tgt.node.id, 0)
        targetEntryCount[tgt.node.id] = entryIdx + 1
        val entryOffset = if (entryIdx == 0) 0f else {
            val mult = (entryIdx + 1) / 2
            val sign = if (entryIdx % 2 != 0) -1f else 1f
            sign * mult * 10f
        }
        val endX = tgt.centerX + entryOffset
        val endY = tgt.y

        pts.add(Offset(startX, startY))

        // --- 核心路由计算 ---
        if (task.isBack || tgt.layer - src.layer > 1) {
            // 需要绕到外围的情况（回边循环 / 跨越过层的长线）
            val isLeftBound = src.centerX < (graphLeft + graphRight) / 2
            val sideX = if (isLeftBound) leftOuterTracks.allocate() else rightOuterTracks.allocate()
            
            // 出口轨道向下，入口轨道向上，绝对不会和节点交叉
            val exitY = hExitAllocators[src.layer]!!.allocate()
            val entryY = hEntryAllocators[tgt.layer]!!.allocate()

            pts.add(Offset(startX, exitY)) // 下拉出节点
            pts.add(Offset(sideX, exitY))  // 走向外围
            pts.add(Offset(sideX, entryY)) // 在外围长途跋涉到目标上方
            pts.add(Offset(endX, entryY))  // 走到目标正上方
            
        } else {
            // 相邻层直接相连的情况
            if (abs(startX - endX) > 2f) {
                // 不在一条垂直线上，需要走个 Z 字型
                val midY = hExitAllocators[src.layer]!!.allocate()
                pts.add(Offset(startX, midY))
                pts.add(Offset(endX, midY))
            }
            // 如果在一条垂直线上，什么都不加，直接一根线到底
        }

        // 无论何种情况，最后一步绝对是垂直向下进入节点顶部！
        pts.add(Offset(endX, endY))
        
        routedEdges.add(RoutedEdge(pts, color))
    }

    return routedEdges
}


// ================= Compose UI 渲染层 =================

@Composable
fun GraphViewer(
    graphData: GraphData,
    cursorAddress: Long,
    scrollToSelectionTrigger: StateFlow<Int>,
    onAddressClick: (Long) -> Unit,
    onShowXrefs: (Long) -> Unit = {},
    onShowInstructionDetail: (Long) -> Unit = {},
    initialScale: Float = 1f,
    onScaleChanged: (Float) -> Unit = {}
) {
    val density = LocalDensity.current
    val layoutResult = remember(graphData) { layoutGraph(graphData) }
    val layoutNodes = layoutResult.nodes

    val highlightNodeId = remember(layoutNodes, cursorAddress) {
        layoutNodes.firstOrNull { ln ->
            ln.node.address == cursorAddress || ln.node.instructions.any { it.addr == cursorAddress }
        }?.node?.id
    }

    val graphBounds = remember(layoutResult) {
        if (layoutResult.nodes.isEmpty()) Rect.Zero
        else {
            var minX = layoutResult.nodes.minOf { it.x }
            var minY = layoutResult.nodes.minOf { it.y }
            var maxX = layoutResult.nodes.maxOf { it.x + it.width }
            var maxY = layoutResult.nodes.maxOf { it.y + it.height }
            for (edge in layoutResult.edges) {
                for (pt in edge.points) {
                    if (pt.x < minX) minX = pt.x
                    if (pt.y < minY) minY = pt.y
                    if (pt.x > maxX) maxX = pt.x
                    if (pt.y > maxY) maxY = pt.y
                }
            }
            Rect(minX - 100f, minY - 100f, maxX + 100f, maxY + 100f)
        }
    }

    var viewportSize by remember { mutableStateOf(Size.Zero) }
    var scale by remember { mutableFloatStateOf(initialScale) }
    var offset by remember(graphBounds) {
        val centerX = -(graphBounds.left + graphBounds.right) / 2f
        val centerY = -graphBounds.top
        mutableStateOf(Offset(centerX, centerY))
    }

    fun clampOffset(off: Offset, s: Float): Offset {
        val vw = if (viewportSize.width > 0f) viewportSize.width else 1080f
        val vh = if (viewportSize.height > 0f) viewportSize.height else 1920f
        val minOx = -graphBounds.right * s - vw / 2f
        val maxOx = -graphBounds.left * s + vw / 2f
        val minOy = -graphBounds.bottom * s - vh / 2f
        val maxOy = -graphBounds.top * s + vh / 2f
        return Offset(off.x.coerceIn(minOx, maxOx), off.y.coerceIn(minOy, maxOy))
    }

    val scrollTrigger by scrollToSelectionTrigger.collectAsState()
    LaunchedEffect(scrollTrigger) {
        if (scrollTrigger > 0) {
            val ln = highlightNodeId?.let { id -> layoutNodes.firstOrNull { it.node.id == id } }
            val centerX = if (ln != null) ln.centerX else (graphBounds.left + graphBounds.right) / 2f
            val centerY = if (ln != null) ln.y + ln.height / 2f else (graphBounds.top + graphBounds.bottom) / 2f
            offset = clampOffset(Offset(-centerX * scale, -centerY * scale), scale)
        }
    }

    var menuVisible by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(DpOffset.Zero) }
    var menuInstr by remember { mutableStateOf<GraphBlockInstruction?>(null) }
    var menuNodeTitle by remember { mutableStateOf("") }
    var menuNodeAddress by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current

    val textPaint = remember {
        android.graphics.Paint().apply {
            color = "#D4D4D4".toColorInt()
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }
    }
    val titlePaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 15f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
    }
    val addrPaint = remember {
        android.graphics.Paint().apply {
            color = "#888888".toColorInt()
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF161616))
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(0.15f, 5f)
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val centroidRel = Offset(centroid.x - cx, centroid.y - cy)
                        val newOffset = centroidRel - (centroidRel - offset) * (newScale / oldScale) + pan
                        scale = newScale
                        onScaleChanged(newScale)
                        offset = clampOffset(newOffset, newScale)
                    }
                }
                .pointerInput(layoutNodes, scale, offset) {
                    detectTapGestures { tapOffset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val gx = (tapOffset.x - cx - offset.x) / scale
                        val gy = (tapOffset.y - cy - offset.y) / scale

                        for (ln in layoutNodes) {
                            if (ln.rect.contains(Offset(gx, gy))) {
                                if (ln.node.instructions.isNotEmpty()) {
                                    val visibleInstrs = ln.node.instructions.take(MAX_INSTRUCTIONS_PER_NODE)
                                    for ((idx, instr) in visibleInstrs.withIndex()) {
                                        if (ln.instrRect(idx).contains(Offset(gx, gy))) {
                                            menuInstr = instr
                                            menuNodeTitle = ln.node.title
                                            menuNodeAddress = ln.node.address
                                            menuPosition = with(density) { DpOffset(tapOffset.x.toDp(), tapOffset.y.toDp()) }
                                            menuVisible = true
                                            return@detectTapGestures
                                        }
                                    }
                                }
                                menuInstr = null
                                menuNodeTitle = ln.node.title
                                menuNodeAddress = ln.node.address
                                menuPosition = with(density) { DpOffset(tapOffset.x.toDp(), tapOffset.y.toDp()) }
                                menuVisible = true
                                return@detectTapGestures
                            }
                        }
                    }
                }
        ) {
            viewportSize = size
            val cx = size.width / 2f
            val cy = size.height / 2f

            withTransform({
                translate(cx + offset.x, cy + offset.y)
                scale(scale, scale, Offset.Zero)
            }) {
                // 先画线，让线在节点底层，视觉上更清晰
                drawRoutedEdges(layoutResult.edges)

                for (ln in layoutNodes) {
                    val isHighlighted = ln.node.id == highlightNodeId
                    drawNode(ln, textPaint, titlePaint, addrPaint, density.density, isHighlighted, cursorAddress)
                }
            }
        }

        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }, offset = menuPosition) {
            val instr = menuInstr
            if (instr != null) {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_jump) + " → 0x%X".format(instr.addr)) }, onClick = { menuVisible = false; onAddressClick(instr.addr) })
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_copy) + " " + stringResource(R.string.menu_address)) }, onClick = {
                    menuVisible = false
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("address", "0x%X".format(instr.addr)))
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_copy) + " " + stringResource(R.string.menu_opcodes)) }, onClick = {
                    menuVisible = false
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("disasm", instr.disasm))
                })
                if (instr.jump != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_jump) + " → 0x%X".format(instr.jump)) }, onClick = { menuVisible = false; onAddressClick(instr.jump) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_graph_xrefs)) }, onClick = { menuVisible = false; onShowXrefs(instr.addr) })
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_graph_detail)) }, onClick = { menuVisible = false; onShowInstructionDetail(instr.addr) })
            } else {
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_jump) + " → $menuNodeTitle") }, onClick = { menuVisible = false })
                if (menuNodeAddress != 0L) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_graph_xrefs)) }, onClick = { menuVisible = false; onShowXrefs(menuNodeAddress) })
                }
            }
        }
    }
}

private fun DrawScope.drawRoutedEdges(edges: List<RoutedEdge>) {
    for (edge in edges) {
        val pts = edge.points
        if (pts.size < 2) continue

        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                lineTo(pts[i].x, pts[i].y)
            }
        }
        drawPath(path, edge.color, style = Stroke(width = 1.5f))

        val tip = pts.last()
        val from = pts[pts.size - 2]
        drawArrowHead(tip, from, edge.color)
    }
}

// 修改了箭头逻辑，使其完美贴合并且向后延伸，绝对不会越过 y 轴界限进入节点内部
private fun DrawScope.drawArrowHead(tip: Offset, from: Offset, color: Color) {
    val dx = tip.x - from.x
    val dy = tip.y - from.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 1f) return

    val ux = dx / len
    val uy = dy / len

    val baseX = tip.x - ux * ARROW_SIZE
    val baseY = tip.y - uy * ARROW_SIZE

    val perpX = -uy * ARROW_SIZE * 0.4f
    val perpY = ux * ARROW_SIZE * 0.4f

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseX + perpX, baseY + perpY)
        lineTo(baseX - perpX, baseY - perpY)
        close()
    }
    // 使用 Fill 绘制实心箭头更清晰
    drawPath(path, color)
}

private fun DrawScope.drawNode(
    ln: LayoutNode,
    textPaint: android.graphics.Paint,
    titlePaint: android.graphics.Paint,
    addrPaint: android.graphics.Paint,
    density: Float,
    isHighlighted: Boolean = false,
    cursorAddress: Long = 0L
) {
    val cornerRadius = NODE_CORNER_RADIUS * density

    drawRoundRect(
        color = nodeBgColor,
        topLeft = Offset(ln.x, ln.y),
        size = Size(ln.width, ln.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
    )

    drawRoundRect(
        color = nodeTitleBgColor,
        topLeft = Offset(ln.x, ln.y),
        size = Size(ln.width, NODE_TITLE_HEIGHT),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
    )

    if (ln.node.instructions.isNotEmpty() || ln.node.body.isNotEmpty()) {
        drawRect(
            color = nodeTitleBgColor,
            topLeft = Offset(ln.x, ln.y + NODE_TITLE_HEIGHT / 2),
            size = Size(ln.width, NODE_TITLE_HEIGHT / 2)
        )
    }

    drawRoundRect(
        color = if (isHighlighted) nodeHighlightBorderColor else nodeBorderColor,
        topLeft = Offset(ln.x, ln.y),
        size = Size(ln.width, ln.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
        style = Stroke(width = if (isHighlighted) 2.5f else 1f)
    )

    drawContext.canvas.nativeCanvas.drawText(
        ln.node.title,
        ln.x + NODE_PADDING,
        ln.y + NODE_TITLE_HEIGHT / 2f + titlePaint.textSize / 3f,
        titlePaint
    )

    if (ln.node.instructions.isNotEmpty()) {
        val canvas = drawContext.canvas.nativeCanvas
        val instrPaint = android.graphics.Paint(textPaint)
        val visibleInstrs = ln.node.instructions.take(MAX_INSTRUCTIONS_PER_NODE)

        visibleInstrs.forEachIndexed { idx, instr ->
            val lineY = ln.y + NODE_TITLE_HEIGHT + (idx + 0.5f) * INSTR_LINE_HEIGHT + instrPaint.textSize / 3f

            if (instr.addr == cursorAddress) {
                drawRect(
                    color = instrHighlightBgColor,
                    topLeft = Offset(ln.x, ln.y + NODE_TITLE_HEIGHT + idx * INSTR_LINE_HEIGHT),
                    size = Size(ln.width, INSTR_LINE_HEIGHT)
                )
            }
            
            val addrText = "%X".format(instr.addr)
            canvas.drawText(addrText, ln.x + NODE_PADDING, lineY, addrPaint)

            instrPaint.color = when (instr.type) {
                "call", "ucall", "ircall" -> "#569CD6".toColorInt()
                "jmp", "cjmp", "ujmp" -> "#4CAF50".toColorInt()
                "ret" -> "#F44336".toColorInt()
                "push", "pop", "rpush" -> "#C586C0".toColorInt()
                "cmp", "test", "acmp" -> "#DCDCAA".toColorInt()
                "nop" -> android.graphics.Color.GRAY
                else -> "#D4D4D4".toColorInt()
            }

            val addrWidth = addrPaint.measureText(addrText)
            val disasmX = ln.x + NODE_PADDING + addrWidth + ADDR_DISASM_GAP * density
            
            canvas.drawText(instr.disasm, disasmX, lineY, instrPaint)
        }
    } else if (ln.node.body.isNotEmpty()) {
        drawContext.canvas.nativeCanvas.drawText(
            ln.node.body,
            ln.x + NODE_PADDING,
            ln.y + NODE_TITLE_HEIGHT + INSTR_LINE_HEIGHT / 2f + textPaint.textSize / 3f,
            textPaint
        )
    }
}