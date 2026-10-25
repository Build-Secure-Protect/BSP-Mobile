package com.mrgregles.bsp_mobile.registry;

import com.mrgregles.bsp_mobile.BSPMobile;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlock;
import com.mrgregles.bsp_mobile.construct.MobileCoreBlockEntity;
import com.mrgregles.bsp_mobile.construct.MobileCoreItem;
import com.mrgregles.bsp_mobile.construct.HelmBlock;
import com.mrgregles.bsp_mobile.construct.HelmBlockEntity;
import com.mrgregles.bsp_mobile.construct.LockItem;
import com.mrgregles.bsp_mobile.construct.PartBlock;
import com.mrgregles.bsp_mobile.construct.PartItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The mod's blocks, items and block entities. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BSPMobile.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BSPMobile.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BSPMobile.MODID);

    public static final RegistryObject<MobileCoreBlock> MOBILE_CORE = BLOCKS.register("mobile_core", MobileCoreBlock::new);
    public static final RegistryObject<Item> MOBILE_CORE_ITEM = ITEMS.register("mobile_core", () -> new MobileCoreItem(MOBILE_CORE.get(), new Item.Properties()));
    // the three parts the core is crafted from (D22, D25); recipes in data/bsp_mobile/recipes from tools/gen_recipes.py
    public static final RegistryObject<Item> MOBILE_CORE_PROCESSOR = ITEMS.register("mobile_core_processor", () -> new PartItem(new Item.Properties()));
    public static final RegistryObject<Item> MOBILE_CORE_GPS = ITEMS.register("mobile_core_gps", () -> new PartItem(new Item.Properties()));
    public static final RegistryObject<Item> MOBILE_CORE_GYROSCOPE = ITEMS.register("mobile_core_gyroscope", () -> new PartItem(new Item.Properties()));
    // the Mobile Core Lock (D27 to D31): binds a core to its owner; recipe from tools/gen_recipes.py
    public static final RegistryObject<Item> MOBILE_CORE_LOCK = ITEMS.register("mobile_core_lock", () -> new LockItem(new Item.Properties().stacksTo(16)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<MobileCoreBlockEntity>> MOBILE_CORE_ENTITY = BLOCK_ENTITIES.register("mobile_core",
            () -> BlockEntityType.Builder.of(MobileCoreBlockEntity::new, MOBILE_CORE.get()).build(null));

    // the Mobile Helm (D36 to D43): drives the construct it stands on
    public static final RegistryObject<HelmBlock> MOBILE_HELM = BLOCKS.register("mobile_helm", HelmBlock::new);
    public static final RegistryObject<Item> MOBILE_HELM_ITEM = ITEMS.register("mobile_helm", () -> new HelmBlock.HelmItem(MOBILE_HELM.get(), new Item.Properties()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<HelmBlockEntity>> MOBILE_HELM_ENTITY = BLOCK_ENTITIES.register("mobile_helm",
            () -> BlockEntityType.Builder.of(HelmBlockEntity::new, MOBILE_HELM.get()).build(null));

    // the parts (D52): counted aboard by number (the list first: the fields below fill it)
    private static final java.util.List<RegistryObject<Item>> PART_ITEMS = new java.util.ArrayList<>();
    public static final RegistryObject<PartBlock> MOBILE_ENGINE = part("mobile_engine", PartBlock.Kind.ENGINE);
    public static final RegistryObject<PartBlock> MOBILE_FLOATER = part("mobile_floater", PartBlock.Kind.FLOATER);
    public static final RegistryObject<PartBlock> MOBILE_BALLOON = part("mobile_balloon", PartBlock.Kind.BALLOON);
    public static final RegistryObject<PartBlock> MOBILE_WING = part("mobile_wing", PartBlock.Kind.WING);
    public static final RegistryObject<PartBlock> MOBILE_JET_ENGINE = part("mobile_jet_engine", PartBlock.Kind.JET);
    private static RegistryObject<PartBlock> part(String name, PartBlock.Kind kind) {
        RegistryObject<PartBlock> block = BLOCKS.register(name, () -> new PartBlock(kind));
        PART_ITEMS.add(ITEMS.register(name, () -> new PartBlock.PartBlockItem(block.get(), name, new Item.Properties())));
        return block;
    }

    private ModBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ModBlocks::onCreativeTabs);
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MOBILE_CORE_ITEM);
            event.accept(MOBILE_HELM_ITEM);
            PART_ITEMS.forEach(event::accept);
            event.accept(MOBILE_CORE_PROCESSOR);
            event.accept(MOBILE_CORE_GPS);
            event.accept(MOBILE_CORE_GYROSCOPE);
            event.accept(MOBILE_CORE_LOCK);
        }
    }
}
