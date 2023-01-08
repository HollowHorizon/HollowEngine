package ru.hollowhorizon.hollowstory.common.entities;

import net.minecraft.world.World;
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings;

public class PublicNPCEntity extends NPCEntity {
    public PublicNPCEntity(NPCSettings options, World level) {
        super(options, level);
    }
}
