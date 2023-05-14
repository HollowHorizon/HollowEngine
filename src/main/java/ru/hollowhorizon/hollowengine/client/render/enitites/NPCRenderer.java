package ru.hollowhorizon.hollowengine.client.render.enitites;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import ru.hollowhorizon.hc.HollowCore;
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity;
import ru.hollowhorizon.hollowengine.common.npcs.IconType;
import ru.hollowhorizon.hollowengine.common.registry.ModItems;

import java.util.Arrays;
import java.util.Optional;

public class NPCRenderer extends EntityRenderer<NPCEntity> {

    public NPCRenderer(EntityRendererManager p_i46179_1_) {
        super(p_i46179_1_);
    }

    @Override
    public void render(NPCEntity entity, float entityYaw, float partialTicks, MatrixStack stack, IRenderTypeBuffer buffer, int packetLight) {
        super.render(entity, entityYaw, partialTicks, stack, buffer, packetLight);

//        if (entity.getPuppet() != null) {
//            Entity puppet = entity.getPuppet();
//
//            EntityRenderer<? super Entity> manager = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(puppet);
//
//            puppet.tickCount = entity.tickCount;
//
//            puppet.setPos(entity.getX(), entity.getY(), entity.getZ());
//            puppet.setDeltaMovement(entity.getDeltaMovement());
//
//            puppet.xo = entity.xo;
//            puppet.yo = entity.yo;
//            puppet.zo = entity.zo;
//            puppet.xOld = entity.xOld;
//            puppet.yOld = entity.yOld;
//            puppet.zOld = entity.zOld;
//
//            puppet.yRot = entity.yRot;
//            puppet.xRot = entity.xRot;
//            puppet.xRotO = entity.xRotO;
//            puppet.yRotO = entity.yRotO;
//
//            puppet.setShiftKeyDown(entity.isShiftKeyDown());
//            puppet.setSprinting(entity.isSprinting());
//
//            if (puppet instanceof LivingEntity) {
//                LivingEntity livingPuppet = (LivingEntity) puppet;
//
//                livingPuppet.walkDist = entity.walkDist;
//                livingPuppet.walkDistO = entity.walkDistO;
//
//                livingPuppet.swinging = entity.swinging;
//                livingPuppet.swingTime = entity.swingTime;
//                livingPuppet.swingingArm = entity.swingingArm;
//
//                livingPuppet.hurtTime = entity.hurtTime;
//                livingPuppet.deathTime = entity.deathTime;
//                livingPuppet.hurtDir = entity.hurtDir;
//                livingPuppet.hurtMarked = entity.hurtMarked;
//
//                livingPuppet.blocksBuilding = entity.blocksBuilding;
//
//                livingPuppet.animationPosition = entity.animationPosition;
//                livingPuppet.animationSpeedOld = entity.animationSpeedOld;
//                livingPuppet.animationSpeed = entity.animationSpeed;
//                livingPuppet.attackAnim = entity.attackAnim;
//
//                livingPuppet.yBodyRot = entity.yBodyRot;
//                livingPuppet.yBodyRotO = entity.yBodyRotO;
//
//                livingPuppet.yHeadRot = entity.yHeadRot;
//                livingPuppet.yHeadRotO = entity.yHeadRotO;
//
//                Arrays.stream(EquipmentSlotType.values()).forEach(slot -> livingPuppet.setItemSlot(slot, entity.getItemBySlot(slot)));
//
//                livingPuppet.setPose(entity.getPose());
//            }
//
//            manager.render(puppet, entityYaw, partialTicks, stack, renderType -> {
//                final IVertexBuilder builder;
//
//                if (renderType instanceof RenderType.Type) {
//                    Optional<ResourceLocation> rs = ((RenderType.Type) renderType).state.textureState.texture;
//
//                    if (rs.isPresent()) {
//                        RenderType newType = RenderType.entityTranslucent(rs.get());
//
//                        if (!newType.format().equals(renderType.format()))
//                            return buffer.getBuffer(renderType);
//
//                        builder = buffer.getBuffer(newType);
//                    } else {
//                        HollowCore.LOGGER.info("No texture found for render type: {}", renderType);
//                        return buffer.getBuffer(renderType);
//                    }
//                } else {
//                    HollowCore.LOGGER.info("Render type is not instance of RenderType.Type: {}", renderType);
//                    return buffer.getBuffer(renderType);
//                }
//
//                return builder;
//            }, packetLight);
//        }

        this.renderInteraction(entity, stack, buffer, packetLight, LivingRenderer.getOverlayCoords(entity, 0.0f));
    }

    private void renderInteraction(NPCEntity entity, MatrixStack stack, IRenderTypeBuffer buffer, int light, int overlay) {
        if(entity.getEntityIcon() == IconType.NONE) return;

        float height = entity.getBbHeight() + 0.7f;
        stack.pushPose();
        stack.translate(0f, height, 0f);
        stack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        stack.scale(0.8f,0.8f,0.8f);

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        ItemStack icon = ItemStack.EMPTY;
        switch (entity.getEntityIcon()) {
            case DIALOGUE:
                icon = new ItemStack(ModItems.DIALOGUE_ICON);
                break;
            case WARNING:
                icon = new ItemStack(ModItems.WARN_ICON);
                break;
            case QUESTION:
                icon = new ItemStack(ModItems.QUESTION_ICON);
                break;
            case NONE:
                break;
        }

        itemRenderer.renderStatic(icon, ItemCameraTransforms.TransformType.GROUND, light, overlay, stack, buffer);

        stack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(NPCEntity entity) {
        return null;
    }
}
