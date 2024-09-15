package ru.hollowhorizon.hollowengine.scripting.nodes


class WhenNode(val branches: List<BranchNode>) : Node {
    var branchIndex = -1

    override fun execute(): Boolean {
        if(branchIndex == -1) branchIndex = branches.indexOfFirst { it.condition.execute() }

        if(branches[branchIndex].body.execute()) {
            return true
        }

        return false
    }
}

class BranchNode(val condition: Node, val body: Node) : Node {
    private var lastCheck = false

    override fun execute(): Boolean {
        if (!lastCheck) lastCheck = condition.execute()
        if (lastCheck) {
            if (body.execute()) {
                lastCheck = false
                return true
            }
        }
        return false
    }

    override fun reset() {
        condition.reset()
        body.reset()
    }
}