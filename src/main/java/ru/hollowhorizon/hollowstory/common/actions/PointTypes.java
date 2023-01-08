package ru.hollowhorizon.hollowstory.common.actions;

import net.minecraft.util.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static ru.hollowhorizon.hollowstory.HollowStory.MODID;

public class PointTypes {
    private static final Map<ResourceLocation, Supplier<PointType<?>>> TYPES = new LinkedHashMap<>();

    public static void register(ResourceLocation location, Supplier<PointType<?>> type) {
        TYPES.put(location, type);
    }

    private static void register(String location, Supplier<PointType<?>> type) {
        TYPES.put(new ResourceLocation(MODID, location), type);
    }

    public static void init() {
        register("int_point_type", IntPointType::new);
    }

    public static final Supplier<PointType<?>> INTEGER_INPUT_TYPE = () -> createType("int_point_type", true);
    public static final Supplier<PointType<?>> INTEGER_OUTPUT_TYPE = () -> createType("int_point_type", false);

    public static PointType<?> createType(String location, boolean isInput) {
        return createType(new ResourceLocation(MODID, location), isInput);
    }

    public static PointType<?> createType(ResourceLocation location, boolean isInput) {
        Supplier<PointType<?>> supplier = TYPES.get(location);
        if(supplier == null) throw new IllegalStateException("type: "+location+" not found!");
        else {
            PointType<?> pointType = supplier.get();
            pointType.setInput(isInput);
            return pointType;
        }
    }
}
