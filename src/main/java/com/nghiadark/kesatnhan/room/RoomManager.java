package com.nghiadark.kesatnhan.room;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RoomManager {
  private final KeSatNhanPlugin plugin;
  private final Map<String, Room> rooms = new LinkedHashMap<>();
  private Location lobby;

  public RoomManager(KeSatNhanPlugin plugin) {
    this.plugin = plugin;
    load();
  }

  public Collection<Room> all() { return rooms.values(); }
  public Room get(String id) { return rooms.get(id.toLowerCase()); }
  public Location lobby() { return lobby; }
  public void lobby(Location l) { this.lobby = l; save(); }

  /** setwaiting vua tao vua dat sanh: chua co thi tao moi. */
  public Room createOrUpdate(String id, Location waiting) {
    Room r = rooms.computeIfAbsent(id.toLowerCase(), Room::new);
    r.waiting(waiting);
    save();
    return r;
  }

  public void delete(String id) {
    rooms.remove(id.toLowerCase());
    save();
  }

  public Room byPlayer(UUID uuid) {
    for (Room r : rooms.values())
      if (r.players().contains(uuid)) return r;
    return null;
  }

  public Room bestWaiting(int max) {
    return rooms.values().stream()
        .filter(r -> (r.state() == RoomState.WAITING || r.state() == RoomState.STARTING) && r.players().size() < max)
        .max(Comparator.comparingInt(r -> r.players().size()))
        .orElse(null);
  }

  private File file() { return new File(plugin.getDataFolder(), "rooms.yml"); }

  public void load() {
    rooms.clear();
    File f = file();
    // migrate 1 lan tu arenas.yml cu: moi arena -> 1 room + nho MapManager doi map
    File old = new File(plugin.getDataFolder(), "arenas.yml");
    if (!f.exists() && old.exists()) migrate(old);
    if (!f.exists()) return;
    YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
    lobby = c.getLocation("lobby");
    if (c.getConfigurationSection("rooms") == null) return;
    for (String id : c.getConfigurationSection("rooms").getKeys(false)) {
      Room r = new Room(id);
      r.waiting(c.getLocation("rooms." + id + ".waiting"));
      rooms.put(id, r);
    }
  }

  private void migrate(File old) {
    try {
      YamlConfiguration c = YamlConfiguration.loadConfiguration(old);
      lobby = c.getLocation("lobby");
      if (c.getConfigurationSection("arenas") != null) {
        for (String id : c.getConfigurationSection("arenas").getKeys(false)) {
          Room r = new Room(id);
          r.waiting(c.getLocation("arenas." + id + ".waiting"));
          rooms.put(id, r);
          // nho MapManager tao map mau tu spec cu (goi sau khi MapManager load)
          plugin.getServer().getScheduler().runTask(plugin, () ->
              plugin.maps().migrateFrom(c.getLocation("arenas." + id + ".spec"), id));
        }
      }
      save();
      if (!old.renameTo(new File(plugin.getDataFolder(), "arenas.yml.bak")))
        plugin.getLogger().warning("Khong doi ten duoc arenas.yml cu!");
      plugin.getLogger().info("Da migrate " + rooms.size() + " arena cu -> room + map.");
    } catch (Exception e) {
      plugin.getLogger().warning("Migrate arenas.yml that bai: " + e.getMessage());
    }
  }

  public void save() {
    YamlConfiguration c = new YamlConfiguration();
    c.set("lobby", lobby);
    for (Room r : rooms.values())
      c.set("rooms." + r.id() + ".waiting", r.waiting());
    try { c.save(file()); } catch (IOException e) { plugin.getLogger().warning("Khong luu duoc rooms.yml: " + e.getMessage()); }
  }
}
