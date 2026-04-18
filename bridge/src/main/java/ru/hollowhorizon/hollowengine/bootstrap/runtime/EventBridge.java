package ru.hollowhorizon.hollowengine.bootstrap.runtime;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

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

    void onGetTooltip(ItemStack stack, Item.TooltipContext tooltipContext, TooltipFlag tooltipType, List<Component> lines);

    void onClientTick(Minecraft minecraft);

    void onClientCommandRegistration(CommandDispatcher<SharedSuggestionProvider> dispatcher, CommandBuildContext ctx);

    interface ShaderRegistration {
        void register(ResourceLocation id, VertexFormat vertexFormat, Consumer<ShaderInstance> loadCallback) throws IOException;
    }

    interface ReloadListenerRegistration {
        void register(PreparableReloadListener listener);
    }
}
