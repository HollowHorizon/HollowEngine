package ru.hollowhorizon.hollowengine.client.screen.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.data.ModelData;
import org.lwjgl.opengl.GL11;
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget;
import ru.hollowhorizon.hc.client.utils.ScissorUtil;
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity;

import java.util.ArrayList;
import java.util.List;

import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;

public class ModelPreviewWidget extends HollowWidget {
    protected static final int BORDER_WIDTH = 2;
    protected static final int BUTTON_SIZE = 20;
    protected static final ResourceLocation BUTTONS_TEXTURE =
            new ResourceLocation(MODID, "textures/gui/preview_buttons.png");
    protected static final int BUTTONS_TEXTURE_HEIGHT = 64;
    protected static final int BUTTONS_TEXTURE_WIDTH = 128;
    protected static final Minecraft MC = Minecraft.getInstance();
    protected static boolean renderBoundingBoxes;
    protected static boolean renderFloor = true;
    protected static boolean showPlayer = false;
    protected static boolean doTurntable = false;
    protected final List<IconButton> buttons = new ArrayList<>();
    protected final IconButton resetButton;
    protected final IconButton fullscreenButton;
    protected final IconButton[] bottomButtons;
    protected final NPCEntity previeNPC;
    protected final LabelWidget title;
    protected final int originalX;
    protected final int originalY;
    protected final int originalWidth;
    protected final int originalHeight;
    private final int parentWidth;
    private final int parentHeight;
    protected float previewScale;
    protected float previewYaw;
    protected float previewPitch;
    protected float previewX;
    protected float previewY;
    protected float fullscreenness;
    protected boolean fullscreen;
    protected boolean transitioning;

    public ModelPreviewWidget(
            int x,
            int y,
            int width,
            int height,
            int pwidth,
            int pheight) {
        super(x, y, width, height, Component.literal("Model Preview"));
        this.originalX = x;
        this.originalY = y;
        this.originalWidth = width;
        this.originalHeight = height;
        this.parentWidth = pwidth;
        this.parentHeight = pheight;
        this.previeNPC = new NPCEntity(MC.level);
        Button.OnTooltip tooltip = (button, matrixStack, mouseX, mouseY) -> {

        };
        resetButton =
                new IconButton(0, 0,
                        BUTTON_SIZE, BUTTON_SIZE,
                        new ResourceLocation("hollowengine:textures/gui/reload.png"),
                        0,
                        0,
                        BUTTON_SIZE * 2,
                        BUTTON_SIZE,
                        BUTTON_SIZE,
                        b -> this.resetPreview(),
                        tooltip,
                        Component.literal("Сбросить предпросмотр")
                );
        this.buttons.add(resetButton);
        fullscreenButton =
                this.makeButton(
                        1,
                        b -> {
                            transitioning = true;
                            fullscreen = !fullscreen;
                        },
                        tooltip,
                        Component.literal("Полноэкранный Режим"));
        bottomButtons =
                new IconButton[]{
                        this.makeButton(
                                4,
                                b -> doTurntable = !doTurntable,
                                tooltip,
                                Component.literal("Поворачивать Модель")),
                        this.makeButton(
                                3,
                                b -> renderFloor = !renderFloor,
                                tooltip,
                                Component.literal("Отображать Землю")),
                        this.addButton(
                                new PlayerIconButton(
                                        0,
                                        0,
                                        MC.getUser().getGameProfile(),
                                        b -> showPlayer = !showPlayer,
                                        tooltip,
                                        Component.literal("Отображать Игрока"))),
                        this.makeButton(
                                2,
                                b -> renderBoundingBoxes = !renderBoundingBoxes,
                                tooltip,
                                Component.literal("Отображать Хитбоксы"))
                };
        title =
                new LabelWidget(
                        0,
                        0,
                        MC.font,
                        LabelWidget.AnchorX.CENTER,
                        LabelWidget.AnchorY.CENTER,
                        this.getMessage());
        this.resetPreview();
        this.resetWidgetPositions();
    }

    protected IconButton makeButton(
            int index, Button.OnPress press, Button.OnTooltip tooltip, Component title) {
        // x and y are controlled by resetWidgetPositions
        final IconButton button =
                new IconButton(
                        0,
                        0,
                        BUTTON_SIZE,
                        BUTTON_SIZE,
                        BUTTONS_TEXTURE,
                        BUTTON_SIZE * index,
                        0,
                        BUTTONS_TEXTURE_HEIGHT,
                        BUTTONS_TEXTURE_WIDTH,
                        BUTTON_SIZE,
                        press,
                        tooltip,
                        title);
        return this.addButton(button);
    }

