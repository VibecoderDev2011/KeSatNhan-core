package com.nghiadark.kesatnhan.map;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Map mau (template) hoac map tam (clone). Map tam co temp=true va bi xoa sau tran.
 */
public class GameMap {
  private final String id;
  private String world;
  private final List<Location> spawns = new ArrayList<>();
  private Location spec;
  private boolean occupied;
  private boolean temp;
  private String templateId;

  public GameMap(String id) { this.id = id; }

  public String id() { return id; }
  public String world() { return world; }
  public void world(String w) { this.world = w; }
  public List<Location> spawns() { return spawns; }
  public Location spec() { return spec; }
  public void spec(Location l) { this.spec = l; }
  public boolean occupied() { return occupied; }
  public void occupied(boolean o) { this.occupied = o; }
  public boolean temp() { return temp; }
  public void temp(boolean t) { this.temp = t; }
  public String templateId() { return templateId; }
  public void templateId(String t) { this.templateId = t; }
}
