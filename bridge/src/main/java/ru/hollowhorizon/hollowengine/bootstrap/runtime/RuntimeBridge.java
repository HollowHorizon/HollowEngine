package ru.hollowhorizon.hollowengine.bootstrap.runtime;

import com.mojang.datafixers.util.Either;
import com.mojang.blaze3d.audio.SoundBuffer;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.Set;

public interface RuntimeBridge extends AutoCloseable {
    boolean shouldApplyMixin(String targetClassName, String mixinClassName);

    boolean onPlayerInteractEntity(Player player, InteractionHand hand, Entity target);

    @Nullable ItemEntity onPlayerDrop(ItemStack stack, boolean includeThrowerName, @Nullable ItemEntity dropped, Player player);

    void onBrewedPlayerPotion(Player player, ItemStack stack);

    boolean onBrewPotionPre(NonNullList<ItemStack> stacks);

    void onBrewPotionPost(NonNullList<ItemStack> stacks);

    BreedResult onAnimalBreed(Animal self, Animal mate, @Nullable AgeableMob child);

    void onLivingEntityTick(LivingEntity entity);

    boolean onLivingEntityDeath(LivingEntity entity, DamageSource damageSource);

    RepositorySource[] augmentPackRepositorySources(RepositorySource[] providers);

    void onPlayerRespawn(ServerPlayer original, boolean returnFromEnd);

    ChatResult onServerChat(ServerPlayer player, Component content);

    void onServerLevelSave(ServerLevel level);

    boolean onServerLevelNeighborNotify(ServerLevel level, BlockPos pos, Set<Direction> sides);

    void onPlayerClone(ServerPlayer self, ServerPlayer oldPlayer, boolean wasDeath);

    @Nullable Either<Player.BedSleepingProblem, Unit> onPlayerSleepInBed(ServerPlayer player, BlockPos bedPos);

    void onPlayerWakeup(ServerPlayer player, boolean wakeImmediately, boolean updateLevelForSleepingPlayers);

    boolean onPlayerUseItemOn(ServerPlayer player, InteractionHand hand, BlockHitResult hitResult);

    boolean onPlayerUseItem(ServerPlayer player, InteractionHand hand, ItemStack stack);

    boolean onBlockPlaced(Player player, BlockState blockState, BlockPos pos);

    int onArrowLoose(ItemStack stack, Level level, Player player, int charge, boolean hasAmmo);

    @Nullable ItemStack onArrowNock(ItemStack stack, Level level, Player player, InteractionHand usedHand);

    void onRegisterTags(Object registry, Map<ResourceLocation, List<TagLoader.EntryWithSource>> value);

    @Nullable Path getStorageFolderOverride(ResourceKey<Level> dimensionKey, Path levelFolder);

    void onRegisterLoot(Map<LootDataId<?>, Object> elements);

    float getSkySunSize(ClientLevel level, float originalSize);

    float getSkyMoonSize(ClientLevel level, float originalSize);

    boolean shouldHideGui(@Nullable Screen currentScreen);

    Screen onScreenOpen(Screen screen);

    void onScreenClose(Screen screen);

