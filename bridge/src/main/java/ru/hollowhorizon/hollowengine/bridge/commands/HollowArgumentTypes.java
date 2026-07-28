package ru.hollowhorizon.hollowengine.bridge.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command argument types added by HollowEngine.
 *
 * Vanilla resolves an argument type to its serializer through a private map filled once at bootstrap.
 * A type that is missing from it is silently dropped from the command tree sent to clients, which makes
 * the command look invalid there and kills its suggestions. Rather than writing into that map, the
 * lookups are extended to also consult this one
 * (see {@link ru.hollowhorizon.hollowengine.bridge.mixins.ArgumentTypeInfosMixin}).
 */
public final class HollowArgumentTypes {
    private static final Map<Class<?>, ArgumentTypeInfo<?, ?>> INFOS = new ConcurrentHashMap<>();

    private HollowArgumentTypes() {
    }

    public static <A extends ArgumentType<?>> void register(Class<A> type, ArgumentTypeInfo<A, ?> info) {
        INFOS.put(type, info);
    }

    public static ArgumentTypeInfo<?, ?> find(Class<?> type) {
        return INFOS.get(type);
    }

    public static boolean contains(Class<?> type) {
        return INFOS.containsKey(type);
    }
}
