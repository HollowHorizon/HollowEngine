package ru.hollowhorizon.hollowstory.client.screen.widget;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

public class ListElement {
    private final ResourceLocation regName;
    private final ITextComponent textComponent;
    private final ResourceLocation icon;

    public ListElement(ResourceLocation regName, ITextComponent textComponent, ResourceLocation icon) {
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

    public ITextComponent getTextComponent() {
        return textComponent;
    }
}
