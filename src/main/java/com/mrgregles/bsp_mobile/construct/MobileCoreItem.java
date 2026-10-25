package com.mrgregles.bsp_mobile.construct;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The core as an item: carries the construct's name and tier in its block entity tag and shows them in the tooltip. */
public class MobileCoreItem extends BlockItem {
    public MobileCoreItem(Block block, Properties props) {
        super(block, props);
    }

    public static void write(ItemStack stack, String name, int tier) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putInt("Tier", tier);
        stack.addTagElement("BlockEntityTag", tag);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        int tier = tag == null || !tag.contains("Tier") ? 1 : Tiers.clamp(tag.getInt("Tier"));
        lines.add(Component.translatable("item.bsp_mobile.mobile_core.tier", roman(tier)).withStyle(ChatFormatting.GRAY));
        if (tag != null && !tag.getString("Name").isBlank()) {
            lines.add(Component.literal(tag.getString("Name")).withStyle(ChatFormatting.AQUA));
        }
    }

    public static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(tier);
        };
    }
}
