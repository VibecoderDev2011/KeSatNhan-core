package com.nghiadark.kesatnhan.arena;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ArenaManager {
  private final KeSatNhanPlugin plugin;
  private final Map<String, Arena> arenas = new LinkedHashMap<>();
  private Location lobby;

  public ArenaManager(KeSatNhanPlugin plugin) {
    this.plugin = plugin;
    load();
  }

  public Collection<Arena> all() { return arenas.values(); }
  public Arena get(String id) { return arenas.get(id.toLowerCase()); }
  public Location lobby() { return lobby; }
  public void lobby(Location l) { this.lobby = l; save(); }

  public Arena create(String id) {
    Arena a = new Arena(id.toLowerCase());
    arenas.put(a.id(), a);
    save();
    return a;
  }

  public void delete(String id) {
    arenas.remove(id.toLowerCase());
    save();
  }

  public Arena byPlayer(UUID uuid) {
    for (Arena a : arenas.values())
      if (a.players().contains(uuid)) return a;
    return null;
  }

  /** Phong cho tot nhat de quick-join: WAITING/STARTING, chua day, dong nhat. */
  public Arena bestWaiting(int max) {
    return arenas.values().stream()
        .filter(a -> (a.state() == ArenaState.WAITING || a.state() == ArenaState.STARTING) && a.players().size() < max)
        .max(Comparator.comparingInt(a -> a.players().size()))
        .orElse(null);
  }

  // ---- persistence ----
  private File file() { return new File(plugin.getDataFolder(), "arenas.yml"); }

  public void load() {
    arenas.clear();
    File f = file();
    if (!f.exists()) { loadLobby(); return; }
    YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
    lobby = c.getLocation("lobby");
    if (c.getConfigurationSection("arenas") == null) return;
    for (String id : c.getConfigurationSection("arenas").getKeys(false)) {
      Arena a = new Arena(id);
      String p = "arenas." + id + ".";
      a.pos1(c.getLocation(p + "pos1"));
      a.pos2(c.getLocation(p + "pos2"));
      a.waiting(c.getLocation(p + "waiting"));
      a.spec(c.getLocation(p + "spec"));
      if (a.pos1() != null) a.world(a.pos1().getWorld().getName());
      arenas.put(id, a);
    }
  }

  private void loadLobby() {
    File f = new File(plugin.getDataFolder(), "arenas.yml");
    if (f.exists()) {
      YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
      lobby = c.getLocation("lobby");
    }
  }

  public void save() {
    YamlConfiguration c = new YamlConfiguration();
    c.set("lobby", lobby);
    for (Arena a : arenas.values()) {
      String p = "arenas." + a.id() + ".";
      c.set(p + "pos1", a.pos1());
      c.set(p + "pos2", a.pos2());
      c.set(p + "waiting", a.waiting());
      c.set(p + "spec", a.spec());
    }
    try { c.save(file()); } catch (IOException e) { plugin.getLogger().warning("Khong luu duoc arenas.yml: " + e.getMessage()); }
  }
}
