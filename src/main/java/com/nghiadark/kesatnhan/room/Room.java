package com.nghiadark.kesatnhan.room;

import com.nghiadark.kesatnhan.game.Role;
import com.nghiadark.kesatnhan.map.MatchMap;
import org.bukkit.Location;

import java.util.*;

public class Room {
  private final String id;
  private Location waiting;
  private RoomState state = RoomState.WAITING;
  private final List<UUID> players = new ArrayList<>();
  private final Set<UUID> spectators = new HashSet<>();
  private final Map<UUID, Role> roles = new HashMap<>();
  private final Map<UUID, Integer> hits = new HashMap<>();
  private final Map<UUID, Long> hitTime = new HashMap<>();
  private int countdown = -1;
  private MatchMap matchMap;

  public Room(String id) { this.id = id; }

  public String id() { return id; }
  public Location waiting() { return waiting; }
  public void waiting(Location l) { this.waiting = l; }
  public RoomState state() { return state; }
  public void state(RoomState s) { this.state = s; }
  public List<UUID> players() { return players; }
  public Set<UUID> spectators() { return spectators; }
  public Map<UUID, Role> roles() { return roles; }
  public Map<UUID, Integer> hits() { return hits; }
  public Map<UUID, Long> hitTime() { return hitTime; }
  public int countdown() { return countdown; }
  public void countdown(int c) { this.countdown = c; }
  public MatchMap matchMap() { return matchMap; }
  public void matchMap(MatchMap m) { this.matchMap = m; }

  public boolean isFull(int max) { return players.size() >= max; }
}
