package ru.hollowhorizon.hollowengine.api;

@FunctionalInterface
public interface AutoModelType {
    AutoModelType DEFAULT = () -> "item/generated";
    AutoModelType HANDHELD = () -> "item/handheld";
    AutoModelType CUBE_ALL = () -> "block/cube_all";

    static AutoModelType custom(String type, String blockState) {
        return new AutoModelType() {
            @Override
            public String modelId() {
                return type;
            }

            @Override
            public String blockStateId() {
                return blockState;
            }
        };
    }

    static AutoModelType custom(String type) {
        return custom(type, "default");
    }

    String modelId();

    default String blockStateId() {
        return "default";
    }
}
