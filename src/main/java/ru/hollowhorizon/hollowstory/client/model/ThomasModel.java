package ru.hollowhorizon.hollowstory.client.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.entity.model.SegmentedModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.merchant.villager.AbstractVillagerEntity;
import net.minecraft.util.math.MathHelper;

public class ThomasModel<T extends Entity> extends SegmentedModel<T> {
    private final ModelRenderer head;
    private final ModelRenderer headwear;
    private final ModelRenderer headwear2;

    private final ModelRenderer nose;
    private final ModelRenderer body;
    private final ModelRenderer bodywear;
    private final ModelRenderer arms;
    private final ModelRenderer right_leg;
    private final ModelRenderer left_leg;
    private final ModelRenderer bone;

    public ThomasModel() {
        this.head = new ModelRenderer(this).setTexSize(64, 64);
        this.head.texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F);

        this.nose = new ModelRenderer(this).setTexSize(64, 64);
        this.nose.texOffs(24, 0).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F);
        this.nose.setPos(0.0F, -2.0F, 0.0F);
        this.head.addChild(this.nose);

        this.headwear = new ModelRenderer(this).setTexSize(64, 64);
        this.headwear.texOffs(32, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, 0.51F);
        this.head.addChild(this.headwear);

        this.headwear2 = new ModelRenderer(this).setTexSize(64, 64);
        this.headwear2.texOffs(30, 47).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 1.0F);
        this.headwear2.xRot = -1.5708F;
        this.head.addChild(this.headwear2);

        this.body = new ModelRenderer(this).setTexSize(64, 64);
        this.body.texOffs(16, 20).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F);

        this.bodywear = new ModelRenderer(this).setTexSize(64, 64);
        this.bodywear.texOffs(0, 38).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F, 0.5F);
        this.body.addChild(this.bodywear);

        this.arms = new ModelRenderer(this).setTexSize(64, 64);
        this.arms
                .texOffs(40, 38).addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F)
                .texOffs(44, 22).addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F);

        this.arms.setPos(0.0F, 2.95F, -1.05F);
        this.arms.xRot = -0.7505F;

        ModelRenderer mirrored = new ModelRenderer(this).setTexSize(64, 64)
                .texOffs(44, 22).addBox(4.0F, -23.05F, -3.05F, 4.0F, 8.0F, 4.0F);
        mirrored.mirror = true;
        mirrored.setPos(0.0F, 21.05F, 1.05F);
        this.arms.addChild(mirrored);

        this.right_leg = new ModelRenderer(this).setTexSize(64, 64);
        this.right_leg.texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F);
        this.right_leg.setPos(-2.0F, 12.0F, 0.0F);

        this.left_leg = new ModelRenderer(this).setTexSize(64, 64);
        this.left_leg.texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F);
        this.left_leg.setPos(2.0F, 12.0F, 0.0F);

        this.bone = new ModelRenderer(this).setTexSize(64, 64);
        this.bone.texOffs(0, 0).addBox(-2.3F, -28.0F, -4.1F, 1.0F, 1.0F, 1.0F)
                .texOffs(0, 0).addBox(1.3F, -28.0F, -4.1F, 1.0F, 1.0F, 1.0F)
                .texOffs(0, 0).addBox(1.0F, -29.0F, -4.2F, 2.0F, 1.0F, 1.0F)
                .texOffs(0, 0).addBox(-1.5F, -24.9F, -4.2F, 3.0F, 1.0F, 1.0F)
                .texOffs(0, 0).addBox(-3.0F, -29.0F, -4.2F, 2.0F, 1.0F, 1.0F);
        this.bone.setPos(0.0F, 24.0F, 0.0F);
        this.head.addChild(this.bone);

    }

	public Iterable<ModelRenderer> parts() {
		return ImmutableList.of(this.head, this.body, this.arms, this.right_leg, this.left_leg);
	}

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		boolean flag = false;
		if (entity instanceof AbstractVillagerEntity) {
			flag = ((AbstractVillagerEntity)entity).getUnhappyCounter() > 0;
		}

		this.head.yRot = netHeadYaw * ((float)Math.PI / 180F);
		this.head.xRot = headPitch * ((float)Math.PI / 180F);
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
		this.right_leg.xRot = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount * 0.5F;
		this.left_leg.yRot = 0.0F;
		this.right_leg.yRot = 0.0F;
    }

	public ModelRenderer getHead() {
		return head;
	}

	public void hatVisible(boolean p_217146_1_) {
		this.head.visible = p_217146_1_;
	}
}