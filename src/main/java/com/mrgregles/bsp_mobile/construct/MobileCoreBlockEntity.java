package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a Mobile Core remembers: the construct's name (D18), its tier (D4), its owner, and the id of the Valkyrien Skies
 * ship it became when assembled (0 while not assembled). Name and tier travel on the dropped item (the loot table copies
 * them) and come back when it is placed again.
 */
public class MobileCoreBlockEntity extends BlockEntity {
    public static final int NAME_MAX = 32;
    private String name = "";
    private int tier = 1;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private long shipId;

    public MobileCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MOBILE_CORE_ENTITY.get(), pos, state);
    }

    public String name() {
        return name;
    }

    public int tier() {
        return tier;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public long shipId() {
        return shipId;
    }

    public boolean isAssembled() {
        return shipId != 0;
    }

    public void setName(String name) {
        this.name = name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name;
        changed();
    }

    public void setTier(int tier) {
        this.tier = Tiers.clamp(tier);
        changed();
    }

    public void setOwner(@Nullable UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
        changed();
    }

    public void setShipId(long shipId) {
        this.shipId = shipId;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Name", name);
        tag.putInt("Tier", tier);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        tag.putLong("Ship", shipId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        name = tag.getString("Name");
        tier = tag.contains("Tier") ? Tiers.clamp(tag.getInt("Tier")) : 1;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        shipId = tag.getLong("Ship");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
