package ru.hollowhorizon.hollowengine.api.extensions;

import org.jetbrains.annotations.Nullable;

public interface EntityExtension {
    @Nullable
    Object hollowengine$detachedAttachments();

    void hollowengine$setDetachedAttachments(@Nullable Object attachments);
}