    boolean onScreenRenderPre(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    void onScreenRenderPost(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick);

    void onRegisterParticles(ParticleEngine particleEngine);

    boolean onClientUseItemOn(Player player, InteractionHand hand, BlockHitResult hitResult);

    boolean onClientInteractEntity(Player player, InteractionHand hand, Entity target);

    boolean onClientUseItem(Player player, InteractionHand hand, ItemStack stack);

    void onDebugClientMain();

    @Nullable CompletableFuture<SoundBuffer> onLoadCompleteSound(ResourceLocation soundId, ResourceProvider resourceManager, Map<ResourceLocation, CompletableFuture<SoundBuffer>> cache);

    @Nullable CompletableFuture<AudioStream> onLoadStreamSound(ResourceLocation soundId, ResourceProvider resourceManager, boolean isWrapper);

    @Nullable FileToIdConverter createSoundConverter();

    @Nullable String getOpenGlVersionOverride();

    boolean shouldForceAutoGuiScale(@Nullable Screen screen);

    void onServerCreated(MinecraftServer server, Thread serverThread, Path levelRoot);

    void onServerStarting(MinecraftServer server);

    void onServerLevelsCreated(MinecraftServer server);

    void onServerTick(MinecraftServer server);

    void onServerStopping(MinecraftServer server);

    void onServerStopped(MinecraftServer server);

    void onClientCreated(Minecraft client);

    void onClientTick(Minecraft client);

    void onClientRenderTickPre(Minecraft client);

    void onClientRenderTickPost(Minecraft client);

    void onClientResized(Minecraft client);

    void onClientStopping(Minecraft client);

    void onLevelCreated(Level level);

    void onLevelUpdateNeighbors(Level level, BlockPos pos);

    void onLevelTickBlockEntities(Level level);

    void onLevelClosed(Level level);

    void onEntityCreated(Entity entity);

    void onEntitySaved(Entity entity, CompoundTag tag);

    void onEntityLoaded(Entity entity, CompoundTag tag);

    boolean onEntityHurt(Entity entity, DamageSource damageSource, float amount);

    void onEntityChangedDimension(Entity entity, @Nullable Entity resultEntity, Level fromLevel, Level toLevel);

    void onEntitySetLevel(Entity entity, Level level);

    void onEntityTeleported(Entity entity, Entity newEntity, Level fromLevel, ServerLevel toLevel);

    void onEntityRemoved(Entity entity);

    void onEntitySetId(Entity entity, int newId, int previousId);

    void onRecipeManagerCreated(RecipeManager recipeManager);

    void onAddEntityRendererLayers(
        Map<EntityType<?>, EntityRenderer<?>> renderers,
        Map<String, EntityRenderer<? extends Player>> playerRenderers,
        EntityRendererProvider.Context context
    );

    boolean onRenderEntityPre(Entity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight);

    void onRenderEntityPost(Entity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight);

    CameraSetup onCameraSetup(GameRenderer gameRenderer, Camera camera, float partialTick);

    void onRegisterLayerDefinitions(Map<ModelLayerLocation, Supplier<LayerDefinition>> definitions);

    void onCreateSkullModels(ImmutableMap.Builder<SkullBlock.Type, SkullModelBase> builder, EntityModelSet entityModelSet);

    boolean onRenderPlayer(AbstractClientPlayer player, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight);

    void onIrisPipelineDestroyed();

    void onIrisShadowRenderStart();

    void onIrisShadowRenderBeforeEndBatch();

    void onIrisShadowRenderEnd();

    boolean onRenderOverlayPre(Window window, GuiGraphics guiGraphics, float partialTick, OverlayKind overlayKind);

    void onRenderOverlayPost(Window window, GuiGraphics guiGraphics, float partialTick, OverlayKind overlayKind);

    @Nullable AbstractWidget createInventoryButton(InventoryScreen screen, int x, int y);

    void updateInventoryButton(@Nullable AbstractWidget widget, int x, int y);

    boolean onKeyboardKey(long windowPointer, int key, int scanCode, int action, int modifiers);

    boolean onKeyboardChar(long windowPointer, int codePoint, int modifiers);

    MouseMoveResult onMouseMove(Minecraft minecraft, long windowPointer, double xPos, double yPos);

    boolean onMousePress(Minecraft minecraft, float x, float y, long windowPointer, int button, int action, int modifiers);

    boolean onMouseScroll(Minecraft minecraft, float x, float y, long windowPointer, double xOffset, double yOffset);

    void onRenderLevelStage(LevelRenderer renderer, PoseStack poseStack, Matrix4f projectionMatrix, int ticks, float partialTick, Camera camera, @Nullable Frustum frustum, RenderLevelStage stage);

    void onCommonInitialize();

    void onClientInitialize();

    void onForgeInitialize();

    void onNeoForgeInitialize(Object modBus);

    @Override
    default void close() {
    }

    final class BreedResult {
        private final @Nullable AgeableMob child;
        private final boolean cancelled;

        public BreedResult(@Nullable AgeableMob child, boolean cancelled) {
            this.child = child;
            this.cancelled = cancelled;
        }

        public @Nullable AgeableMob getChild() {
            return child;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    final class ChatResult {
        private final Component message;
        private final boolean cancelled;

        public ChatResult(Component message, boolean cancelled) {
            this.message = message;
            this.cancelled = cancelled;
        }

        public Component getMessage() {
            return message;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    record CameraSetup(float yaw, float pitch, float roll) {
    }

    record MouseMoveResult(float x, float y, boolean cancel, boolean resetMousePosition) {
    }

    enum OverlayKind {
        VIGNETTE,
        SPYGLASS,
        HELMET,
        PORTAL,
        HOTBAR,
        CROSSHAIR,
        PLAYER_HEALTH,
        MOUNT_HEALTH,
        JUMP_BAR,
        EXPERIENCE_BAR,
        ITEM_NAME,
        POTION_ICONS,
        BOSS_EVENT_PROGRESS,
        CHAT_PANEL,
        DEBUG_TEXT
    }

    enum RenderLevelStage {
        AFTER_LEVEL,
        AFTER_SKY,
        AFTER_ENTITIES,
        AFTER_BLOCK_ENTITIES,
        AFTER_PARTICLES,
        AFTER_WEATHER,
        AFTER_SOLID_BLOCKS,
        AFTER_CUTOUT_MIPPED_BLOCKS,
        AFTER_CUTOUT_BLOCKS,
        AFTER_TRANSLUCENT_BLOCKS,
        AFTER_TRIPWIRE_BLOCKS
    }
}
