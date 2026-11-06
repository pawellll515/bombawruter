package pl.bombawruter;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

import io.papermc.paper.event.entity.EntityKnockbackEvent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Main extends JavaPlugin implements Listener {

    private final Map<UUID, FreezeData> zamrozeni =
            new ConcurrentHashMap<>();

    private final Map<UUID, BukkitTask> taski =
            new ConcurrentHashMap<>();

    private final Map<UUID, BukkitTask> taskiSerc =
            new ConcurrentHashMap<>();

    private ProtocolManager protocolManager;

    private record FreezeData(
            Location serverLocation,
            int entityId,
            Set<Integer> otherPlayerIds,
            float frozenHealth
    ) {}

    @Override
    public void onEnable() {

        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("Nie znaleziono ProtocolLib!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        protocolManager = ProtocolLibrary.getProtocolManager();

        Bukkit.getPluginManager().registerEvents(this, this);

        zarejestrujPakiety();

        getLogger().info("BombaWruter zostal wlaczony!");
    }

    @Override
    public void onDisable() {

        for (BukkitTask task : taski.values()) {
            task.cancel();
        }

        for (BukkitTask task : taskiSerc.values()) {
            task.cancel();
        }

        taski.clear();
        taskiSerc.clear();
        zamrozeni.clear();

        if (protocolManager != null) {
            protocolManager.removePacketListeners(this);
        }
    }

    private void zarejestrujPakiety() {

        // BLOKOWANIE DZIALAN ZAMROZONEGO GRACZA
        protocolManager.addPacketListener(
                new PacketAdapter(
                        this,
                        ListenerPriority.HIGHEST,
                        PacketType.Play.Client.POSITION,
                        PacketType.Play.Client.POSITION_LOOK,
                        PacketType.Play.Client.LOOK,
                        PacketType.Play.Client.GROUND,
                        PacketType.Play.Client.BLOCK_DIG,
                        PacketType.Play.Client.USE_ITEM_ON,
                        PacketType.Play.Client.USE_ITEM,
                        PacketType.Play.Client.USE_ENTITY,
                        PacketType.Play.Client.WINDOW_CLICK,
                        PacketType.Play.Client.HELD_ITEM_SLOT,
                        PacketType.Play.Client.SET_CREATIVE_SLOT,
                        PacketType.Play.Client.ARM_ANIMATION,
                        PacketType.Play.Client.ENTITY_ACTION,
                        PacketType.Play.Client.PICK_ITEM
                ) {

                    @Override
                    public void onPacketReceiving(PacketEvent event) {

                        if (czyZamrozony(event.getPlayer())) {
                            event.setCancelled(true);
                        }
                    }
                }
        );

        // ZATRZYMANIE RUCHU INNYCH GRACZY NA EKRANIE ZAMROZONEGO
        protocolManager.addPacketListener(
                new PacketAdapter(
                        this,
                        ListenerPriority.HIGHEST,
                        PacketType.Play.Server.REL_ENTITY_MOVE,
                        PacketType.Play.Server.REL_ENTITY_MOVE_LOOK,
                        PacketType.Play.Server.ENTITY_LOOK,
                        PacketType.Play.Server.ENTITY_HEAD_ROTATION,
                        PacketType.Play.Server.ENTITY_TELEPORT,
                        PacketType.Play.Server.ENTITY_POSITION_SYNC
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
                                    "Blad pakietu ruchu: "
                                            + exception.getMessage()
                            );
                        }
                    }
                }
        );

        /*
         * NAJWAZNIEJSZA CZESC:
         * Przechwytujemy aktualizacje zdrowia na samym koncu
         * i zawsze podmieniamy HP na wartosc zapisana przy zamrozeniu.
         */
        protocolManager.addPacketListener(
                new PacketAdapter(
                        this,
                        ListenerPriority.MONITOR,
                        PacketType.Play.Server.UPDATE_HEALTH
                ) {

                    @Override
                    public void onPacketSending(PacketEvent event) {

                        FreezeData data = zamrozeni.get(
                                event.getPlayer().getUniqueId()
                        );

                        if (data == null) {
                            return;
                        }

                        event.getPacket()
                                .getFloat()
                                .write(0, data.frozenHealth());
                    }
                }
        );

        // UKRYWANIE ANIMACJI OBRAZEN ZAMROZONEGO GRACZA
        protocolManager.addPacketListener(
                new PacketAdapter(
                        this,
                        ListenerPriority.HIGHEST,
                        PacketType.Play.Server.HURT_ANIMATION,
                        PacketType.Play.Server.DAMAGE_EVENT,
                        PacketType.Play.Server.ENTITY_STATUS,
                        PacketType.Play.Server.ENTITY_VELOCITY
                ) {

                    @Override
                    public void onPacketSending(PacketEvent event) {

                        if (czyZamrozony(event.getPlayer())) {
                            event.setCancelled(true);
                            return;
                        }

                        try {
                            int entityId = event.getPacket()
                                    .getIntegers()
                                    .read(0);

                            if (jestZamrozonyEntity(entityId)) {
                                event.setCancelled(true);
                            }

                        } catch (Exception ignored) {
                        }
                    }
                }
        );

        // WYCISZENIE DZWIEKOW U ZAMROZONEGO
        protocolManager.addPacketListener(
                new PacketAdapter(
                        this,
                        ListenerPriority.HIGHEST,
                        PacketType.Play.Server.ENTITY_SOUND,
                        PacketType.Play.Server.NAMED_SOUND_EFFECT
                ) {

                    @Override
                    public void onPacketSending(PacketEvent event) {

                        if (czyZamrozony(event.getPlayer())) {
                            event.setCancelled(true);
                        }
                    }
                }
        );

        // BLOKOWANIE KOREKT POZYCJI KLIENTA PODCZAS FREEZE
        protocolManager.addPacketListener(
                new PacketAdapter(
                        this,
                        ListenerPriority.HIGHEST,
                        PacketType.Play.Server.POSITION
                ) {

                    @Override
                    public void onPacketSending(PacketEvent event) {

                        if (czyZamrozony(event.getPlayer())) {
                            event.setCancelled(true);
                        }
                    }
                }
        );
    }

    private boolean jestZamrozonyEntity(int entityId) {

        for (FreezeData data : zamrozeni.values()) {

            if (data.entityId() == entityId) {
                return true;
            }
        }

        return false;
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

        Set<Integer> otherPlayerIds = new HashSet<>();

        for (Player other : Bukkit.getOnlinePlayers()) {

            if (!other.getUniqueId().equals(uuid)
                    && other.getWorld().equals(player.getWorld())) {

                otherPlayerIds.add(other.getEntityId());
            }
        }

        // ZAPISUJEMY HP DOKLADNIE W MOMENCIE ZAMROZENIA
        float zapisaneHP = (float) player.getHealth();

        FreezeData data = new FreezeData(
                player.getLocation().clone(),
                player.getEntityId(),
                Set.copyOf(otherPlayerIds),
                zapisaneHP
        );

        zamrozeni.put(uuid, data);

        // NATYCHMIAST WYSYLAMY ZAPAMIETANE HP
        wyslijZdrowie(player, zapisaneHP);

        /*
         * CO TICK WYSYLAMY ZAPAMIETANE HP.
         * Nawet jesli jakis inny plugin wysle prawdziwe HP,
         * klient regularnie otrzyma ponownie falszywa wartosc.
         */
        BukkitTask heartTask = Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {

                    if (!player.isOnline() || !czyZamrozony(player)) {
                        BukkitTask current = taskiSerc.remove(uuid);

                        if (current != null) {
                            current.cancel();
                        }

                        return;
                    }

                    FreezeData currentData = zamrozeni.get(uuid);

                    if (currentData != null) {
                        wyslijZdrowie(
                                player,
                                currentData.frozenHealth()
                        );
                    }
                },
                1L,
                1L
        );

        taskiSerc.put(uuid, heartTask);

        // AUTOMATYCZNE ODMROZENIE PO 30 SEKUNDACH
        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                this,
                () -> odmroz(player),
                600L
        );

        taski.put(uuid, task);
    }

    private void odmroz(Player player) {

        UUID uuid = player.getUniqueId();

        FreezeData data = zamrozeni.remove(uuid);

        BukkitTask task = taski.remove(uuid);

        if (task != null) {
            task.cancel();
        }

        // ZATRZYMUJEMY WYSYLANIE FALSZYWYCH SERC
        BukkitTask heartTask = taskiSerc.remove(uuid);

        if (heartTask != null) {
            heartTask.cancel();
        }

        if (data == null || !player.isOnline()) {
            return;
        }

        // PRZYWRACAMY RZECZYWISTA POZYCJE SERWEROWA
        player.teleport(data.serverLocation());

        // ODSWIEZAMY WIDOK INNYCH GRACZY
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

        // WYSYLAMY PRAWDZIWE HP PO ODMROZENIU
        wyslijZdrowie(player, (float) player.getHealth());

        // ODTWARZAMY 20 LOSOWYCH DZWIEKOW
        odtworzDzwieki(player);
    }

    private void wyslijZdrowie(Player player, float health) {

        if (!player.isOnline()) {
            return;
        }

        try {
            var packet = protocolManager.createPacket(
                    PacketType.Play.Server.UPDATE_HEALTH
            );

            packet.getFloat().write(0, health);

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
                    "Blad wysylania zdrowia: "
                            + exception.getMessage()
            );
        }
    }

    private void odtworzDzwieki(Player player) {

        Sound[] dzwieki = {
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                Sound.ENTITY_PLAYER_ATTACK_CRIT
        };

        for (int i = 0; i < 20; i++) {

            long opoznienie = i % 3;

            Bukkit.getScheduler().runTaskLater(
                    this,
                    () -> {

                        if (!player.isOnline()) {
                            return;
                        }

                        ThreadLocalRandom random =
                                ThreadLocalRandom.current();

                        Sound sound = dzwieki[
                                random.nextInt(dzwieki.length)
                        ];

                        float pitch = (float) random.nextDouble(
                                0.8,
                                1.3
                        );

                        player.playSound(
                                player.getLocation(),
                                sound,
                                1.0f,
                                pitch
                        );
                    },
                    opoznienie
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
    public void onKnockback(EntityKnockbackEvent event) {

        Entity entity = event.getEntity();

        if (entity instanceof Player player
                && czyZamrozony(player)) {

            // OBRAZENIA NADAL SA NALICZANE,
            // ALE GRACZ NIE OTRZYMUJE ODRZUTU.
            event.setCancelled(true);
        }
    }

    // NATYCHMIASTOWE ZAKONCZENIE FREEZE PO SMIERCI
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {

        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        zamrozeni.remove(uuid);

        BukkitTask task = taski.remove(uuid);

        if (task != null) {
            task.cancel();
        }

        BukkitTask heartTask = taskiSerc.remove(uuid);

        if (heartTask != null) {
            heartTask.cancel();
        }
    }

    // ODSWIEZENIE ZDROWIA PO ODRODZENIU
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {

        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(this, () -> {

            if (player.isOnline()) {
                wyslijZdrowie(
                        player,
                        (float) player.getHealth()
                );
            }

        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        UUID uuid = event.getPlayer().getUniqueId();

        zamrozeni.remove(uuid);

        BukkitTask task = taski.remove(uuid);

        if (task != null) {
            task.cancel();
        }

        BukkitTask heartTask = taskiSerc.remove(uuid);

        if (heartTask != null) {
            heartTask.cancel();
        }
    }

    private boolean czyZamrozony(Player player) {

        return zamrozeni.containsKey(player.getUniqueId());
    }
}
