package com.vinstall.alwiz.timeline

enum class RowKind { ROOT, SIDE, MAIN }

class TimelineRow(
    val node: TimelineNode,
    val depth: Int,
    val kind: RowKind,
    val through: BooleanArray,
    val elbowContinues: Boolean,
    val hasChildren: Boolean,
    val isCurrent: Boolean
)

object TimelineTree {

    private class Item(
        val node: TimelineNode,
        val depth: Int,
        val kind: RowKind,
        val parentIndex: Int
    )

    fun build(nodes: List<TimelineNode>): List<TimelineRow> {
        if (nodes.isEmpty()) return emptyList()
        val ids = nodes.mapTo(HashSet()) { it.id }
        val byId = nodes.associateBy { it.id }
        val byParent = nodes.groupBy { node -> node.parentId?.takeIf { it in ids } }
        val head = nodes.lastOrNull { it.type != TimelineType.ATTEMPT }
        val current = head?.takeIf { it.type != TimelineType.UNINSTALL }

        val headPath = HashSet<String>()
        var cursor = head
        while (cursor != null && headPath.add(cursor.id)) {
            cursor = cursor.parentId?.let { byId[it] }
        }

        val items = ArrayList<Item>()
        fun visit(node: TimelineNode, depth: Int, kind: RowKind, parentIndex: Int) {
            val index = items.size
            items += Item(node, depth, kind, parentIndex)
            val kids = (byParent[node.id] ?: emptyList()).sortedBy { it.timestamp }
            val lifecycle = kids.filter { it.type != TimelineType.ATTEMPT }
            val main = lifecycle.firstOrNull { it.id in headPath } ?: lifecycle.lastOrNull()
            kids.filter { it !== main }.forEach { visit(it, depth + 1, RowKind.SIDE, index) }
            if (main != null) visit(main, depth, RowKind.MAIN, index)
        }
        (byParent[null] ?: emptyList()).sortedBy { it.timestamp }
            .forEach { visit(it, 0, RowKind.ROOT, -1) }

        val end = IntArray(items.size) { -1 }
        items.forEachIndexed { i, item ->
            if (item.parentIndex >= 0) end[item.parentIndex] = maxOf(end[item.parentIndex], i)
        }

        return items.mapIndexed { r, item ->
            val through = BooleanArray(item.depth)
            for (p in 0 until r) {
                if (end[p] <= r) continue
                val column = items[p].depth
                if (column >= item.depth) continue
                if (item.kind == RowKind.SIDE && item.parentIndex == p) continue
                through[column] = true
            }
            TimelineRow(
                node = item.node,
                depth = item.depth,
                kind = item.kind,
                through = through,
                elbowContinues = item.kind == RowKind.SIDE && end[item.parentIndex] > r,
                hasChildren = end[r] >= 0,
                isCurrent = item.node.id == current?.id
            )
        }
    }
}
