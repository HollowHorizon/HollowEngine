/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.mixins;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage;
import ru.hollowhorizon.hollowengine.common.capabilities.StructuresCapability;
import ru.hollowhorizon.hollowengine.common.structures.ScriptedStructure;

import java.util.Optional;

@Mixin(LocateCommand.class)
public abstract class LocateCommandMixin {
    @Shadow(aliases = "m_214474_")
    public static int showLocateResult(CommandSourceStack pSource, ResourceOrTagLocationArgument.Result<?> pResult, BlockPos pSourcePosition, Pair<BlockPos, ? extends Holder<?>> pResultWithPosition, String pTranslationKey, boolean pAbsoluteY) {
        return 0;
    }

    @Shadow(aliases = "m_214483_")
    protected static Optional<? extends HolderSet.ListBacked<Structure>> getHolders(ResourceOrTagLocationArgument.Result<Structure> p_214484_, Registry<Structure> p_214485_) {
        return null;
    }

    @Shadow(aliases = "f_214452_")
    @Final
    private static DynamicCommandExceptionType ERROR_STRUCTURE_INVALID;

    @Inject(method = "locateStructure", at = @At("HEAD"), cancellable = true)
    private static void locateStructure(CommandSourceStack pSource, ResourceOrTagLocationArgument.Result<Structure> pStructure, CallbackInfoReturnable<Integer> cir) {
        var capability = pSource.getServer().overworld().getCapability(CapabilityStorage.getCapability(StructuresCapability.class)).orElseThrow(() -> new IllegalStateException("Structure Capability not found!"));
        BlockPos blockpos = new BlockPos(pSource.getPosition());
        pStructure.unwrap().left().ifPresent(key -> {


            Registry<Structure> registry = pSource.getLevel().registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
            try {
                HolderSet<Structure> holderset = getHolders(pStructure, registry).orElseThrow(() -> ERROR_STRUCTURE_INVALID.create(pStructure.asPrintable()));
                var structure = holderset.stream().findFirst().get();
                if(structure.get() instanceof ScriptedStructure) {
                    var name = ((ScriptedStructure) structure.get()).getLocation().toString();
                    if (capability.getStructures().containsKey(name)) {
                        var pos = capability.getStructures().get(name).getCenter();

                        var pair = new Pair<>(pos, structure);

                        cir.setReturnValue(showLocateResult(pSource, pStructure, blockpos, pair, "commands.locate.biome.success", true));
                    }
                }
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }

        });
    }
}
