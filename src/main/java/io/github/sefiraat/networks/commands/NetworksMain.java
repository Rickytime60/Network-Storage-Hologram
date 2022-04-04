package io.github.sefiraat.networks.commands;

import io.github.sefiraat.networks.network.stackcaches.CardInstance;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.slimefun.NetworkSlimefunItems;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.sefiraat.networks.slimefun.tools.NetworkCard;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.Theme;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentCardInstanceType;
import io.github.sefiraat.networks.utils.datatypes.PersistentQuantumStorageType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class NetworksMain implements CommandExecutor {

    private static final Map<Integer, NetworkQuantumStorage> QUANTUM_REPLACEMENT_MAP = new HashMap<>();

    static {
        QUANTUM_REPLACEMENT_MAP.put(4096, NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_1);
        QUANTUM_REPLACEMENT_MAP.put(32768, NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_2);
        QUANTUM_REPLACEMENT_MAP.put(262144, NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_3);
        QUANTUM_REPLACEMENT_MAP.put(2097152, NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_4);
        QUANTUM_REPLACEMENT_MAP.put(16777216, NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_5);
        QUANTUM_REPLACEMENT_MAP.put(134217728, NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_6);
        QUANTUM_REPLACEMENT_MAP.put(1073741824, NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_7);
        QUANTUM_REPLACEMENT_MAP.put(Integer.MAX_VALUE, NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_8);
    }

    @Override
    public boolean onCommand(@Nonnull CommandSender sender, @Nonnull Command command, @Nonnull String label, @Nonnull String[] args) {
        if (sender instanceof Player player) {

            if (args.length == 0) {
                return false;
            }

            if (args[0].equalsIgnoreCase("fillquantum")) {
                if ((player.isOp() || player.hasPermission("networks.admin")) && args.length >= 2) {
                    try {
                        int number = Integer.parseInt(args[1]);
                        fillQuantum(player, number);
                        return true;
                    } catch (NumberFormatException exception) {
                        return false;
                    }
                }
            } else if (args[0].equalsIgnoreCase("replacecard")) {
                replaceCard(player);
                return true;
            }
        }
        return true;
    }

    public void fillQuantum(Player player, int amount) {
        final ItemStack itemStack = player.getInventory().getItemInMainHand();
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            player.sendMessage(Theme.ERROR + "Item in hand must be a Quantum Storage.");
            return;
        }

        SlimefunItem slimefunItem = SlimefunItem.getByItem(itemStack);

        if (!(slimefunItem instanceof NetworkQuantumStorage)) {
            player.sendMessage(Theme.ERROR + "Item in hand must be a Quantum Storage.");
            return;
        }

        ItemMeta meta = itemStack.getItemMeta();
        final QuantumCache quantumCache = DataTypeMethods.getCustom(
            meta,
            Keys.QUANTUM_STORAGE_INSTANCE,
            PersistentQuantumStorageType.TYPE
        );

        if (quantumCache == null || quantumCache.getItemStack() == null) {
            player.sendMessage(Theme.ERROR + "This card has either not been set to an item yet or is a corrupted Quantum Storage.");
            return;
        }

        quantumCache.setAmount(amount);
        DataTypeMethods.setCustom(meta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE, quantumCache);
        quantumCache.updateMetaLore(meta);
        itemStack.setItemMeta(meta);
        player.sendMessage(Theme.SUCCESS + "Item updated");
    }

    public void replaceCard(Player player) {
        final ItemStack oldCard = player.getInventory().getItemInMainHand();
        if (oldCard == null || oldCard.getType() == Material.AIR) {
            player.sendMessage(Theme.ERROR + "Item in hand must be a memory card.");
            return;
        }

        final SlimefunItem slimefunItem = SlimefunItem.getByItem(oldCard);

        if (!(slimefunItem instanceof NetworkCard card)) {
            player.sendMessage(Theme.ERROR + "Item in hand must be a memory card.");
            return;
        }

        if (oldCard.getAmount() > 1) {
            player.sendMessage(Theme.ERROR + "One card at a time only, please.");
            return;
        }

        final ItemMeta oldCardItemMeta = oldCard.getItemMeta();
        final CardInstance cardInstance = DataTypeMethods.getCustom(
            oldCardItemMeta,
            Keys.CARD_INSTANCE,
            PersistentCardInstanceType.TYPE
        );
        final NetworkQuantumStorage quantum = QUANTUM_REPLACEMENT_MAP.get(card.getSize());
        final ItemStack replacement = quantum.getItem().clone();

        if (cardInstance == null || cardInstance.getAmount() == 0) {
            player.getInventory().setItemInMainHand(replacement);
            player.sendMessage(Theme.SUCCESS + "Memory card was empty and has been replaced with a blank equivalent Quantum Storage");
        } else {
            final ItemMeta quantumItemMeta = replacement.getItemMeta();
            final QuantumCache cache = createReplacementCache(quantum, cardInstance);
            DataTypeMethods.setCustom(quantumItemMeta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE, cache);
            cache.addMetaLore(quantumItemMeta);
            replacement.setItemMeta(quantumItemMeta);

            player.getInventory().setItemInMainHand(replacement);
            player.sendMessage(Theme.SUCCESS + "Memory card replaced with equivalent Quantum Storage and filled to correct amount");
        }
    }

    private QuantumCache createReplacementCache(NetworkQuantumStorage storage, CardInstance cardInstance) {
        final ItemStack heldStack = cardInstance.getItemStack().clone();
        heldStack.setAmount(1);
        return new QuantumCache(heldStack, cardInstance.getAmount(), storage.getMaxAmount(), true);
    }
}