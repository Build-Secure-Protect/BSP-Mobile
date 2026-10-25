package com.mrgregles.bsp_mobile.construct;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One of the three parts a Mobile Core is crafted from (D22): the Processor, the GPS and the Gyroscope. Plain items
 * with one tooltip line (D25); their recipes come from {@code tools/gen_recipes.py}.
 */
public class PartItem extends Item {
    public PartItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("item.bsp_mobile.part.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
