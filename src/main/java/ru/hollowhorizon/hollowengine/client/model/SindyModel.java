package ru.hollowhorizon.hollowengine.client.model;// Made with Blockbench 4.6.4
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.entity.model.SegmentedModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.merchant.villager.AbstractVillagerEntity;
import net.minecraft.util.math.MathHelper;

public class SindyModel<T extends Entity> extends SegmentedModel<T> {
    private final ModelRenderer head;
    private final ModelRenderer nose;
    private final ModelRenderer headwear;
    private final ModelRenderer body;
    private final ModelRenderer bodywear;
    private final ModelRenderer arms;
    private final ModelRenderer right_leg;
    private final ModelRenderer left_leg;

    public SindyModel() {
        this.head = new ModelRenderer(this).setTexSize(64, 64);
        this.head.texOffs(20, 17).addBox(-4.0F, -5.1F, -4.0F, 8.0F, 9.0F, 8.0F);

        this.nose = new ModelRenderer(this).setTexSize(64, 64);
        this.nose.texOffs(0, 0).addBox(-0.5F, 2.2F, -4.6F, 1.0F, 2.0F, 1.0F);
        this.nose.setPos(0.0F, -2.0F, 0.0F);
        this.head.addChild(this.nose);

        this.headwear = new ModelRenderer(this).setTexSize(64, 64);
        this.headwear.texOffs(10, 47).addBox(-4.0F, -5.0F, -4.0F, 8.0F, 9.0F, 8.0F, 0.51F);
        this.head.addChild(this.headwear);

        this.body = new ModelRenderer(this).setTexSize(64, 64);
        this.body.texOffs(28, 0).addBox(-4.0F, 3.9F, -3.0F, 8.0F, 11.0F, 6.0F);

        this.bodywear = new ModelRenderer(this).setTexSize(64, 64);
        this.bodywear.texOffs(0, 0).addBox(-4.0F, 3.9F, -3.0F, 8.0F, 16.0F, 6.0F, 0.5F);
        this.body.addChild(bodywear);

        this.arms = new ModelRenderer(this).setTexSize(64, 64);
        this.arms
                .texOffs(0, 34).addBox(-4.0F, 5.3403F, 1.1824F, 8.0F, 3.0F, 3.0F)
                .texOffs(40, 34).addBox(-7.0F, 0.3403F, 1.1824F, 3.0F, 8.0F, 3.0F);
        this.arms.setPos(0.0F, 2.95F, -1.05F);
        this.arms.xRot = -0.7505F;

        ModelRenderer mirrored = new ModelRenderer(this).setTexSize(64, 64);
        mirrored.texOffs(40, 34).addBox(4.0F, -20.7097F, 0.1324F, 3.0F, 8.0F, 3.0F);
        mirrored.setPos(0.0F, 21.05F, 1.05F);
        this.arms.addChild(mirrored);

        this.right_leg = new ModelRenderer(this).setTexSize(64, 64);
        this.right_leg.texOffs(0, 40).addBox(-2.0F, 2.9F, -2.0F, 4.0F, 9.0F, 4.0F);
        this.right_leg.setPos(-2.0F, 12.0F, 0.0F);

        this.left_leg = new ModelRenderer(this).setTexSize(64, 64);
        this.left_leg.texOffs(0, 40).addBox(-2.0F, 2.9F, -2.0F, 4.0F, 9.0F, 4.0F);
        this.left_leg.setPos(2.0F, 12.0F, 0.0F);

        ModelRenderer eyes = new ModelRenderer(this).setTexSize(64, 64);
        eyes.texOffs(0, 30).addBox(-2.3F, -24.1F, -4.1F, 1.0F, 1.0F, 1.0F)
                .texOffs(0, 30).addBox(1.3F, -24.1F, -4.1F, 1.0F, 1.0F, 1.0F)
                .texOffs(11, 25).addBox(1.0F, -25.1F, -4.1F, 2.0F, 1.0F, 1.0F)
                .texOffs(12, 25).addBox(-3.0F, -25.1F, -4.1F, 2.0F, 1.0F, 1.0F);
        eyes.setPos(0.0F, 24.0F, 0.0F);
        this.head.addChild(eyes);

        ModelRenderer wear2 = new ModelRenderer(this).setTexSize(64, 64);
        wear2.texOffs(45, 57).addBox(-4.0F, -9.1F, -3.0F, 8.0F, 6.0F, 1.0F);
        wear2.setPos(0.0F, 24.0F, 0.0F);
        this.bodywear.addChild(wear2);

        ModelRenderer hair = new ModelRenderer(this).setTexSize(64, 64);
        hair.texOffs(25, 37).addBox(-3.0F, -19.5F, 2.8F, 6.0F, 8.0F, 1.0F);
        hair.setPos(0.0F, 24.0F, 0.0F);
        this.bodywear.addChild(hair);


    }

    public Iterable<ModelRenderer> parts() {
        return ImmutableList.of(this.head, this.body, this.arms, this.right_leg, this.left_leg);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean flag = false;
        if (entity instanceof AbstractVillagerEntity) {
            flag = ((AbstractVillagerEntity) entity).getUnhappyCounter() > 0;
        }

        this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.head.xRot = headPitch * ((float) Math.PI / 180F);
        if (flag) {
            this.head.zRot = 0.3F * MathHelper.sin(0.45F * ageInTicks);
            this.head.xRot = 0.4F;
        } else {
            this.head.zRot = 0.0F;
        }

        this.arms.y = 3.0F;
        this.arms.z = -1.0F;
        this.arms.xRot = -0.75F;
        this.left_leg.xRot = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount * 0.5F;
        this.right_leg.xRot = MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount * 0.5F;
        this.left_leg.yRot = 0.0F;
        this.right_leg.yRot = 0.0F;
    }
}