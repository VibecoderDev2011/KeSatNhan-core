package com.nghiadark.kesatnhan.arena;

import com.nghiadark.kesatnhan.game.Role;
import org.bukkit.Location;

import java.util.*;

public class Arena {
  private final String id;
  private String world;
  private Location pos1, pos2, waiting, spec;
  private ArenaState state = ArenaState.WAITING;
  private final List<UUID> players = new ArrayList<>();
  private final Set<UUID> spectators = new HashSet<>();
  private final Map<UUID, Role> roles = new HashMap<>();
  private final Map<UUID, Integer> hits = new HashMap<>();
  private final Map<UUID, Long> hitTime = new HashMap<>();
  private int countdown = -1;
  private int countdownTask = -1;

  public Arena(String id) { this.id = id; }

  public String id() { return id; }
  public String world() { return world; }
  public void world(String w) { this.world = w; }
  public Location pos1() { return pos1; }
  public Location pos2() { return pos2; }
  public void pos1(Location l) { this.pos1 = l; if (world == null && l != null) world = l.getWorld().getName(); }
  public void pos2(Location l) { this.pos2 = l; if (world == null && l != null) world = l.getWorld().getName(); }
  public boolean hasRegion() { return pos1 != null && pos2 != null; }
  public Location waiting() { return waiting; }
  public void waiting(Location l) { this.waiting = l; }
  public Location spec() { return spec; }
  public void spec(Location l) { this.spec = l; }
  public ArenaState state() { return state; }
  public void state(ArenaState s) { this.state = s; }
  public List<UUID> players() { return players; }
  public Set<UUID> spectators() { return spectators; }
  public Map<UUID, Role> roles() { return roles; }
  public Map<UUID, Integer> hits() { return hits; }
  public Map<UUID, Long> hitTime() { return hitTime; }
  public int countdown() { return countdown; }
  public void countdown(int c) { this.countdown = c; }
  public int countdownTask() { return countdownTask; }
  public void countdownTask(int t) { this.countdownTask = t; }

  public boolean isFull(int max) { return players.size() >= max; }

  public boolean inRegion(Location l) {
    if (!hasRegion() || l == null || l.getWorld() == null) return false;
    if (!l.getWorld().getName().equals(pos1.getWorld().getName())) return false;
    double x1 = Math.min(pos1.getX(), pos2.getX()), x2 = Math.max(pos1.getX(), pos2.getX());
    double y1 = Math.min(pos1.getY(), pos2.getY()), y2 = Math.max(pos1.getY(), pos2.getY());
    double z1 = Math.min(pos1.getZ(), pos2.getZ()), z2 = Math.max(pos1.getZ(), pos2.getZ());
    return l.getX() >= x1 && l.getX() <= x2 && l.getY() >= y1 - 2 && l.getY() <= y2 + 2 && l.getZ() >= z1 && l.getZ() <= z2;
  }
}
