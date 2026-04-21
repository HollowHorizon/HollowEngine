package ru.hollowhorizon.hollowengine.bootstrap;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import ru.hollowhorizon.hollowengine.api.HasCreativeTab;

public class BlockItemCreativeTab extends BlockItem implements HasCreativeTab {
    private final CreativeModeTab tab;

    public BlockItemCreativeTab(Block block, Properties properties, CreativeModeTab tab) {
        super(block, properties);
        this.tab = tab;
    }

    @Override
    public CreativeModeTab tab() {
        return tab;
    }
}
