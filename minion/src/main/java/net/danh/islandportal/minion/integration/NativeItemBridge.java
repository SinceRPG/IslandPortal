package net.danh.islandportal.minion.integration;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import net.danh.islandportal.minion.model.MinionIntegrationSettings;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class NativeItemBridge {

    private final Server server;
    private final MinionIntegrationSettings settings;
    private final Map<String, NativeItemProvider> providers = new LinkedHashMap<>();

    public NativeItemBridge(Server server, MinionIntegrationSettings settings) {
        this.server = server;
        this.settings = settings;
        registerProviders();
    }

    public ItemStack item(String key, Material fallback, int amount) {
        NativeItemProvider provider = provider(key);
        ItemStack resolved = provider == null ? null : provider.item(id(key));
        if (resolved == null) {
            resolved = new ItemStack(material(key, fallback), Math.max(1, amount));
        } else {
            resolved.setAmount(Math.max(1, amount));
        }
        return resolved;
    }

    public Material material(String key, Material fallback) {
        if (key == null) {
            return fallback;
        }
        String normalized = key.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        Material material = Material.matchMaterial(normalized);
        return material == null ? fallback : material;
    }

    public boolean matches(ItemStack item, String key, Material fallback) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        NativeItemProvider provider = provider(key);
        if (provider != null) {
            return provider.matches(item, id(key));
        }
        ItemStack expected = item(key, fallback, item.getAmount());
        expected.setAmount(item.getAmount());
        return item.isSimilar(expected);
    }

    public String storageKey(String key, Material fallback) {
        if (key == null || key.isBlank()) {
            return fallback.name();
        }
        if (provider(key) != null) {
            return key.toLowerCase(Locale.ROOT);
        }
        return material(key, fallback).name();
    }

    private NativeItemProvider provider(String key) {
        if (key == null || !key.contains(":")) {
            return null;
        }
        return providers.get(key.split(":", 2)[0].toLowerCase(Locale.ROOT));
    }

    private String id(String key) {
        return key == null || !key.contains(":") ? key : key.split(":", 2)[1];
    }

    private void registerProviders() {
        if (settings.headDatabase() && server.getPluginManager().isPluginEnabled("HeadDatabase")) {
            NativeItemProvider provider = new HeadDatabaseItemProvider();
            providers.put("hdb", provider);
            providers.put("headdatabase", provider);
        }
        if (settings.itemsAdder() && server.getPluginManager().isPluginEnabled("ItemsAdder")) {
            NativeItemProvider provider = new ItemsAdderItemProvider();
            providers.put("itemsadder", provider);
            providers.put("ia", provider);
        }
        if (settings.oraxen() && server.getPluginManager().isPluginEnabled("Oraxen")) {
            providers.put("oraxen", new OraxenItemProvider());
        }
        if (settings.nexo() && server.getPluginManager().isPluginEnabled("Nexo")) {
            providers.put("nexo", new NexoItemProvider());
        }
        if (settings.mmoItems() && server.getPluginManager().isPluginEnabled("MMOItems")) {
            NativeItemProvider provider = new MmoItemsItemProvider();
            providers.put("mmoitems", provider);
            providers.put("mmoitem", provider);
        }
        if (settings.mythicMobs() && server.getPluginManager().isPluginEnabled("MythicMobs")) {
            NativeItemProvider provider = new MythicMobsItemProvider();
            providers.put("mythicmobs", provider);
            providers.put("mythic", provider);
        }
    }
}
