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
 * What a Mobile Core remembers: the construct's name (D18), its tier (D4), its owner, the id of the Valkyrien Skies
 * ship it became when assembled (0 while not assembled), whether a Mobile Core Lock binds it to its owner (D27 to
 * D31), and the steal running on it, if one is. Name and tier travel on the dropped item (the loot table copies them)
 * and come back when it is placed again; the lock drops as its own item.
 */
public class MobileCoreBlockEntity extends BlockEntity {
    public static final int NAME_MAX = 32;
    private String name = "";
    private int tier = 1;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private long shipId;
    private boolean locked;
    // the steal in progress (D31): none while thief is null
    @Nullable
    private UUID thief;
    private String thiefName = "";
    private int stealTotal, stealLeft, stealGrace;
    private boolean stealOutside;

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

    public boolean locked() {
        return locked;
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

    /** Binds the core to {@code owner} (D29): they become its owner and the core is locked. */
    public void lock(UUID owner, String ownerName) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.locked = true;
        changed();
    }

    public void unlock() {
        this.locked = false;
        endSteal();
    }

    // ------------------------------------------------------------------ the steal

    public boolean stealRunning() {
        return thief != null;
    }

    @Nullable
    public UUID thief() {
        return thief;
    }

    public String thiefName() {
        return thiefName;
    }

    public int stealTotal() {
        return stealTotal;
    }

    public int stealLeft() {
        return stealLeft;
    }

    public int stealGrace() {
        return stealGrace;
    }

    public boolean stealOutside() {
        return stealOutside;
    }

    public void startSteal(UUID thief, String thiefName, int totalTicks, int graceTicks) {
        this.thief = thief;
        this.thiefName = thiefName;
        this.stealTotal = Math.max(1, totalTicks);
        this.stealLeft = this.stealTotal;
        this.stealGrace = graceTicks;
        this.stealOutside = false;
        changed();
    }

    /** One tick of the steal: the time left and the grace left outside the radius. Saved, not broadcast (the screen polls). */
    public void stealTick(int left, int grace, boolean outside) {
        this.stealLeft = left;
        this.stealGrace = grace;
        this.stealOutside = outside;
        setChanged();
    }

    /** Shortens a running steal (admin tools and tests). */
    public void setStealTicks(int left, int grace) {
        if (thief != null) {
            this.stealLeft = Math.max(1, left);
            this.stealGrace = grace;
            setChanged();
        }
    }

    public void endSteal() {
        this.thief = null;
        this.thiefName = "";
        this.stealTotal = this.stealLeft = this.stealGrace = 0;
        this.stealOutside = false;
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
        tag.putBoolean("Locked", locked);
        if (thief != null) {
            tag.putUUID("Thief", thief);
            tag.putString("ThiefName", thiefName);
            tag.putInt("StealTotal", stealTotal);
            tag.putInt("StealLeft", stealLeft);
            tag.putInt("StealGrace", stealGrace);
            tag.putBoolean("StealOutside", stealOutside);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        name = tag.getString("Name");
        tier = tag.contains("Tier") ? Tiers.clamp(tag.getInt("Tier")) : 1;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        shipId = tag.getLong("Ship");
        locked = tag.getBoolean("Locked");
        thief = tag.hasUUID("Thief") ? tag.getUUID("Thief") : null;
        thiefName = tag.getString("ThiefName");
        stealTotal = tag.getInt("StealTotal");
        stealLeft = tag.getInt("StealLeft");
        stealGrace = tag.getInt("StealGrace");
        stealOutside = tag.getBoolean("StealOutside");
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
