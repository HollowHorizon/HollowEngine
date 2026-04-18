package ru.hollowhorizon.hollowengine.bootstrap.runtime;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface EventBridge {
    void onRegisterShaders(ShaderRegistration registration);

    void onRegisterEntityRenderers(BiConsumer<EntityType<? extends Entity>, EntityRendererProvider<Entity>> consumer);

    void onRegisterBlockEntityRenderers(BiConsumer<BlockEntityType<? extends BlockEntity>, BlockEntityRendererProvider<BlockEntity>> consumer);

    void onRegisterKeybindings(Consumer<KeyMapping> consumer);

    void onRegisterReloadListeners(ReloadListenerRegistration registration);

    void onRegisterServerReloadListeners(ReloadListenerRegistration registration);

    void onRegisterEntityAttributes(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> consumer);

    void onGetTooltip(ItemStack stack, Item.TooltipContext tooltipContext, TooltipFlag tooltipType, List<Component> lines);

    void onClientTick(Minecraft minecraft);

    void onServerTick(MinecraftServer server);

    void onRegisterCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx, Commands.CommandSelection environment);

    void onClientCommandRegistration(CommandDispatcher<SharedSuggestionProvider> dispatcher, CommandBuildContext ctx);

    void onEntityTrackingStart(ServerPlayer player, Entity entity);

    void onEntityTrackingStop(ServerPlayer player, Entity entity);

    void onPlayerJoin(ServerPlayer player);

    void onPlayerLeave(ServerPlayer player);

    void onPlayerChangeDimension(ServerPlayer player, ServerLevel from, ServerLevel to);

    void onEntityLoad(Entity entity);

    boolean onBlockBreak(Level level, BlockPos pos, BlockState state, ServerPlayer player);

    void onBuildTabContents(CreativeModeTab tab, ResourceKey<CreativeModeTab> tabKey, CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output);

    interface ShaderRegistration {
        void register(ResourceLocation id, VertexFormat vertexFormat, Consumer<ShaderInstance> loadCallback) throws IOException;
    }

    interface ReloadListenerRegistration {
        void register(PreparableReloadListener listener);
    }

}