    protected IconButton addButton(IconButton button) {
        buttons.add(button);
        return button;
    }

    public void resetPreview() {
        previewScale = 2.0F;
        previewYaw = 135.0F;
        previewPitch = -25.0F;
        previewX = 0.0F;
        previewY = 0.0F;
    }

    public float getFullscreenness() {
        return fullscreenness;
    }

    protected void resetWidgetPositions() {
        final int top = y + BORDER_WIDTH;
        final int left = x + BORDER_WIDTH;
        final int right = x + width - BUTTON_SIZE - BORDER_WIDTH;
        final int bottom = y + height - BORDER_WIDTH - BUTTON_SIZE;

        resetButton.x = left;
        resetButton.y = top;
        title.x = x + width / 2;
        title.y = y + 12;
        fullscreenButton.x = right;
        fullscreenButton.y = top;

        final int length = bottomButtons.length;
        final int startX = x + width / 2 - length * BUTTON_SIZE / 2;
        for (int i = 0; i < length; i++) {
            final IconButton button = bottomButtons[i];
            button.x = startX + BUTTON_SIZE * i;
            button.y = bottom;
        }
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    @Override
    public void playDownSound(SoundManager p_230988_1_) {
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.isHovered) return false;

        if (button == 0) {
            previewYaw += (float) (dragX * 0.5F);
            previewPitch -= (float) (dragY * 0.5F);
            if (previewPitch > 90.0F) {
                previewPitch = 90.0F;
            } else if (previewPitch < -90.0F) {
                previewPitch = -90.0F;
            }
            return true;
        } else if (button == 1) {
            previewX += (float) (dragX);
            previewY += (float) (dragY);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        previewScale = Math.max((float) (delta * 0.15F) + previewScale, Float.MIN_VALUE);
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void renderButton(PoseStack stack, int mouseX, int mouseY, float partialTicks) {


        if (transitioning) {
            final float speed = 0.03F;
            if (fullscreen) {
                if (fullscreenness < 1.0F) {
                    fullscreenness += speed;
                    if (fullscreenness > 1.0F) {
                        fullscreenness = 1.0F;
                        transitioning = false;
                    }
                } else {
                    transitioning = false;
                }
            } else {
                if (fullscreenness > 0.0F) {
                    fullscreenness -= speed;
                    if (fullscreenness < 0.0F) {
                        fullscreenness = 0.0F;
                        transitioning = false;
                    }
                } else {
                    transitioning = false;
                }
            }
            x = (int) Mth.lerp(fullscreenness, originalX, 0.0F);
            y = (int) Mth.lerp(fullscreenness, originalY, 0.0F);
            width = Mth.ceil(Mth.lerp(fullscreenness, originalWidth, parentWidth));
            height = Mth.ceil(Mth.lerp(fullscreenness, originalHeight, parentHeight));
            this.resetWidgetPositions();
        }
        if (doTurntable) {
            previewYaw += 0.4F;
        }


        final AABB bounds = new AABB(0, 0, 0, 0, 0, 0);
        final var window = MC.getWindow();
        final double guiScale = window.getGuiScale();
        fill(stack, x, y, x + width, y + height, 0x66FFFFFF);
        ScissorUtil.start(
                x + BORDER_WIDTH,
                y + BORDER_WIDTH,
                width - BORDER_WIDTH * 2,
                height - BORDER_WIDTH * 2);
        fillGradient(stack, x, y, x + width, y + height, 0x66000000, 0xCC000000);

        // We reset the projection matrix here in order to change the clip plane distances
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(
                0.0D,
                (double) window.getWidth() / guiScale,
                (double) window.getHeight() / guiScale,
                0.0D,
                10.0D,
                300000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

        GL11.glTranslatef(
                x + (width / 2.0F) + previewX, y + (height / 2.0F) + previewY, 0.0F);
        GL11.glScalef(1.0F, 1.0F, -1.0F);
        final PoseStack entityPS = new PoseStack();
        entityPS.translate(0.0D, 0.0D, -400.0D);
        // This is me trying to make some scale to fit thing... poorly
        final int dm = Math.min(width, height);
        final double boundsW =
                bounds.minX > bounds.maxX ? bounds.minX - bounds.maxX : bounds.maxX - bounds.minX;
        final double boundsH =
                bounds.minY > bounds.maxY ? bounds.minY - bounds.maxY : bounds.maxY - bounds.minY;
        final double bm = Math.max(boundsW, boundsH);
        final float scale = (float) (dm / (bm * guiScale)) * previewScale;
        entityPS.scale(scale, scale, scale);
        Quaternion quaternion = Vector3f.ZP.rotationDegrees(180.0F);
        Quaternion quaternion1 = Vector3f.XP.rotationDegrees(previewPitch);
        Quaternion quaternion2 = Vector3f.YP.rotationDegrees(previewYaw);
        quaternion1.mul(quaternion2);
        quaternion.mul(quaternion1);
        entityPS.mulPose(quaternion);
        quaternion1.conj();
        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        final double yOff = -(bounds.minY + (bounds.maxY / 2.0D));
        // Render the floor
        if (renderFloor) {
            entityPS.pushPose();
            entityPS.translate(-0.5F, yOff - 1.0F, -0.5F);
            MC.getBlockRenderer()
                    .renderSingleBlock(
                            Blocks.GRASS_BLOCK.defaultBlockState(),
                            entityPS,
                            buffers,
                            0xF000F0,
                            OverlayTexture.NO_OVERLAY,
                            ModelData.EMPTY,
                            RenderType.solid());
            entityPS.popPose();
        }
        // Render the preview puppet
        var dispatcher = MC.getEntityRenderDispatcher();
        dispatcher.overrideCameraOrientation(quaternion1);
        dispatcher.setRenderShadow(false);
        final boolean renderHitBoxes = dispatcher.shouldRenderHitBoxes();
        dispatcher.setRenderHitBoxes(renderBoundingBoxes);
        RenderSystem.runAsFancy(
                () -> {
                    dispatcher.render(
                            previeNPC,
                            0.0D,
                            yOff,
                            0.0D,
                            0.0F,
                            partialTicks,
                            entityPS,
                            buffers,
                            0xF000F0);
                    this.renderFire(entityPS, buffers, dispatcher, yOff);
                });
        // Render the player for scale
        if (showPlayer && MC.player != null) {
            final float xOff = -Math.max(1.0F, (float) bounds.maxX + 0.5F);
            if (renderFloor) {
                entityPS.pushPose();
                entityPS.translate(xOff - 0.5F, yOff - 1.0F, -0.5F);
                MC.getBlockRenderer()
                        .renderSingleBlock(
                                Blocks.GRASS_BLOCK.defaultBlockState(),
                                entityPS,
                                buffers,
                                0xF000F0,
                                OverlayTexture.NO_OVERLAY,
                                ModelData.EMPTY,
                                RenderType.solid());
                entityPS.popPose();
            }
            dispatcher.setRenderHitBoxes(false);
            //PlayerRotationHelper.save();
            //PlayerRotationHelper.clear();
            RenderSystem.runAsFancy(
                    () ->
                            dispatcher.render(
                                    MC.player,
                                    xOff,
                                    yOff,
                                    0.0D,
                                    0.0F,
                                    partialTicks,
                                    entityPS,
                                    buffers,
                                    0xF000F0));
            //PlayerRotationHelper.load();
        }
        dispatcher.setRenderHitBoxes(renderHitBoxes);
        dispatcher.setRenderShadow(true);

        buffers.endBatch();
        GL11.glPopMatrix();
        ScissorUtil.stop();

        stack.pushPose();
        stack.translate(0, 0, 1000);
        for (final IconButton button : buttons) {
            button.render(stack, mouseX, mouseY, partialTicks);
        }
        title.render(stack, mouseX, mouseY, partialTicks);
        stack.popPose();
    }

    @Override
    public boolean mouseClicked(double p_231044_1_, double p_231044_3_, int p_231044_5_) {
        for (IconButton button : this.buttons) {
            if (button.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_)) return true;
        }
        return super.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
    }

    protected void renderFire(
            PoseStack entityPS,
            MultiBufferSource.BufferSource buffers,
            EntityRenderDispatcher manager,
            double yOff) {
        // The preview copy will never be on fire otherwise, so doing this directly is fine
        if (false) {
            entityPS.pushPose();
            entityPS.translate(0.0D, yOff, 0.0D);
            entityPS.mulPose(Vector3f.YP.rotationDegrees(manager.camera.getYRot() - previewYaw));
            //manager.renderFlame(entityPS, buffers, previewPuppet);
            entityPS.popPose();
        }
    }
}
