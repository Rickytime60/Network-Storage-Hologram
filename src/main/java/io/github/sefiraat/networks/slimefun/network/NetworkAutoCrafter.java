package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.network.SupportedRecipes;
import io.github.sefiraat.networks.network.stackcaches.BlueprintInstance;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.slimefun.NetworkSlimefunItems;
import io.github.sefiraat.networks.slimefun.tools.CraftingBlueprint;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.sefiraat.networks.utils.Theme;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentCraftingBlueprintType;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class NetworkAutoCrafter extends NetworkObject {

    private static final int[] BACKGROUND_SLOTS = new int[]{
        3, 4, 5, 12, 13, 14, 21, 22, 23
    };
    private static final int[] BLUEPRINT_BACKGROUND = new int[]{0, 1, 2, 9, 11, 18, 19, 20};
    private static final int[] OUTPUT_BACKGROUND = new int[]{6, 7, 8, 15, 17, 24, 25, 26};

    private static final int BLUEPRINT_SLOT = 10;
    private static final int OUTPUT_SLOT = 16;

    public static final CustomItemStack BLUEPRINT_BACKGROUND_STACK = new CustomItemStack(
        Material.BLUE_STAINED_GLASS_PANE, Theme.PASSIVE + "Crafting Blueprint"
    );

    public static final CustomItemStack OUTPUT_BACKGROUND_STACK = new CustomItemStack(
        Material.GREEN_STAINED_GLASS_PANE, Theme.PASSIVE + "Output"
    );

    private final int chargePerCraft;
    private final boolean directSubmit;
    private static final Map<Location, UUID> OWNER_MAP = new HashMap<>();

    public NetworkAutoCrafter(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe, int chargePerCraft, boolean directSubmit) {
        super(itemGroup, item, recipeType, recipe, NodeType.CRAFTER);

        this.chargePerCraft = chargePerCraft;
        this.directSubmit = directSubmit;

        this.getSlotsToDrop().add(BLUEPRINT_SLOT);
        this.getSlotsToDrop().add(OUTPUT_SLOT);

        addItemHandler(
            new BlockTicker() {
                @Override
                public boolean isSynchronized() {
                    return false;
                }

                @Override
                public void tick(Block block, SlimefunItem slimefunItem, Config config) {
                    BlockMenu blockMenu = BlockStorage.getInventory(block);
                    if (blockMenu != null) {
                        craftPreFlight(blockMenu);
                    }
                }
            },
            new BlockPlaceHandler(false) {
                @Override
                public void onPlayerPlace(@Nonnull BlockPlaceEvent event) {
                    final UUID uuid = event.getPlayer().getUniqueId();
                    BlockStorage.addBlockInfo(event.getBlock(), "owner", uuid.toString());
                    OWNER_MAP.put(event.getBlock().getLocation(), uuid);
                }
            }
        );
    }

    protected void craftPreFlight(@Nonnull BlockMenu blockMenu) {

        final Player player = Bukkit.getPlayer(OWNER_MAP.get(blockMenu.getLocation()));

        if (player == null) {
            return;
        }

        final NodeDefinition definition = NetworkStorage.getAllNetworkObjects().get(blockMenu.getLocation());

        if (definition.getNode() == null) {
            return;
        }

        final ItemStack blueprint = blockMenu.getItemInSlot(BLUEPRINT_SLOT);

        if (blueprint == null || blueprint.getType() == Material.AIR) {
            return;
        }

        final NetworkRoot root = definition.getNode().getRoot();
        final long networkCharge = root.getDownstreamCharge();

        if (networkCharge > this.chargePerCraft) {
            final SlimefunItem item = SlimefunItem.getByItem(blueprint);

            if (!(item instanceof CraftingBlueprint)) {
                return;
            }

            final ItemMeta blueprintMeta = blueprint.getItemMeta();
            final Optional<BlueprintInstance> optional = DataTypeMethods.getOptionalCustom(blueprintMeta, Keys.BLUEPRINT_INSTANCE, PersistentCraftingBlueprintType.TYPE);

            if (optional.isEmpty()) {
                return;
            }

            final BlueprintInstance instance = optional.get();
            final ItemStack outputItem = blockMenu.getItemInSlot(OUTPUT_SLOT);

            if (outputItem != null
                && outputItem.getType() == Material.AIR
                && (!StackUtils.itemsMatch(instance, outputItem) || outputItem.getAmount() >= outputItem.getMaxStackSize())
            ) {
                return;
            }

            if (tryCraft(blockMenu, player, instance, root)) {
                root.removeCharge(this.chargePerCraft);
            }

            if (this.directSubmit) {
                root.addItemStack(blockMenu.getItemInSlot(OUTPUT_SLOT));
            }
        }
    }

    private boolean tryCraft(@Nonnull BlockMenu blockMenu, @Nonnull Player player, @Nonnull BlueprintInstance instance, @Nonnull NetworkRoot root) {
        // Get the recipe input
        final ItemStack[] inputs = new ItemStack[9];

        for (int i = 0; i < 9; i++) {
            final ItemStack fetched = root.getItemStack(new ItemRequest(instance.getRecipe()[i], 1));
            inputs[i] = fetched;
        }

        ItemStack crafted = null;

        // Go through each slimefun recipe, test and set the ItemStack if found
        for (Map.Entry<ItemStack[], ItemStack> entry : SupportedRecipes.getRecipes().entrySet()) {
            if (SupportedRecipes.testRecipe(inputs, entry.getKey())) {
                crafted = entry.getValue().clone();
                break;
            }
        }

        // If no slimefun recipe found, try a vanilla one
        if (crafted == null) {
            crafted = Bukkit.craftItem(inputs, blockMenu.getBlock().getWorld(), player);
        }

        // If no item crafted OR result doesn't fit, escape
        if (crafted.getType() == Material.AIR) {
            for (ItemStack input : inputs) {
                root.addItemStack(input);
            }
            return false;
        }

        // Push item
        blockMenu.pushItem(crafted, OUTPUT_SLOT);
        return true;
    }


    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {

            @Override
            public void init() {
                drawBackground(BACKGROUND_SLOTS);
                drawBackground(BLUEPRINT_BACKGROUND_STACK, BLUEPRINT_BACKGROUND);
                drawBackground(OUTPUT_BACKGROUND_STACK, OUTPUT_BACKGROUND);
            }


            @Override
            public boolean canOpen(@Nonnull Block block, @Nonnull Player player) {
                return NetworkSlimefunItems.NETWORK_AUTO_CRAFTER.canUse(player, false)
                    && Slimefun.getProtectionManager().hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK);
            }

            @Override
            public void newInstance(@Nonnull BlockMenu menu, @Nonnull Block b) {
                String owner = BlockStorage.getLocationInfo(b.getLocation(), "owner");
                OWNER_MAP.put(b.getLocation(), UUID.fromString(owner));
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }
        };
    }
}