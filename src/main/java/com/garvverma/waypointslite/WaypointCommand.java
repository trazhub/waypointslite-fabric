package com.garvverma.waypointslite;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

public class WaypointCommand {

    private static final long OVERWRITE_WINDOW_MS = 10_000L;
    private static final Map<UUID, Map.Entry<String, Long>> pendingOverwrites = new HashMap<>();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var cmd = CommandManager.literal("wp")
                .requires(src -> src.getEntity() instanceof ServerPlayerEntity)
                .then(CommandManager.literal("list")
                    .executes(ctx -> list(ctx.getSource())))
                .then(CommandManager.literal("go")
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity p)
                                WaypointManager.getWaypointNames(p.getUuidAsString()).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> go(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("tp")
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity p)
                                WaypointManager.getWaypointNames(p.getUuidAsString()).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> go(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("set")
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("delete")
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity p)
                                WaypointManager.getWaypointNames(p.getUuidAsString()).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity p)
                                WaypointManager.getWaypointNames(p.getUuidAsString()).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));

            dispatcher.register(cmd);
            dispatcher.register(CommandManager.literal("waypoint").redirect(dispatcher.register(cmd)));
        });
    }

    private static int list(ServerCommandSource source) {
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        Set<String> names = WaypointManager.getWaypointNames(player.getUuidAsString());
        if (names.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§cYou have no waypoints."), false);
            return 0;
        }
        source.sendFeedback(() -> Text.literal("§aWaypoints (" + names.size() + "/" + WaypointManager.getMaxPerPlayer() + "):"), false);
        for (String name : names) {
            Waypoint w = WaypointManager.getWaypoint(player.getUuidAsString(), name);
            if (w == null) continue;
            String dim = switch (w.world) {
                case "world"          -> "Overworld";
                case "world_nether"   -> "Nether";
                case "world_the_end"  -> "End";
                default -> w.world;
            };
            source.sendFeedback(() -> Text.literal(
                "§e" + w.name + "§7 — " + dim +
                " §f(" + Math.round(w.x) + ", " + Math.round(w.y) + ", " + Math.round(w.z) + ")"
            ), false);
        }
        return names.size();
    }

    private static int go(ServerCommandSource source, String name) {
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        Waypoint w = WaypointManager.getWaypoint(player.getUuidAsString(), name.toLowerCase());
        if (w == null) {
            source.sendError(Text.literal("§cWaypoint §e" + name + "§c not found."));
            return 0;
        }

        // Map Paper plugin world names to Minecraft dimension IDs
        String dimId = switch (w.world) {
            case "world"         -> "minecraft:overworld";
            case "world_nether"  -> "minecraft:the_nether";
            case "world_the_end" -> "minecraft:the_end";
            default -> w.world.contains(":") ? w.world : "minecraft:" + w.world;
        };

        ServerWorld targetWorld = source.getServer().getWorld(
            RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimId))
        );
        if (targetWorld == null) {
            source.sendError(Text.literal("§cWorld §e" + dimId + "§c is not loaded."));
            return 0;
        }

        player.teleport(targetWorld, w.x, w.y, w.z, Set.of(), w.yaw, w.pitch, false);
        source.sendFeedback(() -> Text.literal("§aTeleported to §e" + w.name + "§a."), false);
        return 1;
    }

    private static int set(ServerCommandSource source, String name) {
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        String uuid = player.getUuidAsString();
        String lower = name.toLowerCase();

        if (WaypointManager.hasWaypoint(uuid, lower)) {
            Map.Entry<String, Long> pending = pendingOverwrites.get(player.getUuid());
            boolean confirmed = pending != null
                && pending.getKey().equals(lower)
                && System.currentTimeMillis() < pending.getValue();
            if (!confirmed) {
                pendingOverwrites.put(player.getUuid(),
                    Map.entry(lower, System.currentTimeMillis() + OVERWRITE_WINDOW_MS));
                source.sendFeedback(() -> Text.literal(
                    "§cWaypoint §e" + lower + "§c already exists. Run the command again within 10s to overwrite."
                ), false);
                return 1;
            }
            pendingOverwrites.remove(player.getUuid());
        }

        // Map dimension key back to Paper-style world name for storage compatibility
        String dimKey = player.getWorld().getRegistryKey().getValue().toString();
        String worldName = switch (dimKey) {
            case "minecraft:overworld"  -> "world";
            case "minecraft:the_nether" -> "world_nether";
            case "minecraft:the_end"    -> "world_the_end";
            default -> dimKey;
        };

        if (!WaypointManager.setWaypoint(uuid, lower, worldName,
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch())) {
            source.sendError(Text.literal(
                "§cWaypoint limit reached (§e" + WaypointManager.getMaxPerPlayer() + "§c). Delete one first."
            ));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("§aWaypoint §e" + lower + "§a saved."), false);
        return 1;
    }

    private static int delete(ServerCommandSource source, String name) {
        ServerPlayerEntity player = (ServerPlayerEntity) source.getEntity();
        if (WaypointManager.deleteWaypoint(player.getUuidAsString(), name.toLowerCase())) {
            source.sendFeedback(() -> Text.literal("§aWaypoint §e" + name + "§a deleted."), false);
            return 1;
        }
        source.sendError(Text.literal("§cWaypoint §e" + name + "§c not found."));
        return 0;
    }
}
