package com.nghiadark.kesatnhan.map;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/** Map cua 1 tran dang chay: tham chieu world da load + spawn/spec da remap sang world do. */
public class MatchMap {
  private final String id;
  private final World world;
  private final List<Location> spawns;
  private final Location spec;
  private final boolean temp;
  private final GameMap template;

  public MatchMap(String id, World world, List<Location> spawns, Location spec, boolean temp, GameMap template) {
    this.id = id;
    this.world = world;
    this.spawns = spawns;
    this.spec = spec;
    this.temp = temp;
    this.template = template;
  }

  public String id() { return id; }
  public World world() { return world; }
  public List<Location> spawns() { return spawns; }
  public Location spec() { return spec; }
  public boolean temp() { return temp; }
  public GameMap template() { return template; }
}
