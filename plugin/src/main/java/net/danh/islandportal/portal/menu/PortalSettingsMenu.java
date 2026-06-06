package net.danh.islandportal.portal.menu;

import net.danh.islandportal.portal.config.PortalConfig;
import net.danh.islandportal.portal.model.AccessPolicy;
import net.danh.islandportal.portal.model.ManagedPortal;
import net.danh.islandportal.portal.model.MenuConfig;
import net.danh.islandportal.portal.model.MenuItemConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalSettingsMenu {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private final PortalConfig config;
    private final NamespacedKey actionKey;

    public PortalSettingsMenu(JavaPlugin plugin, PortalConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
    }

    public NamespacedKey actionKey() {
        return actionKey;
    }

    public void open(Player player, ManagedPortal portal) {
        MenuConfig menu = config.settingsMenu();
        Inventory inventory = plugin.getServer().createInventory(new PortalSettingsHolder(portal.id()), menu.size(), miniMessage.deserialize(apply(menu.title(), portal)));
        for (MenuItemConfig item : menu.items()) {
            ItemStack stack = item(item, portal);
            for (int slot : item.slots()) {
                if (slot < 0 || slot >= menu.size()) {
                    continue;
                }
                inventory.setItem(slot, stack);
            }
        }
        player.openInventory(inventory);
    }

    private ItemStack item(MenuItemConfig config, ManagedPortal portal) {
        ItemStack item = new ItemStack(config.material(), config.amount());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(miniMessage.deserialize(apply(config.displayName(), portal)));
        if (!config.lore().isEmpty()) {
            meta.lore(config.lore().stream().map(line -> miniMessage.deserialize(apply(line, portal))).toList());
        }
        if (config.customModelData() != null) {
            setCustomModelData(meta, config.customModelData());
        }
        meta.setUnbreakable(config.unbreakable());
        if (config.enchantmentGlint() != null) {
            meta.setEnchantmentGlintOverride(config.enchantmentGlint());
        }
        for (String flagName : config.itemFlags()) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, config.action());
        item.setItemMeta(meta);
        return item;
    }

    private String apply(String input, ManagedPortal portal) {
        Map<String, String> placeholders = new ConcurrentHashMap<>();
        placeholders.put("%portal_id%", portal.id());
        placeholders.put("%portal_type%", portal.type());
        placeholders.put("%pickup_policy%", portal.pickupPolicy().name());
        placeholders.put("%use_policy%", portal.usePolicy().name());
        placeholders.put("%configure_policy%", portal.configurePolicy().name());
        placeholders.put("%next_pickup_policy%", next(portal.pickupPolicy()).name());
        placeholders.put("%next_use_policy%", next(portal.usePolicy()).name());
        placeholders.put("%next_configure_policy%", next(portal.configurePolicy()).name());
        placeholders.put("%owner%", portal.owner() == null ? "server" : portal.owner());
        placeholders.put("%island_id%", portal.islandId() == null ? "" : portal.islandId());
        placeholders.put("%default%", Boolean.toString(portal.defaultPortal()));
        String output = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace(entry.getKey(), entry.getValue());
        }
        return output;
    }

    private AccessPolicy next(AccessPolicy policy) {
        AccessPolicy[] values = AccessPolicy.values();
        return values[(policy.ordinal() + 1) % values.length];
    }

    private void setCustomModelData(ItemMeta meta, int value) {
        org.bukkit.inventory.meta.components.CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.of((float) value));
        meta.setCustomModelDataComponent(component);
    }
}
