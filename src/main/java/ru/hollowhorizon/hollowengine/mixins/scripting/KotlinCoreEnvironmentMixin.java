package ru.hollowhorizon.hollowengine.mixins.scripting;


import org.spongepowered.asm.mixin.Mixin;
//? if forge || neoforge {
/*import org.jetbrains.kotlin.utils.PathUtil;
import org.spongepowered.asm.mixin.injection.At;
import ru.hollowhorizon.hc.common.utils.ForgeKotlinKt;
import org.spongepowered.asm.mixin.injection.Redirect;
*///?}
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingSourcesKt;

import java.io.File;

@Mixin(KotlinCoreEnvironment.Companion.class)
public class KotlinCoreEnvironmentMixin {

    //? if forge || neoforge {
    /*@Redirect(method = "registerApplicationExtensionPointsAndExtensionsFrom", at = @At(value = "INVOKE", target = "Lorg/jetbrains/kotlin/utils/PathUtil;getResourcePathForClass(Ljava/lang/Class;)Ljava/io/File;"), remap = false)
     *///?}
    private File getResourcePathForClass(Class<?> aClass) {
        //? if forge || neoforge {
        /*if(!ForgeKotlinKt.isProduction()) return PathUtil.getResourcePathForClass(aClass);
         *///?}
        return ScriptingSourcesKt.compilerJar();
    }
}
