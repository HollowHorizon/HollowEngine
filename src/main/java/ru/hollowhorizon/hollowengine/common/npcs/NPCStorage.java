package ru.hollowhorizon.hollowengine.common.npcs;

import java.util.HashMap;

public class NPCStorage {
    public static final HashMap<String, NPCSettings> NPC_STORAGE = new HashMap<>();

    public static NPCSettings addNPC(String location, NPCSettings npc) {
        NPC_STORAGE.put(location, npc);
        return npc;
    }

    public static void removeNPC(String npc) {
        NPC_STORAGE.remove(npc);
    }
}
