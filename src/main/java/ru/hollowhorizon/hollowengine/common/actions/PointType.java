package ru.hollowhorizon.hollowengine.common.actions;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;

public abstract class PointType<T> {
    private final ResourceLocation location;
    private T value;
    private boolean isInput;

    public PointType(ResourceLocation location) {
        this.location = location;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public ResourceLocation getType() {
        return location;
    }

    public abstract CompoundNBT writeNBT(CompoundNBT nbt);

    public abstract void readNBT(CompoundNBT nbt);

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PointType<?> pointType = (PointType<?>) o;
        return equals(pointType.location);
    }

    public boolean equals(ResourceLocation location) {
        return this.location.equals(location);
    }

    public void setInput(boolean input) {
        isInput = input;
    }

    public boolean isInput() {
        return this.isInput;
    }
}
