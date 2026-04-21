package ru.hollowhorizon.hollowengine.internal;

import ru.hollowhorizon.hollowengine.api.ModList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class NeoForgeModList implements ModList {
    @Override
    public boolean isLoaded(String modId) {
        return net.neoforged.fml.ModList.get().isLoaded(modId);
    }

    @Override
    public File getFile(String modId) {
        return getModFile(modId);
    }


    private File getModFile(String modId) {
        Path path = net.neoforged.fml.ModList.get().getModFileById(modId).getFile().getFilePath();

        try {
            String fileName = path.getFileName().toString();
            if (!fileName.endsWith(".jar")) {
                fileName = modId + ".jar";
            }

            File newFile = new File("hollowengine/.cache/mods/" + fileName);
            if (!newFile.getParentFile().exists()) {
                newFile.getParentFile().mkdirs();
            }

            if (newFile.exists()) {
                return newFile;
            }
            newFile.createNewFile();

            try (InputStream input = Files.newInputStream(path);
                 FileOutputStream output = new FileOutputStream(newFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }

            return newFile;
        } catch (AccessDeniedException e) {
            return e.getFile() != null ? new File(e.getFile()) : null;
        } catch (Exception e) {
            // Рефлексия для доступа к UnionFileSystem
            String fsName = path.getFileSystem().getClass().getName();
            if (fsName.equals("cpw.mods.niofs.union.UnionFileSystem")) {
                try {
                    java.lang.reflect.Field field = path.getFileSystem().getClass().getDeclaredField("basepaths");
                    field.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    List<Path> basePaths = (List<Path>) field.get(path.getFileSystem());
                    return basePaths.get(0).toFile();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            return path.toFile();
        }
    }
}
