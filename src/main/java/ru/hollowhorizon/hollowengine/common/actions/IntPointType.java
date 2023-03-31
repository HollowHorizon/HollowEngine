package ru.hollowhorizon.hollowengine.common.actions;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;

import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;

public class IntPointType extends PointType<Integer> {
    public IntPointType() {
        super(new ResourceLocation(MODID, "int_point_type"));
    }

    @Override
    public CompoundNBT writeNBT(CompoundNBT nbt) {
        nbt.putInt("value", getValue());
        return nbt;
    }

    @Override
    public void readNBT(CompoundNBT nbt) {
        setValue(nbt.getInt("value"));
    }
}
