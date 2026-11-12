package ch.livio.ominousbottlefarm;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.block.Banner;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class OminousBottleFarm extends JavaPlugin implements Listener {
    private final Random random = new Random();
    private final Map<UUID, Integer> captainAmplifiers = new HashMap<>();
    private final Map<UUID, Boolean> farmPillagers = new HashMap<>();
    private NamespacedKey farmKey;
    private NamespacedKey captainKey;
    private Location corner1;
    private Location corner2;
    private boolean running = false;
    private BukkitTask task;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        farmKey = new NamespacedKey(this, "farm_pillager");
        captainKey = new NamespacedKey(this, "farm_captain");
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("OminousBottleFarm enabled. Use /obfarm for setup.");
    }

    @Override
    public void onDisable() {
        stopFarm();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("obfarm.admin")) {
            sender.sendMessage("§cYou do not have permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/obfarm pos1 | pos2 | set | start | stop | status | reload");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "pos1" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                corner1 = floorLocation(player.getLocation());
                sender.sendMessage("§aCorner 1 set to " + coords(corner1));
            }
            case "pos2" -> {
                if (!(sender instanceof org.bukkit.entity.Player player)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                corner2 = floorLocation(player.getLocation());
                sender.sendMessage("§aCorner 2 set to " + coords(corner2));
            }
            case "set" -> {
                if (corner1 == null || corner2 == null || !corner1.getWorld().equals(corner2.getWorld())) {
                    sender.sendMessage("§cSet pos1 and pos2 in the same world first.");
                    return true;
                }
                if (corner1.getBlockY() != corner2.getBlockY()) {
                    sender.sendMessage("§cBoth corners must be on the same floor height.");
                    return true;
                }
                getConfig().set("farm.world", corner1.getWorld().getName());
                getConfig().set("farm.min-x", Math.min(corner1.getBlockX(), corner2.getBlockX()));
                getConfig().set("farm.max-x", Math.max(corner1.getBlockX(), corner2.getBlockX()));
                getConfig().set("farm.floor-y", corner1.getBlockY());
                getConfig().set("farm.min-z", Math.min(corner1.getBlockZ(), corner2.getBlockZ()));
                getConfig().set("farm.max-z", Math.max(corner1.getBlockZ(), corner2.getBlockZ()));
                saveConfig();
                sender.sendMessage("§aFarm area saved.");
            }
            case "start" -> {
                if (!getConfig().contains("farm.world")) {
                    sender.sendMessage("§cDefine the farm first with pos1, pos2 and set.");
                    return true;
                }
                startFarm();
                sender.sendMessage("§aFarm started.");
            }
            case "stop" -> {
                stopFarm();
                sender.sendMessage("§eFarm stopped.");
            }
            case "status" -> sender.sendMessage(running ? "§aFarm is running." : "§eFarm is stopped.");
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("§aConfig reloaded.");
            }
            default -> sender.sendMessage("§e/obfarm pos1 | pos2 | set | start | stop | status | reload");
        }
        return true;
    }

    private Location floorLocation(Location playerLocation) {
        return new Location(playerLocation.getWorld(), playerLocation.getBlockX(),
                playerLocation.getBlockY() - 1, playerLocation.getBlockZ());
    }

    private String coords(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private void startFarm() {
        stopFarm();
        running = true;
        long interval = Math.max(1, getConfig().getLong("spawn-interval-seconds", 5));
        task = Bukkit.getScheduler().runTaskTimer(this, this::spawnTick, 20L, interval * 20L);
    }

    private void stopFarm() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void spawnTick() {
        String worldName = getConfig().getString("farm.world");
        World world = Bukkit.getWorld(worldName == null ? "" : worldName);
        if (world == null) return;

        int radius = getConfig().getInt("activation-radius", 128);
        boolean active = Bukkit.getOnlinePlayers().stream().anyMatch(p ->
                p.getWorld().equals(world) &&
                p.getLocation().distanceSquared(new Location(world,
                        (getConfig().getInt("farm.min-x") + getConfig().getInt("farm.max-x")) / 2.0,
                        getConfig().getInt("farm.floor-y") + 1,
                        (getConfig().getInt("farm.min-z") + getConfig().getInt("farm.max-z")) / 2.0)) <= (double) radius * radius);
        if (!active) return;

        long alive = world.getEntities().stream().filter(e -> isFarmPillager(e)).count();
        int maxAlive = getConfig().getInt("max-alive-pillagers", 12);
        if (alive >= maxAlive) return;

        int minX = getConfig().getInt("farm.min-x");
        int maxX = getConfig().getInt("farm.max-x");
        int minZ = getConfig().getInt("farm.min-z");
        int maxZ = getConfig().getInt("farm.max-z");
        int floorY = getConfig().getInt("farm.floor-y");

        for (int attempt = 0; attempt < 12; attempt++) {
            int x = minX + random.nextInt(Math.max(1, maxX - minX + 1));
            int z = minZ + random.nextInt(Math.max(1, maxZ - minZ + 1));
            Block floor = world.getBlockAt(x, floorY, z);
            if (!floor.getType().isSolid()) continue;
            if (!world.getBlockAt(x, floorY + 1, z).isPassable()) continue;
            if (!world.getBlockAt(x, floorY + 2, z).isPassable()) continue;

            Location spawn = new Location(world, x + 0.5, floorY + 1, z + 0.5);
            Entity entity = world.spawnEntity(spawn, EntityType.PILLAGER);
            if (!(entity instanceof Pillager pillager)) {
                entity.remove();
                return;
            }
            pillager.getPersistentDataContainer().set(farmKey, PersistentDataType.BYTE, (byte) 1);
            farmPillagers.put(pillager.getUniqueId(), true);

            double chance = Math.max(0, Math.min(100, getConfig().getDouble("captain-chance-percent", 6.25)));
            if (random.nextDouble() * 100.0 < chance) {
                int amplifier = random.nextInt(5);
                captainAmplifiers.put(pillager.getUniqueId(), amplifier);
                pillager.getPersistentDataContainer().set(captainKey, PersistentDataType.INTEGER, amplifier);
                pillager.setPatrolLeader(true);
                pillager.getEquipment().setHelmet(new ItemStack(Material.WHITE_BANNER));
                pillager.getEquipment().setHelmetDropChance(0.0f);
            }
            return;
        }
    }

    private boolean isFarmPillager(Entity entity) {
        return entity instanceof Pillager &&
                entity.getPersistentDataContainer().has(farmKey, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onPillagerDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!isFarmPillager(entity)) return;
        Integer amplifier = entity.getPersistentDataContainer().get(captainKey, PersistentDataType.INTEGER);
        if (amplifier == null) return;

        ItemStack bottle = new ItemStack(Material.OMINOUS_BOTTLE);
        ItemMeta meta = bottle.getItemMeta();
        if (meta != null) {
            meta.setOminousBottleAmplifier(amplifier);
            bottle.setItemMeta(meta);
        }
        event.getDrops().add(bottle);
    }
}
