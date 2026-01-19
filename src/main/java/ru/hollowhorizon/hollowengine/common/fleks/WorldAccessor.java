package ru.hollowhorizon.hollowengine.common.fleks;

import com.github.quillraven.fleks.*;
import com.github.quillraven.fleks.collection.Bag;
import com.github.quillraven.fleks.collection.BitArray;

import java.util.Map;

public class WorldAccessor {
    public static EntityService get(World world) {
        return world.getEntityService();
    }

    public static World get(WorldConfiguration world) {
        return world.getWorld();
    }

    public static boolean delayRemoval(EntityService entityService) {
        return entityService.getDelayRemoval();
    }

    public static ComponentService compService(EntityService entityService) {
        return entityService.getWorld().getComponentService();
    }


    public static Bag<BitArray> getCompMasks(EntityService service) {
        return service.getCompMasks();
    }

    public static ComponentService getComponentService(World service) {
        return service.getComponentService();
    }

    public static ComponentsHolder<?> holderByIndexOrNull(ComponentService service, int index) {
        return service.holderByIndexOrNull$Fleks(index);
    }

    public static World world(EntityService service) {
        return service.getWorld();
    }

    public static Family[] allFamilies(World world) {
        return world.getAllFamilies();
    }

    public static void onEntityCfgChanged(Family config, Entity entity, BitArray mask) {
        config.onEntityCfgChanged(entity, mask);
    }

    public static Map<Integer, UniqueId<?>> getTagCache(World service) {
        return service.getTagCache();
    }

    public static void setWildcard(ComponentsHolder<?> holder, Entity entity, Object obj) {
        holder.setWildcard(entity, obj);
    }
}
