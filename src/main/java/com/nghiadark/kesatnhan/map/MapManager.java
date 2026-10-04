package com.nghiadark.kesatnhan.map;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class MapManager {
  private final KeSatNhanPlugin plugin;
  private final Map<String, GameMap> maps = new LinkedHashMap<>();
  private final Set<String> tempWorlds = new HashSet<>();
  private final AtomicInteger cloneSeq = new AtomicInteger(0);

  public MapManager(KeSatNhanPlugin plugin) {
    this.plugin = plugin;
    load();
  }

  public Collection<GameMap> all() { return maps.values(); }
  public GameMap get(String id) { return maps.get(id.toLowerCase()); }

  public GameMap create(String id, String world) {
    GameMap m = new GameMap(id.toLowerCase());
    m.world(world);
    maps.put(m.id(), m);
    save();
    return m;
  }

  public void delete(String id) {
    maps.remove(id.toLowerCase());
    save();
  }

  /** Tao map mau tu spec cua arena cu (migration). */
  public void migrateFrom(Location spec, String id) {
    if (maps.containsKey(id.toLowerCase())) return;
    GameMap m = new GameMap(id.toLowerCase());
    if (spec != null) {
      m.world(spec.getWorld().getName());
      m.spec(spec);
    }
    maps.put(m.id(), m);
    save();
  }

  // ---------- world helpers ----------
  public boolean worldExists(String name) {
    if (Bukkit.getWorld(name) != null) return true;
    return new File(Bukkit.getWorldContainer(), name).isDirectory();
  }

  public World ensureLoaded(String name) {
    World w = Bukkit.getWorld(name);
    if (w != null) return w;
    if (!worldExists(name)) return null;
    return Bukkit.createWorld(new WorldCreator(name));
  }

  /** Random map mau ranh, du spawn, world ton tai. */
  public GameMap pickFree(int needSpawns) {
    List<GameMap> free = new ArrayList<>();
    for (GameMap m : maps.values())
      if (!m.occupied() && !m.temp() && m.spawns().size() >= needSpawns
          && m.world() != null && worldExists(m.world()) && tempWorlds.size() < maxTemp())
        free.add(m);
    if (free.isEmpty()) return null;
    return free.get(new Random().nextInt(free.size()));
  }

  /** Random map mau de clone (khong can ranh). */
  public GameMap pickForClone(int needSpawns) {
    List<GameMap> ok = new ArrayList<>();
    for (GameMap m : maps.values())
      if (!m.temp() && m.spawns().size() >= needSpawns && m.world() != null && worldExists(m.world()))
        ok.add(m);
    if (ok.isEmpty() || tempWorlds.size() >= maxTemp()) return null;
    return ok.get(new Random().nextInt(ok.size()));
  }

  private int maxTemp() { return plugin.getConfig().getInt("max-temp-clones", 3); }

  public MatchMap buildDirect(GameMap template) {
    World w = ensureLoaded(template.world());
    if (w == null) return null;
    template.occupied(true);
    List<Location> spawns = new ArrayList<>(template.spawns());
    Location spec = template.spec();
    return new MatchMap(template.id(), w, spawns, spec, false, template);
  }

  /** Clone world mau sang world tam, chay async copy roi ve main thread load. Callback luon o main thread. */
  public void cloneAsync(GameMap template, Consumer<MatchMap> done) {
    String cloneName = ("ksn_" + template.id() + "_" + cloneSeq.incrementAndGet()).toLowerCase().replaceAll("[^a-z0-9_]", "_");
    tempWorlds.add(cloneName);
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        World loaded = Bukkit.getWorld(template.world());
        if (loaded != null) {
          if (!loaded.getPlayers().isEmpty()) { fail(done, cloneName); return; }
          final World lw = loaded;
          // unload tren main thread
          Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.unloadWorld(lw, true);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> copyAndLoad(template, cloneName, done));
          });
        } else {
          copyAndLoad(template, cloneName, done);
        }
      } catch (Exception e) {
        plugin.getLogger().warning("Clone map " + template.id() + " that bai: " + e.getMessage());
        fail(done, cloneName);
      }
    });
  }

  private void copyAndLoad(GameMap template, String cloneName, Consumer<MatchMap> done) {
    try {
      File src = new File(Bukkit.getWorldContainer(), template.world());
      File dst = new File(Bukkit.getWorldContainer(), cloneName);
      copyWorld(src.toPath(), dst.toPath());
      copyWGRegions(template.world(), cloneName);
      // load + remap tren main thread
      Bukkit.getScheduler().runTask(plugin, () -> {
        World w = Bukkit.createWorld(new WorldCreator(cloneName));
        if (w == null) { fail(done, cloneName); return; }
        List<Location> spawns = new ArrayList<>();
        for (Location l : template.spawns())
          spawns.add(new Location(w, l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
        Location spec = template.spec() == null ? null
            : new Location(w, template.spec().getX(), template.spec().getY(), template.spec().getZ(),
                template.spec().getYaw(), template.spec().getPitch());
        GameMap temp = new GameMap(cloneName);
        temp.world(cloneName); temp.temp(true); temp.templateId(template.id());
        temp.spawns().addAll(spawns); temp.spec(spec);
        maps.put(cloneName, temp);
        done.accept(new MatchMap(cloneName, w, spawns, spec, true, template));
      });
    } catch (Exception e) {
      plugin.getLogger().warning("Copy world " + template.world() + " that bai: " + e.getMessage());
      fail(done, cloneName);
    }
  }

  private void fail(Consumer<MatchMap> done, String cloneName) {
    tempWorlds.remove(cloneName);
    Bukkit.getScheduler().runTask(plugin, () -> done.accept(null));
  }

  /** Giai phong map sau tran: clone tam thi unload + xoa, map mau thi mo khoa + don rac. */
  public void release(MatchMap m) {
    if (m == null) return;
    if (m.temp()) {
      tempWorlds.remove(m.world().getName());
      maps.remove(m.id());
      String name = m.world().getName();
      Bukkit.unloadWorld(m.world(), false);
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        deleteDir(new File(Bukkit.getWorldContainer(), name));
        deleteWGRegions(name);
      }, 20L);
    } else {
      if (m.template() != null) m.template().occupied(false);
      // don item roi/mui ten con sot trong world mau
      for (Entity e : m.world().getEntities())
        if (e instanceof Item || e.getType().name().contains("ARROW") || e.getType().name().equals("TRIDENT")) e.remove();
    }
  }

  /** Don toan bo world tam khi tat plugin. */
  public void cleanupAll() {
    for (String name : new HashSet<>(tempWorlds)) {
      World w = Bukkit.getWorld(name);
      if (w != null) Bukkit.unloadWorld(w, false);
      deleteDir(new File(Bukkit.getWorldContainer(), name));
      deleteWGRegions(name);
    }
    tempWorlds.clear();
  }

  // ---------- file ops ----------
  private void copyWorld(Path src, Path dst) throws IOException {
    Files.walkFileTree(src, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) throws IOException {
        String n = dir.getFileName().toString();
        if (n.equals("playerdata") || n.equals("stats")) return FileVisitResult.SKIP_SUBTREE;
        Files.createDirectories(dst.resolve(src.relativize(dir)));
        return FileVisitResult.CONTINUE;
      }
      @Override
      public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
        String n = f.getFileName().toString();
        if (n.equals("session.lock") || n.equals("uid.dat")) return FileVisitResult.CONTINUE;
        Files.copy(f, dst.resolve(src.relativize(f)), StandardCopyOption.REPLACE_EXISTING);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  private void deleteDir(File dir) {
    if (dir == null || !dir.exists()) return;
    File[] files = dir.listFiles();
    if (files != null) for (File f : files) {
      if (f.isDirectory()) deleteDir(f);
      else f.delete();
    }
    dir.delete();
  }

  private File wgDir(String world) {
    return new File(new File(Bukkit.getWorldContainer(), "plugins/WorldGuard/worlds"), world);
  }

  public boolean wgPresent() {
    return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
  }

  private void copyWGRegions(String from, String to) {
    File src = new File(wgDir(from), "regions.yml");
    if (!src.exists()) {
      if (wgPresent()) plugin.getLogger().warning("Map " + from + " chua co regions.yml WorldGuard!");
      return;
    }
    try {
      File d = wgDir(to);
      d.mkdirs();
      Files.copy(src.toPath(), new File(d, "regions.yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      plugin.getLogger().warning("Copy regions.yml that bai: " + e.getMessage());
    }
  }

  private void deleteWGRegions(String world) {
    deleteDir(wgDir(world));
  }

  // ---------- persistence ----------
  private File file() { return new File(plugin.getDataFolder(), "maps.yml"); }

  public void load() {
    maps.clear();
    File f = file();
    if (!f.exists()) return;
    YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
    if (c.getConfigurationSection("maps") == null) return;
    for (String id : c.getConfigurationSection("maps").getKeys(false)) {
      // bo qua map tam con sot tu lan chay truoc
      if (id.startsWith("ksn_")) continue;
      GameMap m = new GameMap(id);
      m.world(c.getString("maps." + id + ".world"));
      List<?> list = c.getList("maps." + id + ".spawns");
      if (list != null) for (Object o : list) if (o instanceof Location l) m.spawns().add(l);
      m.spec(c.getLocation("maps." + id + ".spec"));
      maps.put(id, m);
    }
  }

  public void save() {
    YamlConfiguration c = new YamlConfiguration();
    for (GameMap m : maps.values()) {
      if (m.temp()) continue;
      c.set("maps." + m.id() + ".world", m.world());
      c.set("maps." + m.id() + ".spawns", new ArrayList<>(m.spawns()));
      c.set("maps." + m.id() + ".spec", m.spec());
    }
    try { c.save(file()); } catch (IOException e) { plugin.getLogger().warning("Khong luu duoc maps.yml: " + e.getMessage()); }
  }
}
