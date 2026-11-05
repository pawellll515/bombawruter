
package pl.bombawruter;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main extends JavaPlugin implements Listener {

    private final Map<UUID, Location> zamrozeni = new HashMap<>();
    private final Map<UUID, BukkitTask> taski = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BombaWruter zostal wlaczony!");
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : taski.values()) {
            task.cancel();
        }

        taski.clear();
        zamrozeni.clear();
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("bombawruter")) {
            return false;
        }

        if (!sender.hasPermission("bombawruter.use")) {
            sender.sendMessage("§cNie masz uprawnien!");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUzycie: /bombawruter <nick>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);

        if (target == null) {
            sender.sendMessage("§cTen gracz jest offline!");
            return true;
        }

        if (zamrozeni.containsKey(target.getUniqueId())) {
            sender.sendMessage("§cTen gracz jest juz zamrozony!");
            return true;
        }

        zamroz(target);

        sender.sendMessage(
                "§aZamrozono gracza §e" + target.getName()
                        + " §ana 30 sekund!"
        );

        target.sendMessage("§cZostales zamrozony na 30 sekund!");

        return true;
    }

    private void zamroz(Player player) {
        UUID uuid = player.getUniqueId();

        zamrozeni.put(uuid, player.getLocation().clone());

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                this,
                () -> odmroz(player),
                600L
        );

        taski.put(uuid, task);
    }

    private void odmroz(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask task = taski.remove(uuid);

        if (task != null) {
            task.cancel();
        }

        zamrozeni.remove(uuid);

        if (player.isOnline()) {
            player.setVelocity(new Vector(0, 0, 0));
            player.sendMessage("§aMozesz juz sie poruszac!");
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!zamrozeni.containsKey(player.getUniqueId())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        if (from.getX() != to.getX()
                || from.getY() != to.getY()
                || from.getZ() != to.getZ()
                || from.getYaw() != to.getYaw()
                || from.getPitch() != to.getPitch()) {

            event.setTo(from);
            player.setVelocity(new Vector(0, 0, 0));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (czyZamrozony(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (czyZamrozony(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (czyZamrozony(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        if (czyZamrozony(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && czyZamrozony(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && czyZamrozony(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player
                && czyZamrozony(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player
                && czyZamrozony(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        zamrozeni.remove(uuid);

        BukkitTask task = taski.remove(uuid);

        if (task != null) {
            task.cancel();
        }
    }

    private boolean czyZamrozony(Player player) {
        return zamrozeni.containsKey(player.getUniqueId());
    }
}
