package com.nghiadark.kesatnhan.listener;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import com.nghiadark.kesatnhan.arena.Arena;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Set;

/** Vung pos1-pos2 tu dong khoa: cam pha/dat/mo/su dung/spawn quai. Admin bypass. */
public class ProtectListener implements Listener {
  private final KeSatNhanPlugin plugin;
  private static final Set<Material> USE_BLOCK = Set.of(
      Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.SHULKER_BOX,
      Material.WHITE_SHULKER_BOX, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
      Material.HOPPER, Material.DISPENSER, Material.DROPPER, Material.BREWING_STAND,
      Material.ANVIL, Material.CRAFTING_TABLE, Material.ENCHANTING_TABLE, Material.OAK_DOOR,
      Material.OAK_TRAPDOOR, Material.LEVER, Material.STONE_BUTTON, Material.OAK_BUTTON,
      Material.CHEST_MINECART, Material.HOPPER_MINECART);

  public ProtectListener(KeSatNhanPlugin plugin) { this.plugin = plugin; }

  private boolean locked(org.bukkit.Location l, Player p) {
    if (l == null) return false;
    if (p != null && p.hasPermission("kesatnhan.admin")) return false; // admin bypass de sua map
    for (Arena a : plugin.arenas().all()) if (a.inRegion(l)) return true;
    return false;
  }

  @EventHandler public void onBreak(BlockBreakEvent e) { if (locked(e.getBlock().getLocation(), e.getPlayer())) e.setCancelled(true); }
  @EventHandler public void onPlace(BlockPlaceEvent e) { if (locked(e.getBlock().getLocation(), e.getPlayer())) e.setCancelled(true); }
  @EventHandler public void onBucketE(PlayerBucketEmptyEvent e) { if (locked(e.getBlock().getLocation(), e.getPlayer())) e.setCancelled(true); }
  @EventHandler public void onBucketF(PlayerBucketFillEvent e) { if (locked(e.getBlock().getLocation(), e.getPlayer())) e.setCancelled(true); }

  @EventHandler
  public void onUse(PlayerInteractEvent e) {
    if (!e.hasBlock()) return;
    if (USE_BLOCK.contains(e.getClickedBlock().getType()) && locked(e.getClickedBlock().getLocation(), e.getPlayer()))
      e.setCancelled(true);
  }

  @EventHandler
  public void onSpawn(CreatureSpawnEvent e) {
    if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
    for (Arena a : plugin.arenas().all())
      if (a.inRegion(e.getLocation())) { e.setCancelled(true); return; }
  }

  @EventHandler
  public void onExplode(EntityExplodeEvent e) {
    e.blockList().removeIf(b -> { for (Arena a : plugin.arenas().all()) if (a.inRegion(b.getLocation())) return true; return false; });
  }

  @EventHandler
  public void onExplodeB(BlockExplodeEvent e) {
    e.blockList().removeIf(b -> { for (Arena a : plugin.arenas().all()) if (a.inRegion(b.getLocation())) return true; return false; });
  }
}
