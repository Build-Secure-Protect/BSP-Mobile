package com.mrgregles.bsp_mobile.construct;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge;
import com.mrgregles.bsp_mobile.compat.ValkyrienSkiesBridge.Construct;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Save and paste (D60): a construct's blocks and block entities, core and helms included, as a vanilla structure file
 * under {@code config/bsp_mobile/constructs/<name>.nbt}; pasting sets them down at a target and assembles them into a
 * new construct owned by the admin, parked. Admin tools and the GameTests' fixtures.
 */
public final class Blueprints {
    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve(BSPMobile.MODID).resolve("constructs");

    private Blueprints() {}

    public record Saved(String name, int blocks) {}

    public record Pasted(long shipId, BlockPos corePos, int blocks) {}

    public static Path file(String name) {
        return DIR.resolve(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_") + ".nbt");
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(DIR)) {
            return out;
        }
        try (Stream<Path> s = Files.list(DIR)) {
            s.filter(p -> p.toString().endsWith(".nbt")).forEach(p -> out.add(p.getFileName().toString().replaceAll("\\.nbt$", "")));
        } catch (IOException ignored) {
            // an unreadable folder lists nothing
        }
        out.sort(String::compareTo);
        return out;
    }

    /** Writes the construct's shipyard box as a structure file; the core's name and tier travel in its block entity. */
    public static Optional<Saved> save(ServerLevel level, long shipId, String name) {
        Construct c = ValkyrienSkiesBridge.construct(level.getServer(), shipId).orElse(null);
        if (c == null || c.shipyardMin() == null || c.shipyardMax() == null) {
            return Optional.empty();
        }
        Vec3i size = c.shipyardMax().subtract(c.shipyardMin());
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, c.shipyardMin(), size, true, Blocks.STRUCTURE_VOID);
        CompoundTag tag = t.save(new CompoundTag());
        int blocks = 0;
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    if (!level.getBlockState(c.shipyardMin().offset(x, y, z)).isAir()) {
                        blocks++;
                    }
                }
            }
        }
        tag.putInt("MobileBlocks", blocks);
        try {
            Files.createDirectories(DIR);
            NbtIo.writeCompressed(tag, file(name).toFile());
        } catch (IOException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: could not save construct {} as {}", shipId, name, e);
            return Optional.empty();
        }
        return Optional.of(new Saved(name, blocks));
    }

    /** Sets a saved construct down with its lowest corner at {@code at}, gives its core to {@code owner}, and assembles it parked. */
    public static Optional<Pasted> paste(ServerLevel level, String name, BlockPos at, ServerPlayer owner) {
        Path f = file(name);
        if (!Files.isRegularFile(f)) {
            return Optional.empty();
        }
        CompoundTag tag;
        try {
            tag = NbtIo.readCompressed(f.toFile());
        } catch (IOException e) {
            BSPMobile.LOGGER.error("BSP-Mobile: could not read the saved construct {}", name, e);
            return Optional.empty();
        }
        StructureTemplate t = new StructureTemplate();
        t.load(level.holderLookup(Registries.BLOCK), tag);
        Vec3i size = t.getSize();
        if (!t.placeInWorld(level, at, at, new StructurePlaceSettings(), level.getRandom(), 2)) {
            return Optional.empty();
        }
        BlockPos corePos = null;
        for (int x = 0; x < size.getX() && corePos == null; x++) {
            for (int y = 0; y < size.getY() && corePos == null; y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos p = at.offset(x, y, z);
                    if (level.getBlockEntity(p) instanceof MobileCoreBlockEntity be) {
                        be.setOwner(owner.getUUID(), owner.getGameProfile().getName());
                        be.setShipId(0);
                        corePos = p;
                        break;
                    }
                }
            }
        }
        if (corePos == null) {
            return Optional.empty();
        }
        CoreService.Outcome out = CoreService.assemble(level, corePos);
        return Optional.of(new Pasted(out.ok() ? out.shipId() : 0, corePos, tag.getInt("MobileBlocks")));
    }
}
