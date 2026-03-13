package ru.hollowhorizon.hollowengine.common.codeblocks.validation

import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.*
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.root
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.VariableScope
import ru.hollowhorizon.hollowengine.common.codeblocks.walk
import java.util.*

object CodeBlockAnalysisService {
    fun analyze(rootBlocks: List<BlockModel>): CodeBlockAnalysis {
        return analyzeScripts(mapOf(null to rootBlocks))
    }

    fun analyzeScripts(scripts: Map<String?, List<BlockModel>>): CodeBlockAnalysis {
        val roots = scripts.flatMap { (path, blocks) ->
            blocks.map { ScriptRoot(path, it) }
        }
        val globalTypes = linkedMapOf<String, ExpressionType>()
        val localTypesByRoot = linkedMapOf<UUID, LinkedHashMap<String, ExpressionType>>()
        val issues = linkedMapOf<String, ValidationIssue>()
        val maxPasses = maxOf(1, roots.sumOf { it.root.walk().count() } + 1)
        var pass = 0
        var changed = true
        while (changed && pass < maxPasses) {
            pass++
            changed = runLocalAnalysisPass(roots, localTypesByRoot, globalTypes, issues)
            changed = runGlobalAnalysisPass(roots, localTypesByRoot, globalTypes, issues) || changed
        }

        roots.forEach { scriptRoot ->
            val localTypes = localTypesByRoot[scriptRoot.root.uuid].orEmpty()
            scriptRoot.root.walk().forEach { block ->
                when (block) {
                    is GetVarBlock -> reportUnknownLocal(block, block.varName, localTypes, issues, scriptRoot.path)
                    is GetVarInlineBlock -> reportUnknownLocal(block, block.name, localTypes, issues, scriptRoot.path)
                    is GetGlobalVarBlock -> reportUnknownGlobal(block, block.variableName, globalTypes, issues, scriptRoot.path)
                    else -> block.asEventOutputBinding()?.let { validateEventOutput(it.block, issues, scriptRoot.path) }
                }
            }
        }

        return CodeBlockAnalysis(globalTypes, localTypesByRoot, issues.values.toList())
    }

    fun resolveLocalVariableType(block: BlockModel, variableName: String): ExpressionType {
        if (variableName.isBlank()) return AnyType
        val scope = block.scope ?: return AnyType
        val analysis = analyze(scope.rootBlocks.toList())
        return analysis.localTypesByRoot[block.root.uuid]?.get(variableName) ?: AnyType
    }

    fun resolveGlobalVariableType(block: BlockModel, variableName: String): ExpressionType {
        if (variableName.isBlank()) return AnyType
        val scope = block.scope ?: return AnyType
        val analysis = analyze(scope.rootBlocks.toList())
        return analysis.globalTypes[variableName] ?: AnyType
    }

    private fun reportUnknownLocal(
        block: BlockModel,
        name: String,
        localTypes: Map<String, ExpressionType>,
        issues: MutableMap<String, ValidationIssue>,
        scriptPath: String?,
    ) {
        if (name.isBlank() || localTypes.containsKey(name)) return
        issues.putIfAbsent(
            "local:get:${block.uuid}",
            ValidationIssue(block.uuid.toString(), "Unknown local variable '$name'.", scriptPath),
        )
    }

    private fun reportUnknownGlobal(
        block: BlockModel,
        name: String,
        globalTypes: Map<String, ExpressionType>,
        issues: MutableMap<String, ValidationIssue>,
        scriptPath: String?,
    ) {
        if (name.isBlank() || globalTypes.containsKey(name)) return
        issues.putIfAbsent(
            "global:get:${block.uuid}",
            ValidationIssue(block.uuid.toString(), "Unknown global variable '$name'.", scriptPath),
        )
    }

    private fun validateEventOutput(block: ExpressionBlock, issues: MutableMap<String, ValidationIssue>, scriptPath: String?) {
        if (block.parentBlock == null || block.parentOutputName == null || inferEventOutputType(block) == null) {
            issues.putIfAbsent(
                "event_output:${block.uuid}",
                ValidationIssue(block.uuid.toString(), "Event output block must be connected to a typed output slot.", scriptPath),
            )
        }
    }

    private fun inferAssignedType(
        expression: BlockModel?,
        localTypes: Map<String, ExpressionType>,
        globalTypes: Map<String, ExpressionType>,
    ): ExpressionType? {
        val block = expression as? ExpressionBlock ?: return null
        return when (block) {
            is GetVarBlock -> localTypes[block.varName]
            is GetVarInlineBlock -> localTypes[block.name]
            is GetGlobalVarBlock -> globalTypes[block.variableName]
            is EventOutputLocalVariableBlock,
            is EventOutputGlobalVariableBlock -> inferEventOutputType(block)
            else -> block.expressionType.takeUnless { it == AnyType }
        }
    }

