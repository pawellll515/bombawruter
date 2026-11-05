
package pl.bombawruter;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Main extends JavaPlugin implements Listener {

    private final Map<UUID, FreezeData> zamrozeni =
            new ConcurrentHashMap<>();

    private final Map<UUID, BukkitTask> taski =
            new ConcurrentHashMap<>();

    private ProtocolManager protocolManager;

    private record FreezeData(
            double x,
            double y,
            double z,
            int selfEntityId,
            Set<Integer> otherPlayerIds
    ) {}

    @Override
    public void onEnable() {

        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("Nie znaleziono ProtocolLib!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        protocolManager = ProtocolLibrary.getProtocolManager();

        getServer().getPluginManager().registerEvents(this, this);

        zarejestrujPakiety();

        getLogger().info("BombaWruter 1.1.0 zostal wlaczony!");
    }

    @Override
    public void onDisable() {

        for (BukkitTask task : taski.values()) {
            task.cancel();
        }

        taski.clear();
        zamrozeni.clear();

        if (protocolManager != null) {
            protocolManager.removePacketListeners(this);
        }
    }

    private void zarejestrujPakiety() {

        protocolManager.addPacketListener(
                new PacketAdapter(
                        this,
                        ListenerPriority.NORMAL,
                        PacketType.Play.Server.REL_ENTITY_MOVE,
                        PacketType.Play.Server.REL_ENTITY_MOVE_LOOK,
                        PacketType.Play.Server.ENTITY_LOOK,
                        PacketType.Play.Server.ENTITY_HEAD_ROTATION,
                        PacketType.Play.Server.ENTITY_TELEPORT
                ) {

                    @Override
                    public void onPacketSending(PacketEvent event) {

                        FreezeData data = zamrozeni.get(
                                event.getPlayer().getUniqueId()
                        );

                        if (data == null) {
                            return;
                        }

                        try {
                            int entityId = event.getPacket()
                                    .getIntegers()
                                    .read(0);

                            if (data.otherPlayerIds().contains(entityId)) {
                                event.setCancelled(true);
                            }

                        } catch (Exception exception) {
                            getLogger().warning(
                                    "Blad odczytu pakietu pozycji: "
                                            + exception.getMessage()
                            );
                        }
                    }
                }
        );

        protocolManager.addPacketListener(
                new PacketAdapter(
                        this,
                        ListenerPriority.NORMAL,
                        PacketType.Play.Server.UPDATE_HEALTH
                ) {

                    @Override
                    public void onPacketSending(PacketEvent event) {

                        if (zamrozeni.containsKey(
                                event.getPlayer().getUniqueId()
                        )) {
                            event.setCancelled(true);
                        }
                    }
                }
        );

        protocolManager.addPacketListener(
                new PacketAdapter(
                        this,
                        ListenerPriority.NORMAL,
                        PacketType.Play.Server.ENTITY_SOUND
                ) {

                    @Override
                    public void onPacketSending(PacketEvent event) {

                        FreezeData data = zamrozeni.get(
                                event.getPlayer().getUniqueId()
                        );

                        if (data == null) {
                            return;
                        }

                        try {
                            int entityId = event.getPacket()
                                    .getIntegers()
                                    .read(0);

                            if (entityId != data.selfEntityId()) {
                                event.setCancelled(true);
                            }

                        } catch (Exception exception) {
                            getLogger().warning(
                                    "Blad odczytu dzwieku encji: "
                                            + exception.getMessage()
                            );
                        }
                    }
                }
        );

        protocolManager.addPacketListener(
                new PacketAdapter(
                        this,
                        ListenerPriority.NORMAL,
                        PacketType.Play.Server.NAMED_SOUND_EFFECT
                ) {

                    @Override
                    public void onPacketSending(PacketEvent event) {

                        FreezeData data = zamrozeni.get(
                                event.getPlayer().getUniqueId()
                        );

                        if (data == null) {
                            return;
                        }

                        try {
                            var integers = event.getPacket().getIntegers();

                            if (integers.size() < 3) {
                                return;
                            }

                            double soundX = integers.read(0) / 8.0;
                            double soundY = integers.read(1) / 8.0;
                            double soundZ = integers.read(2) / 8.0;

                            double dx = soundX - data.x();
                            double dy = soundY - data.y();
                            double dz = soundZ - data.z();

                            double distanceSquared =
                                    dx * dx + dy * dy + dz * dz;

                            if (distanceSquared > 9.0) {
                                event.setCancelled(true);
                            }

                        } catch (Exception exception) {
                            getLogger().warning(
                                    "Blad odczytu dzwieku: "
                                            + exception.getMessage()
                            );
                        }
                    }
                }
        );
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

        if (czyZamrozony(target)) {
            sender.sendMessage("§cTen gracz jest juz zamrozony!");
            return true;
        }

        zamroz(target);

        sender.sendMessage(
                "§aZamrozono gracza §e" + target.getName()
                        + " §ana 30 sekund!"
        );

        return true;
    }

    private void zamroz(Player player) {

        UUID uuid = player.getUniqueId();
        Location location = player.getLocation();

        Set<Integer> otherPlayerIds = new HashSet<>();

        for (Player other : Bukkit.getOnlinePlayers()) {

            if (!other.getUniqueId().equals(uuid)
                    && other.getWorld().equals(player.getWorld())) {

                otherPlayerIds.add(other.getEntityId());
            }
        }

        FreezeData data = new FreezeData(
                location.getX(),
                location.getY(),
                location.getZ(),
                player.getEntityId(),
                Set.copyOf(otherPlayerIds)
        );

        zamrozeni.put(uuid, data);

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

        if (!player.isOnline()) {
            return;
        }

        player.setVelocity(new Vector(0, 0, 0));

        wyslijZdrowie(player);

        for (Player other : Bukkit.getOnlinePlayers()) {

            if (other.equals(player)) {
                continue;
            }

            if (other.getWorld().equals(player.getWorld())
                    && player.canSee(other)) {

                player.hidePlayer(this, other);
                player.showPlayer(this, other);
            }
        }
    }

    private void wyslijZdrowie(Player player) {

        try {
            var packet = protocolManager.createPacket(
                    PacketType.Play.Server.UPDATE_HEALTH
            );

            packet.getFloat().write(
                    0,
                    (float) player.getHealth()
            );

            packet.getIntegers().write(
                    0,
                    player.getFoodLevel()
            );

            packet.getFloat().write(
                    1,
                    player.getSaturation()
            );

            protocolManager.sendServerPacket(player, packet);

        } catch (Exception exception) {
            getLogger().warning(
                    "Nie udalo sie odswiezyc zdrowia: "
                            + exception.getMessage()
            );
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();

        if (!czyZamrozony(player)) {
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
    public void onBlockBreak(BlockBreakEvent event) {

        if (czyZamrozony(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {

        if (czyZamrozony(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {

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
