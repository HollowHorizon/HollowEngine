package ru.hollowhorizon.hollowengine.api;

import java.io.File;

public interface ModList {
    boolean isLoaded(String modId);

    File getFile(String modId);
}
