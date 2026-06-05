package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder
import com.sunnychung.lib.multiplatform.kotlite.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.ServerRuntimeState
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.KatariGeneratedBindingRuntime
import ru.hollowhorizon.hollowengine.common.utils.areStacksEqual

fun NarrativeBindingsBuilder.registerNpcRequestItemsBinding(server: MinecraftServer?) {
    if (server == null) {
        immediateMemberFunction(
            dispatchReceiverType = "NpcEntity",
            name = "requestItems",
            valueParameters = requestItemsParameters,
        )
        return
    }

    register(RequestItemsCallable(server))
}

private class RequestItemsCallable(
    private val server: MinecraftServer,
) : NarrativeCallable {
    override val id: String = "requestItems"
    override val receiverType: String = "NpcEntity"
    override val returnType: String = "Unit"
    override val typeParameters = emptyList<TypeParameter>()
    override val valueParameters: List<CustomFunctionParameter> = requestItemsParameters

    override suspend fun startCall(
        arguments: List<RuntimeValue>,
        context: NarrativeCallContext,
    ): NarrativeCallResult {
        return if (arguments.size <= 1) {
            NarrativeCallResult.Returned(NullValue)
        } else {
            NarrativeCallResult.SuspendedWithState(runtimeRemainingValue(arguments.drop(1), context))
        }
    }

    override suspend fun resumeCall(
        arguments: List<RuntimeValue>,
        response: FunctionResponse?,
        context: NarrativeCallContext,
    ): NarrativeCallResult {
        return when (response) {
            RequestItemsDone -> NarrativeCallResult.Returned(NullValue)
            is RequestItemsPending -> NarrativeCallResult.SuspendedWithState(response.remaining)
            is RequestItemsFailed -> error(response.message)
            else -> NarrativeCallResult.Suspended
        }
    }

    override fun dispatch(
        arguments: List<RuntimeValue>,
        context: NarrativeCallDispatchContext,
        resume: (FunctionResponse?) -> Unit,
    ) {
        server.coroutineScope.launch {
            val result = runCatching {
                val npc = KatariGeneratedBindingRuntime.awaitHost<NpcEntity>(
                    arguments.getOrNull(0),
                    "NpcEntity",
                    "receiver",
                )
                val state = context.suspendedState ?: runtimeRemainingValue(arguments.drop(1), context).also {
                    HollowEngine.LOGGER.warn(
                        "Katari requestItems for NPC {} resumed without saved remaining item state. Rebuilding the request from original arguments.",
                        npc.uuid,
                    )
                }
                val remaining = remainingItems(state)
                if (npc.pickupRequestedItems(remaining)) {
                    // Без этого он не сохранит
                    ServerRuntimeState.context(server).markDirty()
                }
                if (remaining.isEmpty()) {
                    RequestItemsDone
                } else {
                    val updatedRemaining = itemStackRemainingValue(remaining, context)
                    context.updateSuspendedState(updatedRemaining)
                    delay(50)
                    RequestItemsPending(updatedRemaining)
                }
            }
            resume(
                result.getOrElse { error ->
                    RequestItemsFailed(error.message ?: error::class.java.simpleName)
                }
            )
        }
    }

    private suspend fun remainingItems(value: RuntimeValue): MutableList<ItemStack> {
        return KatariGeneratedBindingRuntime.awaitList(value, "items") { item, index ->
            KatariGeneratedBindingRuntime.awaitHost<ItemStack>(item, "ItemStack", "items[$index]").copy()
        }
            .filterNot(ItemStack::isEmpty)
            .toMutableList()
    }

    private fun itemStackRemainingValue(
        items: List<ItemStack>,
        context: NarrativeCallContext,
    ): RuntimeValue {
        val values = items
            .filterNot(ItemStack::isEmpty)
            .map { item -> KatariGeneratedBindingRuntime.toRuntimeValue(item.copy(), "ItemStack", context.symbolTable) }
        val elementType = values.firstOrNull()?.type() ?: context.symbolTable.AnyType
        return ListValue(values, elementType, context.symbolTable)
    }

    private fun runtimeRemainingValue(
        values: List<RuntimeValue>,
        context: NarrativeCallContext,
    ): RuntimeValue {
        val elementType = values.firstOrNull()?.type() ?: context.symbolTable.AnyType
        return ListValue(values, elementType, context.symbolTable)
    }
}

private data object RequestItemsDone : FunctionResponse

private data class RequestItemsPending(
    val remaining: RuntimeValue,
) : FunctionResponse

private data class RequestItemsFailed(
    val message: String,
) : FunctionResponse

private val requestItemsParameters = listOf(
    CustomFunctionParameter("items", "ItemStack", modifiers = setOf("vararg")),
)

private fun NpcEntity.pickupRequestedItems(list: MutableList<ItemStack>): Boolean {
    var changed = false
    level().getEntitiesOfClass(
        ItemEntity::class.java,
        this.boundingBox.inflate(pickupDistance.x.toDouble(), pickupDistance.y.toDouble(), pickupDistance.z.toDouble())
    ).forEach { item ->
        if (item.isRemoved || item.item.isEmpty || item.hasPickUpDelay()) return@forEach

        val entityItem = item.item

        list.find { requestItem -> requestItem.areStacksEqual(entityItem) }?.let { requestItem ->
            val requestedCount = requestItem.count
            requestItem.shrink(entityItem.count)
            entityItem.shrink(requestedCount)
            changed = true
        }

        list.removeIf(ItemStack::isEmpty)
    }

    return changed || list.isEmpty()
}
