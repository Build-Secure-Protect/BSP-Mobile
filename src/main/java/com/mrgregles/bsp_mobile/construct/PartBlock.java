package com.mrgregles.bsp_mobile.construct;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A part (D52): a block that does something for the construct it stands in, counted by number anywhere aboard.
 * Engines add thrust, floaters buoyancy, balloons lift, wings lift from speed (the engine's aerodynamics), jet
 * engines thrust and lift. Plain blocks otherwise; the wing is a bottom half-slab.
 */
public class PartBlock extends Block {
    public enum Kind { ENGINE, FLOATER, BALLOON, WING, JET }

    private static final VoxelShape SLAB = Block.box(0, 0, 0, 16, 8, 16);
    private final Kind kind;

    public PartBlock(Kind kind) {
        super(kind == Kind.BALLOON
                ? Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.8F).sound(SoundType.WOOL)
                : Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return kind == Kind.WING ? SLAB : Shapes.block();
    }

    /** The part as an item, with its tooltip. */
    public static class PartBlockItem extends BlockItem {
        private final String key;

        public PartBlockItem(Block block, String name, Properties props) {
            super(block, props);
            this.key = "item.bsp_mobile." + name + ".tooltip";
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
            super.appendHoverText(stack, level, lines, flag);
            lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
