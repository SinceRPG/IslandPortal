package net.danh.islandportal.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import net.danh.islandportal.IslandPortal;
import net.danh.islandportal.minion.config.MinionConfig;
import net.danh.islandportal.minion.model.MinionFuel;
import net.danh.islandportal.minion.model.MinionType;
import net.danh.islandportal.minion.service.MinionService;
import net.danh.islandportal.npc.config.IslandNpcConfig;
import net.danh.islandportal.npc.model.NpcType;
import net.danh.islandportal.npc.service.IslandNpcService;
import net.danh.islandportal.portal.config.PortalConfig;
import net.danh.islandportal.portal.service.PortalService;
import net.danh.islandportal.portal.model.PortalType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class IslandPortalCommand {

    private final IslandPortal plugin;
    private final PortalConfig config;
    private final PortalService portalService;
    private final IslandNpcConfig npcConfig;
    private final IslandNpcService npcService;
    private final MinionConfig minionConfig;
    private final MinionService minionService;

    public IslandPortalCommand(IslandPortal plugin, PortalConfig config, PortalService portalService, IslandNpcConfig npcConfig, IslandNpcService npcService, MinionConfig minionConfig, MinionService minionService) {
        this.plugin = plugin;
        this.config = config;
        this.portalService = portalService;
        this.npcConfig = npcConfig;
        this.npcService = npcService;
        this.minionConfig = minionConfig;
        this.minionService = minionService;
    }

    public void register(ReloadableRegistrarEvent<Commands> event) {
        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("islandportal")
                .requires(source -> source.getSender().hasPermission("islandportal.admin"))
                .then(Commands.literal("reload").executes(this::reload))
                .then(Commands.literal("help").executes(this::help))
                .then(Commands.literal("settarget")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(this::suggestPortalTypes)
                                .executes(this::setTarget)))
                .then(Commands.literal("give")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(this::suggestPortalTypes)
                                .executes(this::giveSelf)
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests(this::suggestPlayers)
                                        .executes(this::givePlayer)
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(this::givePlayer)))))
                .then(Commands.literal("create")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(this::suggestPortalTypes)
                                .executes(this::create)))
                .then(Commands.literal("createisland")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(this::suggestPortalTypes)
                                .executes(this::createIsland)))
                .then(Commands.literal("npc")
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(this::suggestNpcTypes)
                                        .executes(this::spawnNpc)
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(this::spawnNpc))))
                        .then(Commands.literal("remove")
                                .executes(this::removeNpc)))
                .then(Commands.literal("minion")
                        .then(Commands.literal("give")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(this::suggestMinionTypes)
                                        .executes(this::giveMinionSelf)
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(this::suggestPlayers)
                                                .executes(this::giveMinionPlayer)
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(this::giveMinionPlayer)))))
                        .then(Commands.literal("fuel")
                                .then(Commands.argument("fuel", StringArgumentType.word())
                                        .suggests(this::suggestMinionFuels)
                                        .executes(this::giveFuelSelf)
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(this::suggestPlayers)
                                                .executes(this::giveFuelPlayer)
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(this::giveFuelPlayer)))))
                        .then(Commands.literal("booster")
                                .then(Commands.argument("fuel", StringArgumentType.word())
                                        .suggests(this::suggestMinionFuels)
                                        .executes(this::giveFuelSelf)
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests(this::suggestPlayers)
                                                .executes(this::giveFuelPlayer)
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(this::giveFuelPlayer)))))
                        .then(Commands.literal("remove")
                                .executes(this::removeMinion))
                        .then(Commands.literal("inspect")
                                .executes(this::inspectMinion)))
                .then(Commands.literal("remove").executes(this::remove))
                .executes(this::usage);

        event.registrar().register(command.build(), config.commandDescription(), config.commandAliases());
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        plugin.reloadPortalConfig();
        sender(context).sendMessage(config.message("reloaded"));
        return Command.SINGLE_SUCCESS;
    }

    private int help(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        for (String line : List.of(
                config.message("help.header"),
                config.message("help.reload"),
                config.message("help.settarget"),
                config.message("help.give"),
                config.message("help.create"),
                config.message("help.createisland"),
                config.message("help.npc"),
                config.message("help.minion"),
                config.message("help.remove"),
                config.message("help.pickup")
        )) {
            sender.sendMessage(line);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int setTarget(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = player(sender);
        if (player == null) {
            sender.sendMessage(config.message("players-only"));
            return 0;
        }
        PortalType type = type(context, "type");
        if (type == null) {
            sender.sendMessage(config.message("unknown-type"));
            return 0;
        }
        config.setTarget(type.id(), player.getLocation());
        sender.sendMessage(config.message("target-updated").replace("%type%", type.id()));
        return Command.SINGLE_SUCCESS;
    }

    private int giveSelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = player(sender);
        if (player == null) {
            sender.sendMessage(config.message("give-usage"));
            return 0;
        }
        return give(context, player, 1);
    }

    private int givePlayer(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(context, "player"));
        if (target == null) {
            sender.sendMessage(config.message("player-not-found"));
            return 0;
        }
        int amount = context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("amount"))
                ? IntegerArgumentType.getInteger(context, "amount")
                : 1;
        return give(context, target, amount);
    }

    private int give(CommandContext<CommandSourceStack> context, Player target, int amount) {
        CommandSender sender = sender(context);
        PortalType type = type(context, "type");
        if (type == null) {
            sender.sendMessage(config.message("unknown-type"));
            return 0;
        }
        portalService.givePortalItem(target, type, amount);
        sender.sendMessage(config.message("item-given").replace("%amount%", String.valueOf(amount)).replace("%type%", type.id()).replace("%player%", target.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = player(sender);
        if (player == null) {
            sender.sendMessage(config.message("players-only"));
            return 0;
        }
        PortalType type = type(context, "type");
        if (type == null) {
            sender.sendMessage(config.message("unknown-type"));
            return 0;
        }
        Location base = player.getLocation().getBlock().getLocation();
        portalService.createPortal("manual:" + base.getWorld().getName() + ":" + base.getBlockX() + ":" + base.getBlockY() + ":" + base.getBlockZ(), type, base, player.getFacing());
        sender.sendMessage(config.message("portal-created").replace("%type%", type.id()));
        return Command.SINGLE_SUCCESS;
    }

    private int createIsland(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = player(sender);
        if (player == null) {
            sender.sendMessage(config.message("players-only"));
            return 0;
        }
        PortalType type = type(context, "type");
        if (type == null) {
            sender.sendMessage(config.message("unknown-type"));
            return 0;
        }
        Location origin = player.getLocation();
        String id = "manual-island:" + origin.getWorld().getName() + ":" + origin.getBlockX() + ":" + origin.getBlockY() + ":" + origin.getBlockZ();
        portalService.createDefaultPortal(id, type, origin, player, created -> player.sendMessage(created ? config.message("portal-island-created").replace("%type%", type.id()) : config.message("portal-island-no-space")));
        sender.sendMessage(config.message("portal-island-queued").replace("%type%", type.id()));
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = player(sender);
        if (player == null) {
            sender.sendMessage(config.message("players-only"));
            return 0;
        }
        boolean removed = portalService.removeNearestPortal(player.getLocation(), 6, true);
        sender.sendMessage(removed ? config.message("nearest-removed") : config.message("nearest-not-found"));
        return removed ? Command.SINGLE_SUCCESS : 0;
    }

    private int spawnNpc(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = player(sender);
        if (player == null) {
            sender.sendMessage(config.message("players-only"));
            return 0;
        }
        NpcType type = npcConfig.type(StringArgumentType.getString(context, "type"));
        if (type == null) {
            sender.sendMessage(config.message("unknown-npc-type"));
            return 0;
        }
        String id = context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("id"))
                ? StringArgumentType.getString(context, "id")
                : "manual:" + UUID.randomUUID();
        Location location = player.getLocation();
        boolean created = npcService.createManualNpc(id, type, location, player);
        sender.sendMessage(created ? config.message("npc-created").replace("%type%", type.id()).replace("%id%", id) : config.message("npc-create-failed"));
        return created ? Command.SINGLE_SUCCESS : 0;
    }

    private int removeNpc(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = player(sender);
        if (player == null) {
            sender.sendMessage(config.message("players-only"));
            return 0;
        }
        boolean removed = npcService.removeNearest(player.getLocation(), 6);
        sender.sendMessage(removed ? config.message("npc-removed") : config.message("npc-not-found"));
        return removed ? Command.SINGLE_SUCCESS : 0;
    }

    private int giveMinionSelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = player(sender);
        if (player == null) {
            sender.sendMessage(config.message("players-only"));
            return 0;
        }
        return giveMinion(context, player, 1);
    }

    private int giveMinionPlayer(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(context, "player"));
        if (target == null) {
            sender.sendMessage(config.message("player-not-found"));
            return 0;
        }
        int amount = context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("amount"))
                ? IntegerArgumentType.getInteger(context, "amount")
                : 1;
        return giveMinion(context, target, amount);
    }

    private int giveMinion(CommandContext<CommandSourceStack> context, Player target, int amount) {
        CommandSender sender = sender(context);
        MinionType type = minionConfig.type(StringArgumentType.getString(context, "type"));
        if (type == null) {
            sender.sendMessage(config.message("unknown-minion-type"));
            return 0;
        }
        minionService.giveMinionItem(target, type, amount);
        sender.sendMessage(config.message("minion-item-given").replace("%amount%", String.valueOf(amount)).replace("%type%", type.id()).replace("%player%", target.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int giveFuelSelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = player(sender);
        if (player == null) {
            sender.sendMessage(config.message("players-only"));
            return 0;
        }
        return giveFuel(context, player, 1);
    }

    private int giveFuelPlayer(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player target = Bukkit.getPlayerExact(StringArgumentType.getString(context, "player"));
        if (target == null) {
            sender.sendMessage(config.message("player-not-found"));
            return 0;
        }
        int amount = context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("amount"))
                ? IntegerArgumentType.getInteger(context, "amount")
                : 1;
        return giveFuel(context, target, amount);
    }

    private int giveFuel(CommandContext<CommandSourceStack> context, Player target, int amount) {
        CommandSender sender = sender(context);
        MinionFuel fuel = minionConfig.fuel(StringArgumentType.getString(context, "fuel"));
        if (fuel == null) {
            sender.sendMessage(config.message("unknown-minion-fuel"));
            return 0;
        }
        minionService.giveFuelItem(target, fuel, amount);
        sender.sendMessage(config.message("minion-fuel-given").replace("%amount%", String.valueOf(amount)).replace("%fuel%", fuel.id()).replace("%player%", target.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private int removeMinion(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = player(sender);
        if (player == null) {
            sender.sendMessage(config.message("players-only"));
            return 0;
        }
        boolean removed = minionService.removeNearest(player.getLocation(), 6, true);
        sender.sendMessage(removed ? config.message("minion-removed") : config.message("minion-not-found"));
        return removed ? Command.SINGLE_SUCCESS : 0;
    }

    private int inspectMinion(CommandContext<CommandSourceStack> context) {
        CommandSender sender = sender(context);
        Player player = player(sender);
        if (player == null) {
            sender.sendMessage(config.message("players-only"));
            return 0;
        }
        boolean inspected = minionService.inspectNearest(player, 6);
        sender.sendMessage(inspected ? config.message("minion-inspected") : config.message("minion-not-found"));
        return inspected ? Command.SINGLE_SUCCESS : 0;
    }

    private int usage(CommandContext<CommandSourceStack> context) {
        return help(context);
    }

    private CompletableFuture<Suggestions> suggestPortalTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (PortalType type : config.portalTypes()) {
            if (type.id().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(type.id());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(player.getName());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestNpcTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (NpcType type : npcConfig.npcTypes()) {
            if (type.id().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(type.id());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestMinionTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (MinionType type : minionConfig.types()) {
            if (type.id().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(type.id());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestMinionFuels(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String prefix = builder.getRemainingLowerCase();
        for (MinionFuel fuel : minionConfig.fuels()) {
            if (fuel.id().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(fuel.id());
            }
        }
        return builder.buildFuture();
    }

    private PortalType type(CommandContext<CommandSourceStack> context, String name) {
        return config.type(StringArgumentType.getString(context, name));
    }

    private CommandSender sender(CommandContext<CommandSourceStack> context) {
        return context.getSource().getSender();
    }

    private Player player(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }
}
