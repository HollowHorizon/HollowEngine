package ru.hollowhorizon.hollowengine.client.screen.widget;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ListElement {
    private final ResourceLocation regName;
    private final Component textComponent;
    private final ResourceLocation icon;

    public ListElement(ResourceLocation regName, Component textComponent, ResourceLocation icon) {
        this.regName = regName;

        this.textComponent = textComponent;
        this.icon = icon;
    }

    public ResourceLocation getIcon() {
        return icon;
    }

    public ResourceLocation getRegName() {
        return regName;
    }

    public Component getTextComponent() {
        return textComponent;
    }
}
