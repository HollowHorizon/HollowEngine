package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.structures.ScriptedStructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
    @Shadow(aliases = "f_223022_")
    @Final
    private Map<Structure, List<StructurePlacement>> placementsForStructure;

    @Inject(method = {"lambda$generatePositions$5", "m_223205_"}, at = @At(value = "HEAD"), remap = true)
    private void generatePositions(Set set, RandomState pRandom, Holder holder, CallbackInfo ci) {
        StructureSet structureset = (StructureSet) holder.value();

        structureset.structures().forEach(structureEntry -> {
            var structure = structureEntry.structure().value();
            if (structure instanceof ScriptedStructure) {
                placementsForStructure.computeIfAbsent(structure, s -> new ArrayList<>()).add(structureset.placement());
            }
        });
    }
}
