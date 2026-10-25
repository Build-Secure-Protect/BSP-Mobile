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
 * The Mobile Core Lock (D27 to D31): right-clicked on a Mobile Core it is used up and binds the core to that player,
 * who becomes its owner. A locked core admits its owner and their team; anyone else takes it by a timed steal at the
 * core. UNLOCK on the core's screen gives the lock back.
 */
public class LockItem extends Item {
    public LockItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("item.bsp_mobile.mobile_core_lock.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
