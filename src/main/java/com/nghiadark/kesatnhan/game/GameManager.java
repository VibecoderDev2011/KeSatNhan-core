package com.nghiadark.kesatnhan.game;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import com.nghiadark.kesatnhan.arena.Arena;
import com.nghiadark.kesatnhan.arena.ArenaState;
import com.nghiadark.kesatnhan.util.Items;
import com.nghiadark.kesatnhan.util.SpawnUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GameManager {
  private final KeSatNhanPlugin plugin;
  private final Map<String, BukkitTask> countdownTasks = new HashMap<>();

  public GameManager(KeSatNhanPlugin plugin) { this.plugin = plugin; }

  public int min() { return plugin.getConfig().getInt("min-players", 8); }
  public int max() { return plugin.getConfig().getInt("max-players", 10); }
  public int countdownSec() { return plugin.getConfig().getInt("countdown-seconds", 30); }

  public Set<Material> danger() {
    Set<Material> s = new HashSet<>();
    for (String n : plugin.getConfig().getStringList("dangerous-blocks")) {
      try { s.add(Material.valueOf(n)); } catch (Exception ignored) {}
    }
    return s;
  }

  // ---------- join / leave ----------
  public boolean join(Player p, Arena a) {
    if (a.state() == ArenaState.PLAYING || a.state() == ArenaState.ENDING) {
      p.sendMessage(plugin.msg().get("join-ingame")); return false;
    }
    if (a.players().size() >= max()) { p.sendMessage(plugin.msg().get("join-full")); return false; }
    Arena old = plugin.arenas().byPlayer(p.getUniqueId());
    if (old != null && old != a) leave(p, old);
    if (a.players().contains(p.getUniqueId())) return true;
    a.players().add(p.getUniqueId());
    if (a.waiting() != null) p.teleport(a.waiting());
    p.setGameMode(GameMode.ADVENTURE);
    p.getInventory().clear();
    p.sendMessage(plugin.msg().get("join-ok", Map.of("arena", a.id(), "count", String.valueOf(a.players().size()))));
    broadcast(a, plugin.msg().get("join-ok", Map.of("arena", a.id(), "count", String.valueOf(a.players().size()))));
    maybeStartCountdown(a);
    return true;
  }

  public void leave(Player p, Arena a) {
    a.players().remove(p.getUniqueId());
    a.spectators().remove(p.getUniqueId());
    a.roles().remove(p.getUniqueId());
    p.getInventory().clear();
    p.setGameMode(GameMode.SURVIVAL);
    Location lobby = plugin.arenas().lobby();
    if (lobby != null) p.teleport(lobby);
    p.sendMessage(plugin.msg().get("leave-ok"));
    if (a.state() == ArenaState.PLAYING) checkWin(a);
    else if (a.players().size() < min()) cancelCountdown(a, true);
  }

  public void broadcast(Arena a, String message) {
    for (UUID id : a.players()) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) p.sendMessage(message);
    }
  }

  // ---------- countdown ----------
  public void maybeStartCountdown(Arena a) {
    if (a.state() != ArenaState.WAITING) {
      // du 10 nguoi -> rut ngan
      if (a.state() == ArenaState.STARTING && a.players().size() >= max()) {
        if (a.countdown() > plugin.getConfig().getInt("full-start-short-seconds", 5))
          a.countdown(plugin.getConfig().getInt("full-start-short-seconds", 5));
      }
      return;
    }
    if (a.players().size() < min()) return;
    a.state(ArenaState.STARTING);
    a.countdown(countdownSec());
    cancelTask(a);
    BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
      if (a.players().size() < min()) { cancelCountdown(a, true); return; }
      if (a.players().size() >= max() && a.countdown() > plugin.getConfig().getInt("full-start-short-seconds", 5))
        a.countdown(plugin.getConfig().getInt("full-start-short-seconds", 5));
      a.countdown(a.countdown() - 1);
      if (a.countdown() <= 0) { start(a); return; }
      if (a.countdown() % 10 == 0 || a.countdown() <= 5)
        broadcast(a, plugin.msg().get("game-countdown", Map.of("count", String.valueOf(a.players().size()), "sec", String.valueOf(a.countdown()))));
    }, 20L, 20L);
    countdownTasks.put(a.id(), t);
  }

  public void cancelCountdown(Arena a, boolean notify) {
    cancelTask(a);
    if (a.state() == ArenaState.STARTING) {
      a.state(ArenaState.WAITING);
      a.countdown(-1);
      if (notify) broadcast(a, plugin.msg().get("game-cancel"));
    }
  }

  private void cancelTask(Arena a) {
    BukkitTask t = countdownTasks.remove(a.id());
    if (t != null) t.cancel();
  }

  // ---------- start / end ----------
  public void start(Arena a) {
    cancelTask(a);
    if (!a.hasRegion() || a.waiting() == null) {
      broadcast(a, plugin.msg().get("need-both-pos"));
      a.state(ArenaState.WAITING);
      return;
    }
    List<Location> spawns = SpawnUtil.scatter(a, a.players().size(), danger(), plugin.getConfig().getInt("min-scatter-distance", 5));
    if (spawns.size() < a.players().size()) {
      // fallback: dung waiting neu khong du diem an toan
      while (spawns.size() < a.players().size()) spawns.add(a.waiting());
    }
    // chia role: 1 sat nhan + 1 canh sat
    List<UUID> shuffled = new ArrayList<>(a.players());
    Collections.shuffle(shuffled);
    a.roles().clear(); a.hits().clear(); a.hitTime().clear(); a.spectators().clear();
    a.roles().put(shuffled.get(0), Role.MURDERER);
    if (shuffled.size() > 1) a.roles().put(shuffled.get(1), Role.SHERIFF);
    for (int i = 2; i < shuffled.size(); i++) a.roles().put(shuffled.get(i), Role.INNOCENT);

    int i = 0;
    for (UUID id : shuffled) {
      Player p = Bukkit.getPlayer(id);
      if (p == null) continue;
      p.teleport(spawns.get(i++ % spawns.size()));
      p.setGameMode(GameMode.ADVENTURE);
      p.getInventory().clear();
      p.setHealth(20); p.setFoodLevel(20);
      Role r = a.roles().get(id);
      if (r == Role.MURDERER) {
        p.getInventory().addItem(Items.murderSword());
        p.sendMessage(plugin.msg().get("role-murderer"));
      } else if (r == Role.SHERIFF) {
        p.getInventory().addItem(Items.sheriffBow());
        p.getInventory().addItem(new ItemStack(Material.ARROW, 1));
        p.sendMessage(plugin.msg().get("role-sheriff"));
      } else {
        p.sendMessage(plugin.msg().get("role-innocent"));
      }
      p.sendTitle(plugin.msg().get("game-start"), "", 10, 40, 10);
    }
    a.state(ArenaState.PLAYING);
    broadcast(a, plugin.msg().get("game-start"));
  }

  public void forceStart(Arena a) {
    if (a.state() == ArenaState.PLAYING) return;
    a.state(ArenaState.WAITING);
    start(a);
  }

  public void end(Arena a, String endMessage) {
    cancelTask(a);
    a.state(ArenaState.ENDING);
    if (endMessage != null) broadcast(a, endMessage);
    for (UUID id : new ArrayList<>(a.players())) {
      Player p = Bukkit.getPlayer(id);
      if (p == null) continue;
      p.getInventory().clear();
      p.setGameMode(GameMode.SURVIVAL);
      Location lobby = plugin.arenas().lobby();
      if (lobby != null) p.teleport(lobby);
    }
    int delay = plugin.getConfig().getInt("end-delay-seconds", 10);
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      a.players().clear(); a.spectators().clear(); a.roles().clear(); a.hits().clear(); a.hitTime().clear();
      a.state(ArenaState.WAITING);
    }, delay * 20L);
  }

  /** Goi sau moi cai chet / roi game de kiem tra thang thua. */
  public void checkWin(Arena a) {
    if (a.state() != ArenaState.PLAYING) return;
    UUID murderer = null;
    int aliveVillagers = 0;
    for (UUID id : a.players()) {
      if (a.spectators().contains(id)) continue;
      Role r = a.roles().get(id);
      if (r == Role.MURDERER) murderer = id;
      else aliveVillagers++;
    }
    if (murderer == null) { // sat nhan chet/roi -> dan thang
      end(a, plugin.msg().get("villagers-win"));
    } else if (aliveVillagers == 0) { // het dan + canh sat -> sat nhan thang
      Player m = Bukkit.getPlayer(murderer);
      if (m != null) m.sendTitle(plugin.msg().get("murderer-win"), "", 10, 60, 10);
      end(a, plugin.msg().get("murderer-win"));
    }
  }

  /** Bien nan nhan thanh khan gia quan sat tran dau. */
  public void toSpectator(Arena a, Player victim) {
    a.spectators().add(victim.getUniqueId());
    victim.getInventory().clear();
    victim.setGameMode(GameMode.SPECTATOR);
    Location s = a.spec() != null ? a.spec() : a.waiting();
    if (s != null) victim.teleport(s);
    // cap sao Nether de ve sanh nhanh (slot 8 khi SPECTATOR van giu duoc item hien thi)
    victim.getInventory().setItem(8, Items.lobbyStar());
  }
}
