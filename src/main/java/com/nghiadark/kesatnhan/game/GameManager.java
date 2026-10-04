package com.nghiadark.kesatnhan.game;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import com.nghiadark.kesatnhan.map.GameMap;
import com.nghiadark.kesatnhan.map.MatchMap;
import com.nghiadark.kesatnhan.room.Room;
import com.nghiadark.kesatnhan.room.RoomState;
import com.nghiadark.kesatnhan.util.Items;
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
  public int forceMin() { return plugin.getConfig().getInt("force-start-min", 2); }

  // ---------- join / leave ----------
  public boolean join(Player p, Room r) {
    if (r.state() == RoomState.PLAYING || r.state() == RoomState.ENDING) {
      p.sendMessage(plugin.msg().get("join-ingame")); return false;
    }
    if (r.players().size() >= max()) { p.sendMessage(plugin.msg().get("join-full")); return false; }
    Room old = plugin.rooms().byPlayer(p.getUniqueId());
    if (old != null && old != r) leave(p, old);
    if (r.players().contains(p.getUniqueId())) return true;
    r.players().add(p.getUniqueId());
    if (r.waiting() != null) p.teleport(r.waiting());
    p.setGameMode(GameMode.ADVENTURE);
    p.getInventory().clear();
    String m = plugin.msg().get("join-ok", Map.of("room", r.id(), "count", String.valueOf(r.players().size())));
    p.sendMessage(m);
    broadcast(r, m);
    maybeStartCountdown(r);
    return true;
  }

  public void leave(Player p, Room r) {
    r.players().remove(p.getUniqueId());
    r.spectators().remove(p.getUniqueId());
    r.roles().remove(p.getUniqueId());
    p.getInventory().clear();
    p.setGameMode(GameMode.SURVIVAL);
    Location lobby = plugin.rooms().lobby();
    if (lobby != null) p.teleport(lobby);
    p.sendMessage(plugin.msg().get("leave-ok"));
    if (r.state() == RoomState.PLAYING) checkWin(r);
    else if (r.players().size() < min()) cancelCountdown(r, true);
  }

  public void broadcast(Room r, String message) {
    for (UUID id : r.players()) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) p.sendMessage(message);
    }
  }

  // ---------- countdown ----------
  public void maybeStartCountdown(Room r) {
    if (r.state() != RoomState.WAITING) {
      if (r.state() == RoomState.STARTING && r.players().size() >= max()) {
        if (r.countdown() > plugin.getConfig().getInt("full-start-short-seconds", 5))
          r.countdown(plugin.getConfig().getInt("full-start-short-seconds", 5));
      }
      return;
    }
    if (r.players().size() < min()) return;
    r.state(RoomState.STARTING);
    r.countdown(countdownSec());
    cancelTask(r);
    BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
      if (r.players().size() < min()) { cancelCountdown(r, true); return; }
      if (r.players().size() >= max() && r.countdown() > plugin.getConfig().getInt("full-start-short-seconds", 5))
        r.countdown(plugin.getConfig().getInt("full-start-short-seconds", 5));
      r.countdown(r.countdown() - 1);
      if (r.countdown() <= 0) { beginMatch(r, false); return; }
      if (r.countdown() % 10 == 0 || r.countdown() <= 5)
        broadcast(r, plugin.msg().get("game-countdown", Map.of("count", String.valueOf(r.players().size()), "sec", String.valueOf(r.countdown()))));
    }, 20L, 20L);
    countdownTasks.put(r.id(), t);
  }

  public void cancelCountdown(Room r, boolean notify) {
    cancelTask(r);
    if (r.state() == RoomState.STARTING) {
      r.state(RoomState.WAITING);
      r.countdown(-1);
      if (notify) broadcast(r, plugin.msg().get("game-cancel"));
    }
  }

  private void cancelTask(Room r) {
    BukkitTask t = countdownTasks.remove(r.id());
    if (t != null) t.cancel();
  }

  // ---------- match flow: random map -> clone neu thieu ----------
  public void beginMatch(Room r, boolean forced) {
    cancelTask(r);
    int need = forced ? forceMin() : min();
    if (r.players().size() < need) {
      r.state(RoomState.WAITING);
      return;
    }
    int n = r.players().size();
    GameMap free = plugin.maps().pickFree(n);
    if (free != null) {
      MatchMap m = plugin.maps().buildDirect(free);
      if (m != null) { startOnMap(r, m); return; }
    }
    // het map ranh -> clone map tam
    GameMap src = plugin.maps().pickForClone(n);
    if (src == null) {
      broadcast(r, plugin.msg().get("no-map"));
      r.state(RoomState.WAITING);
      return;
    }
    broadcast(r, plugin.msg().get("preparing-map", Map.of("map", src.id())));
    plugin.maps().cloneAsync(src, m -> {
      if (m == null) {
        broadcast(r, plugin.msg().get("clone-fail"));
        r.state(RoomState.WAITING);
        return;
      }
      if (r.state() != RoomState.STARTING || r.players().size() < need) {
        // player out het trong luc clone -> huy + xoa clone
        plugin.maps().release(m);
        r.state(RoomState.WAITING);
        return;
      }
      startOnMap(r, m);
    });
  }

  private void startOnMap(Room r, MatchMap m) {
    List<Location> spawns = new ArrayList<>(m.spawns());
    Collections.shuffle(spawns);
    while (spawns.size() < r.players().size()) spawns.add(spawns.get(0));
    List<UUID> shuffled = new ArrayList<>(r.players());
    Collections.shuffle(shuffled);
    r.roles().clear(); r.hits().clear(); r.hitTime().clear(); r.spectators().clear();
    r.roles().put(shuffled.get(0), Role.MURDERER);
    if (shuffled.size() > 1) r.roles().put(shuffled.get(1), Role.SHERIFF);
    for (int i = 2; i < shuffled.size(); i++) r.roles().put(shuffled.get(i), Role.INNOCENT);

    int i = 0;
    for (UUID id : shuffled) {
      Player p = Bukkit.getPlayer(id);
      if (p == null) continue;
      p.teleport(spawns.get(i++ % spawns.size()));
      p.setGameMode(GameMode.ADVENTURE);
      p.getInventory().clear();
      p.setHealth(20); p.setFoodLevel(20);
      Role role = r.roles().get(id);
      if (role == Role.MURDERER) {
        p.getInventory().addItem(Items.murderSword());
        p.sendMessage(plugin.msg().get("role-murderer"));
      } else if (role == Role.SHERIFF) {
        p.getInventory().addItem(Items.sheriffBow());
        p.getInventory().addItem(new ItemStack(Material.ARROW, 1));
        p.sendMessage(plugin.msg().get("role-sheriff"));
      } else {
        p.sendMessage(plugin.msg().get("role-innocent"));
      }
      p.sendTitle(plugin.msg().get("game-start"), "", 10, 40, 10);
    }
    r.matchMap(m);
    r.state(RoomState.PLAYING);
    broadcast(r, plugin.msg().get("game-start"));
    broadcast(r, plugin.msg().get("map-picked", Map.of("map", m.id())));
  }

  public void forceStart(Room r) {
    if (r.state() == RoomState.PLAYING || r.state() == RoomState.ENDING) return;
    r.state(RoomState.STARTING);
    beginMatch(r, true);
  }

  public void end(Room r, String endMessage) {
    cancelTask(r);
    r.state(RoomState.ENDING);
    if (endMessage != null) broadcast(r, endMessage);
    for (UUID id : new ArrayList<>(r.players())) {
      Player p = Bukkit.getPlayer(id);
      if (p == null) continue;
      p.getInventory().clear();
      p.setGameMode(GameMode.SURVIVAL);
      Location lobby = plugin.rooms().lobby();
      if (lobby != null) p.teleport(lobby);
    }
    MatchMap m = r.matchMap();
    r.matchMap(null);
    int delay = plugin.getConfig().getInt("end-delay-seconds", 10);
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      r.players().clear(); r.spectators().clear(); r.roles().clear(); r.hits().clear(); r.hitTime().clear();
      r.state(RoomState.WAITING);
      plugin.maps().release(m);
    }, delay * 20L);
  }

  public void checkWin(Room r) {
    if (r.state() != RoomState.PLAYING) return;
    UUID murderer = null;
    int aliveVillagers = 0;
    for (UUID id : r.players()) {
      if (r.spectators().contains(id)) continue;
      Role role = r.roles().get(id);
      if (role == Role.MURDERER) murderer = id;
      else aliveVillagers++;
    }
    if (murderer == null) {
      end(r, plugin.msg().get("villagers-win"));
    } else if (aliveVillagers == 0) {
      Player m = Bukkit.getPlayer(murderer);
      if (m != null) m.sendTitle(plugin.msg().get("murderer-win"), "", 10, 60, 10);
      end(r, plugin.msg().get("murderer-win"));
    }
  }

  public void toSpectator(Room r, Player victim) {
    r.spectators().add(victim.getUniqueId());
    victim.getInventory().clear();
    victim.setGameMode(GameMode.SPECTATOR);
    Location s = (r.matchMap() != null && r.matchMap().spec() != null) ? r.matchMap().spec() : r.waiting();
    if (s != null) victim.teleport(s);
    victim.getInventory().setItem(8, Items.lobbyStar());
  }
}
