package com.nghiadark.kesatnhan.util;

import com.nghiadark.kesatnhan.arena.Arena;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

public final class SpawnUtil {
  private SpawnUtil() {}

  /** Tim n diem spawn an toan trong vung pos1-pos2, tranh dangerous-blocks, cach nhau minDist. */
  public static List<Location> scatter(Arena a, int n, Set<Material> danger, int minDist) {
    List<Location> out = new ArrayList<>();
    if (!a.hasRegion()) return out;
    World w = a.pos1().getWorld();
    double x1 = Math.min(a.pos1().getX(), a.pos2().getX()), x2 = Math.max(a.pos1().getX(), a.pos2().getX());
    double z1 = Math.min(a.pos1().getZ(), a.pos2().getZ()), z2 = Math.max(a.pos1().getZ(), a.pos2().getZ());
    if (x2 - x1 < 2 || z2 - z1 < 2) return out;
    Random r = new Random();
    int tries = 0;
    while (out.size() < n && tries++ < 600) {
      int x = (int) (x1 + 1 + r.nextDouble() * Math.max(1, (x2 - x1 - 2)));
      int z = (int) (z1 + 1 + r.nextDouble() * Math.max(1, (z2 - z1 - 2)));
      int y = w.getHighestBlockYAt(x, z);
      Location loc = new Location(w, x + 0.5, y + 1, z + 0.5);
      if (!safe(w, x, y, z, danger)) continue;
      boolean far = true;
      for (Location e : out) if (e.distanceSquared(loc) < minDist * minDist) { far = false; break; }
      if (far) out.add(loc);
    }
    // fallback: cho phep gan nhau neu map hep
    tries = 0;
    while (out.size() < n && tries++ < 200) {
      int x = (int) (x1 + 1 + r.nextDouble() * Math.max(1, (x2 - x1 - 2)));
      int z = (int) (z1 + 1 + r.nextDouble() * Math.max(1, (z2 - z1 - 2)));
      int y = w.getHighestBlockYAt(x, z);
      if (!safe(w, x, y, z, danger)) continue;
      out.add(new Location(w, x + 0.5, y + 1, z + 0.5));
    }
    return out;
  }

  private static boolean safe(World w, int x, int y, int z, Set<Material> danger) {
    Block ground = w.getBlockAt(x, y, z);
    Block feet = w.getBlockAt(x, y + 1, z);
    Block head = w.getBlockAt(x, y + 2, z);
    // chan phai la AIR (khong bi ket), dau la AIR, dat phai solid va khong nguy hiem
    if (!feet.getType().isAir() || !head.getType().isAir()) return false;
    if (!ground.getType().isSolid()) return false;
    if (danger.contains(ground.getType()) || danger.contains(feet.getType())) return false;
    String n = ground.getType().name();
    if (n.contains("LAVA") || n.contains("FIRE") || n.contains("CACTUS") || n.contains("MAGMA")) return false;
    return true;
  }
}
