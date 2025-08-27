package ru.hollowhorizon.hc.mixins;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.hollowhorizon.hc.HollowLoggerKt;
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hc.common.events.registry.RegisterResourcePacksEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @ModifyVariable(at = @At("HEAD"), method = "<init>*", argsOnly = true)
    private static RepositorySource[] onInit(RepositorySource[] providers) {
        List<RepositorySource> l = new ArrayList<>(Arrays.asList(providers));


        l.add(src -> EventBus.post(new RegisterResourcePacksEvent(src)));

        return l.toArray(new RepositorySource[0]);
    }
}