    private fun inferEventOutputType(block: ExpressionBlock): ExpressionType? {
        val parent = block.parentBlock ?: return null
        val outputName = block.parentOutputName ?: return null
        return parent.outputTypes[outputName]?.takeUnless { it == AnyType }
    }

    private fun BlockModel.asEventOutputBinding(): EventOutputBinding? {
        val binding = this as? EventOutputVariableBinding ?: return null
        val expression = this as? ExpressionBlock ?: return null
        return EventOutputBinding(expression, binding.variableName, binding.variableScope)
    }

    private fun mergeType(
        issues: MutableMap<String, ValidationIssue>,
        types: MutableMap<String, ExpressionType>,
        name: String,
        inferredType: ExpressionType?,
        block: BlockModel,
        scriptPath: String?,
        label: String,
    ): Boolean {
        if (name.isBlank() || inferredType == null || inferredType == AnyType) return false
        val current = types[name]
        if (current == null) {
            types[name] = inferredType
            return true
        }
        if (current.accepts(inferredType) && inferredType.accepts(current)) {
            return false
        }
        if (current.accepts(inferredType)) {
            return false
        }
        if (inferredType.accepts(current)) {
            types[name] = inferredType
            return true
        }
        issues.putIfAbsent(
            "$label:conflict:$name:${block.root.uuid}",
            ValidationIssue(
                block.uuid.toString(),
                "Conflicting $label variable type for '$name': $current vs $inferredType.",
                scriptPath,
            ),
        )
        return false
    }

    private fun runLocalAnalysisPass(
        roots: List<ScriptRoot>,
        localTypesByRoot: MutableMap<UUID, LinkedHashMap<String, ExpressionType>>,
        globalTypes: Map<String, ExpressionType>,
        issues: MutableMap<String, ValidationIssue>,
    ): Boolean {
        var changed = false
        roots.forEach { scriptRoot ->
            val localTypes = localTypesByRoot.getOrPut(scriptRoot.root.uuid, ::LinkedHashMap)
            scriptRoot.root.walk().forEach { block ->
                when (block) {
                    is SetVarBlock -> {
                        changed = mergeType(
                            issues,
                            localTypes,
                            block.variableName,
                            inferAssignedType(block.inputs["value"], localTypes, globalTypes),
                            block,
                            scriptRoot.path,
                            "local"
                        ) || changed
                    }

                    else -> {
                        val eventOutput = block.asEventOutputBinding()
                        if (eventOutput?.variableScope == VariableScope.LOCAL) {
                            changed = mergeType(
                                issues,
                                localTypes,
                                eventOutput.variableName,
                                inferEventOutputType(eventOutput.block),
                                eventOutput.block,
                                scriptRoot.path,
                                "local"
                            ) || changed
                        }
                    }
                }
            }
        }
        return changed
    }

    private fun runGlobalAnalysisPass(
        roots: List<ScriptRoot>,
        localTypesByRoot: Map<UUID, Map<String, ExpressionType>>,
        globalTypes: MutableMap<String, ExpressionType>,
        issues: MutableMap<String, ValidationIssue>,
    ): Boolean {
        var changed = false
        roots.forEach { scriptRoot ->
            val localTypes = localTypesByRoot[scriptRoot.root.uuid].orEmpty()
            scriptRoot.root.walk().forEach { block ->
                when (block) {
                    is SetGlobalVarBlock -> {
                        changed = mergeType(
                            issues,
                            globalTypes,
                            block.variableName,
                            inferAssignedType(block.inputs["value"], localTypes, globalTypes),
                            block,
                            scriptRoot.path,
                            "global"
                        ) || changed
                    }

                    else -> {
                        val eventOutput = block.asEventOutputBinding()
                        if (eventOutput?.variableScope == VariableScope.GLOBAL) {
                            changed = mergeType(
                                issues,
                                globalTypes,
                                eventOutput.variableName,
                                inferEventOutputType(eventOutput.block),
                                eventOutput.block,
                                scriptRoot.path,
                                "global"
                            ) || changed
                        }
                    }
                }
            }
        }
        return changed
    }

    data class CodeBlockAnalysis(
        val globalTypes: Map<String, ExpressionType>,
        val localTypesByRoot: Map<UUID, Map<String, ExpressionType>>,
        val issues: List<ValidationIssue>,
    )

    private data class ScriptRoot(
        val path: String?,
        val root: BlockModel,
    )

    private data class EventOutputBinding(
        val block: ExpressionBlock,
        val variableName: String,
        val variableScope: VariableScope,
    )
}
