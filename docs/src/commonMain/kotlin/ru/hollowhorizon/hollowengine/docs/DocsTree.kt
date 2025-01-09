package ru.hollowhorizon.hollowengine.docs

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_DOWN
import de.fabmax.kool.modules.ui2.ArrowScope.Companion.ROTATION_RIGHT
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.logI

class DocsNode(val treeName: String, val treePath: String) : Composable {
    var isFolder = true
    var depth = 0
    val children: MutableList<DocsNode> = ArrayList()
    val isExpanded = mutableStateOf(false)

    fun walk(): MutableList<DocsNode> {
        val list = mutableListOf(this)
        if (isExpanded.value) list.addAll(children.flatMap { it.walk() })
        return list
    }

    fun toggleExpanded() {
        if (isFolder) {
            logI { "Clicked (${isExpanded.value}): ${walk().joinToString(", ") { it.treePath }}" }
            isExpanded.set(!isExpanded.value)
        }
    }

    fun sort() {
        children.sortBy { it.treeName }
        children.sortByDescending { it.isFolder }
        children.forEach { it.sort() }
    }

    companion object {
        val EMPTY = DocsNode("", "")
    }

    override fun UiScope.compose() {

        modifier.margin(sizes.smallGap)

        LazyList(
            containerModifier = { it.backgroundColor(null) },
            vScrollbarModifier = { it.width(10.dp).margin(5.dp) }
        ) {
            var hoveredIndex by remember(-1)

            itemsIndexed(walk()) { i, item ->
                sceneObjectItem(item, i == hoveredIndex).apply {
                    modifier.onEnter { hoveredIndex = i }.onExit { hoveredIndex = -1 }
                        .onClick {

                        }
                }
            }
        }

    }

    private fun UiScope.sceneObjectItem(item: DocsNode, isHovered: Boolean) {
        modifier
            .onClick { evt ->
                if (evt.pointer.isLeftButtonClicked && evt.pointer.leftButtonRepeatedClickCount == 2) {
                    if (item.isFolder) {
                        item.toggleExpanded()
                    } else {

                    }
                }
            }
            .margin(horizontal = sizes.smallGap)
            .padding(horizontal = sizes.smallGap)

        if (isHovered) {
            modifier.background(RoundRectBackground(colors.hoverBg, sizes.smallGap))
        }

        sceneObjectLabel(item, isHovered)
    }

    private fun UiScope.sceneObjectLabel(item: DocsNode, isHovered: Boolean) =
        Row(width = Grow.Std) {
            // tree-depth based indentation
            if (item.depth > 0) {
                Box(width = 20.dp * item.depth) {}
            }

            // expand / collapse arrow
            Box {
                modifier
                    .size(sizes.lineHeight * 0.8f, sizes.lineHeight)
                    .alignY(AlignmentY.Center)
                if (item.isFolder) {
                    Arrow(isHoverable = false) {
                        modifier
                            .rotation(if (item.isExpanded.use()) ROTATION_DOWN else ROTATION_RIGHT)
                            .align(AlignmentX.Center, AlignmentY.Center)
                            .onClick { item.toggleExpanded() }
                            .size(15.dp, 15.dp)
                    }
                }
            }

            val fgColor = if (isHovered) colors.primary else colors.secondary

//            val icon = when {
//                item.isFolder && item.isExpanded.value -> FOLDER_OPEN
//                item.isFolder && !item.isExpanded.value -> FOLDER
//                item.treeName.endsWith(".kts") -> KOTLIN
//                else -> FILE
//            }
//
//            Box {
//                modifier.alignY(AlignmentY.Center)
//                Image(icon) {
//                    modifier.margin(horizontal = 10.dp).size(sizes.lineHeight, sizes.lineHeight)
//                        .imageSize(ImageSize.Stretch)
//                }
//            }

            Box(width = Grow.Std, height = Grow.Std) {
                Text(item.treeName) {
                    modifier
                        .alignY(AlignmentY.Center)
                        .textColor(fgColor)
                }
            }
        }
}