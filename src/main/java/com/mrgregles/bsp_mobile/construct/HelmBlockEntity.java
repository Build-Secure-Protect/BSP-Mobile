package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a Mobile Helm remembers while the server runs: its seat entity and driver (never saved: a restart empties every
 * helm), the construct it last served, the core it found and the rules aboard, refreshed every couple of seconds, and
 * how long the construct has stood still with nobody driving (for parking itself, D40).
 */
public class HelmBlockEntity extends BlockEntity {
    @Nullable
    private UUID seat;
    @Nullable
    private UUID driver;
    private String driverName = "";
    private long shipId;
    @Nullable
    private BlockPos corePos;
    private ConstructRules rules = ConstructRules.FREE;
    private int refreshTicks;
    private int stillTicks;
    private boolean closedRegion;
    private boolean autopilot;
    private Parts parts = Parts.NONE;
    private int ceiling = Integer.MAX_VALUE;

    public HelmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MOBILE_HELM_ENTITY.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().hasProperty(HelmBlock.FACING) ? getBlockState().getValue(HelmBlock.FACING) : Direction.NORTH;
    }

    @Nullable
    public UUID seat() {
        return seat;
    }

    @Nullable
    public UUID driver() {
        return driver;
    }

    public String driverName() {
        return driverName;
    }

    public boolean hasDriver() {
        return seat != null;
    }

    public long shipId() {
        return shipId;
    }

    @Nullable
    public BlockPos corePos() {
        return corePos;
    }

    public ConstructRules rules() {
        return rules;
    }

    void setSeat(@Nullable UUID seat, @Nullable UUID driver, String driverName) {
        this.seat = seat;
        this.driver = driver;
        this.driverName = driverName;
    }

    void clearSeat() {
        setSeat(null, null, "");
    }

    void setShipId(long shipId) {
        this.shipId = shipId;
    }

    void setCore(@Nullable BlockPos corePos, ConstructRules rules) {
        this.corePos = corePos;
        this.rules = rules;
    }

    /** True once every {@code every} ticks: the slow checks (the core, the rules) run then. */
    boolean refreshDue(int every) {
        if (--refreshTicks <= 0) {
            refreshTicks = every;
            return true;
        }
        return false;
    }

    boolean autopilot() {
        return autopilot;
    }

    void setAutopilot(boolean on) {
        this.autopilot = on;
    }

    Parts parts() {
        return parts;
    }

    void setParts(Parts parts) {
        this.parts = parts;
    }

    int ceiling() {
        return ceiling;
    }

    void setCeiling(int ceiling) {
        this.ceiling = ceiling;
    }

    boolean closedRegion() {
        return closedRegion;
    }

    void setClosedRegion(boolean closed) {
        this.closedRegion = closed;
    }

    int stillTicks() {
        return stillTicks;
    }

    void setStillTicks(int ticks) {
        this.stillTicks = ticks;
    }
}
