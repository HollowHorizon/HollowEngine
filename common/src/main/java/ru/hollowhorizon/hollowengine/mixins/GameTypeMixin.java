package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.IntFunction;

@Mixin(GameType.class)
@Unique
public class GameTypeMixin {
    @Shadow
    @Final
    @Mutable
    private static GameType[] $VALUES;
    @Shadow
    @Final
    @Mutable
    public static StringRepresentable.EnumCodec<GameType> CODEC;
    @Shadow
    @Final
    @Mutable
    private static IntFunction<GameType> BY_ID;

    private static GameType STORYTELLER = hollowengine$addVariant("STORYTELLER", 4, "storyteller");

    private static GameType hollowengine$addVariant(String internalName, int id, String name) {
        ArrayList<GameType> variants = new ArrayList<>(Arrays.asList(GameTypeMixin.$VALUES));
        GameType instrument = invokeInit(internalName, variants.getLast().ordinal() + 1, id, name);
        variants.add(instrument);
        GameTypeMixin.$VALUES = variants.toArray(new GameType[0]);
        CODEC = StringRepresentable.fromEnum(() -> $VALUES);
        BY_ID = ByIdMap.continuous(GameType::getId, $VALUES, ByIdMap.OutOfBoundsStrategy.ZERO);
        return instrument;
    }

    @Invoker("<init>")
    public static GameType invokeInit(String internalName, int internalId, int id, String name) {
        throw new UnsupportedOperationException("How do you do it?");
    }

}
