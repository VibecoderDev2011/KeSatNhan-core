package com.nghiadark.kesatnhan.listener;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import com.nghiadark.kesatnhan.game.Role;
import com.nghiadark.kesatnhan.room.Room;
import com.nghiadark.kesatnhan.room.RoomState;
import com.nghiadark.kesatnhan.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class GameListener implements Listener {
  private final KeSatNhanPlugin plugin;
  public GameListener(KeSatNhanPlugin plugin) { this.plugin = plugin; }

  private Room roomOf(UUID id) { return plugin.rooms().byPlayer(id); }

  // ---- SÁT NHÂN 2 HIT ----
  @EventHandler
  public void onMelee(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player victim)) return;
    Player atk = null;
    if (e.getDamager() instanceof Player p) atk = p;
    if (atk == null) return;
    Room r = roomOf(atk.getUniqueId());
    if (r == null || r.state() != RoomState.PLAYING) {
      if (r != null) e.setCancelled(true);
      return;
    }
    if (!r.players().contains(victim.getUniqueId())) return;
    if (r.spectators().contains(victim.getUniqueId()) || r.spectators().contains(atk.getUniqueId())) { e.setCancelled(true); return; }

    Role ra = r.roles().get(atk.getUniqueId());
    Role rv = r.roles().get(victim.getUniqueId());

    if (ra != Role.MURDERER && rv != Role.MURDERER) { e.setCancelled(true); return; }
    if (ra == Role.MURDERER && Items.isMurderSword(atk.getInventory().getItemInMainHand())) {
      e.setCancelled(true);
      long now = System.currentTimeMillis();
      long resetMs = plugin.getConfig().getInt("hit-reset-seconds", 10) * 1000L;
      Long last = r.hitTime().get(victim.getUniqueId());
      int hits = (last != null && now - last < resetMs) ? r.hits().getOrDefault(victim.getUniqueId(), 0) : 0;
      hits++;
      if (hits >= 2) {
        r.hits().remove(victim.getUniqueId()); r.hitTime().remove(victim.getUniqueId());
        kill(r, victim, atk);
      } else {
        r.hits().put(victim.getUniqueId(), hits); r.hitTime().put(victim.getUniqueId(), now);
        victim.sendMessage(plugin.msg().get("murderer-first-hit"));
        victim.damage(0.1, atk);
      }
      return;
    }
    e.setCancelled(true);
  }

  // ---- CẢNH SÁT 1 SHOT ----
  @EventHandler
  public void onArrow(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Arrow arrow)) return;
    if (!(arrow.getShooter() instanceof Player shooter)) return;
    if (!(e.getEntity() instanceof Player victim)) return;
    Room r = roomOf(shooter.getUniqueId());
    if (r == null || r.state() != RoomState.PLAYING) return;
    if (!r.players().contains(victim.getUniqueId())) return;
    Role rs = r.roles().get(shooter.getUniqueId());
    Role rv = r.roles().get(victim.getUniqueId());
    if (rs != Role.SHERIFF && rs != Role.HERO) { e.setCancelled(true); return; }
    e.setCancelled(true);
    if (rv == Role.MURDERER) {
      kill(r, victim, shooter);
    } else {
      killSilent(r, shooter);
      killSilent(r, victim);
      dropBow(r, shooter.getLocation());
      plugin.game().broadcast(r, plugin.msg().get("sheriff-miss"));
      plugin.game().checkWin(r);
    }
  }

  @EventHandler
  public void onShoot(EntityShootBowEvent e) {
    if (!(e.getEntity() instanceof Player p)) return;
    Room r = roomOf(p.getUniqueId());
    if (r == null || r.state() != RoomState.PLAYING) return;
    Role role = r.roles().get(p.getUniqueId());
    if (role != Role.SHERIFF && role != Role.HERO) { e.setCancelled(true); return; }
    Bukkit.getScheduler().runTaskLater(plugin, () -> p.getInventory().addItem(new ItemStack(Material.ARROW, 1)), 5L);
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent e) {
    Player v = e.getEntity();
    Room r = roomOf(v.getUniqueId());
    if (r == null || r.state() != RoomState.PLAYING) return;
    e.setCancelled(true);
    Role role = r.roles().get(v.getUniqueId());
    if (role == Role.SHERIFF) {
      dropBow(r, v.getLocation());
      plugin.game().broadcast(r, plugin.msg().get("sheriff-fallen"));
    }
    plugin.game().toSpectator(r, v);
    plugin.game().checkWin(r);
  }

  @EventHandler
  public void onPickup(EntityPickupItemEvent e) {
    if (!(e.getEntity() instanceof Player p)) return;
    Room r = roomOf(p.getUniqueId());
    if (r == null || r.state() != RoomState.PLAYING) return;
    if (r.spectators().contains(p.getUniqueId())) { e.setCancelled(true); return; }
    if (Items.isGameBow(e.getItem().getItemStack()) && r.roles().get(p.getUniqueId()) == Role.INNOCENT) {
      r.roles().put(p.getUniqueId(), Role.HERO);
      p.getInventory().addItem(new ItemStack(Material.ARROW, 1));
      plugin.game().broadcast(r, plugin.msg().get("hero-born", Map.of("player", p.getName())));
    } else if (Items.isGameBow(e.getItem().getItemStack())) {
      if (r.roles().get(p.getUniqueId()) == Role.MURDERER) e.setCancelled(true);
    }
  }

  @EventHandler
  public void onDrop(PlayerDropItemEvent e) {
    Room r = roomOf(e.getPlayer().getUniqueId());
    if (r == null) return;
    if (r.state() == RoomState.WAITING || r.state() == RoomState.STARTING) e.setCancelled(true);
    else if (r.state() == RoomState.PLAYING && Items.isMurderSword(e.getItemDrop().getItemStack())) e.setCancelled(true);
  }

  @EventHandler
  public void onStar(PlayerInteractEvent e) {
    ItemStack it = e.getItem();
    if (it == null || it.getType() != Material.NETHER_STAR) return;
    if (!it.hasItemMeta() || !it.getItemMeta().getDisplayName().contains("Về sảnh")) return;
    if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    Room r = roomOf(e.getPlayer().getUniqueId());
    if (r == null) return;
    e.setCancelled(true);
    plugin.game().leave(e.getPlayer(), r);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    Room r = roomOf(e.getPlayer().getUniqueId());
    if (r == null) return;
    Role role = r.roles().get(e.getPlayer().getUniqueId());
    r.players().remove(e.getPlayer().getUniqueId());
    r.spectators().remove(e.getPlayer().getUniqueId());
    if (r.state() == RoomState.PLAYING) {
      if (role == Role.SHERIFF) { dropBow(r, e.getPlayer().getLocation()); plugin.game().broadcast(r, plugin.msg().get("sheriff-fallen")); }
      plugin.game().checkWin(r);
    } else if (r.players().size() < plugin.game().min()) plugin.game().cancelCountdown(r, true);
  }

  @EventHandler
  public void onHunger(FoodLevelChangeEvent e) {
    if (!(e.getEntity() instanceof Player p)) return;
    Room r = roomOf(p.getUniqueId());
    if (r != null && r.state() != RoomState.PLAYING) e.setCancelled(true);
  }

  private void kill(Room r, Player victim, Player killer) {
    Role role = r.roles().get(victim.getUniqueId());
    if (role == Role.SHERIFF) {
      dropBow(r, victim.getLocation());
      plugin.game().broadcast(r, plugin.msg().get("sheriff-fallen"));
    }
    plugin.game().toSpectator(r, victim);
    if (killer != null) victim.sendMessage("§cBạn bị hạ bởi " + killer.getName());
    plugin.game().checkWin(r);
  }

  private void killSilent(Room r, Player victim) {
    plugin.game().toSpectator(r, victim);
  }

  private void dropBow(Room r, org.bukkit.Location loc) {
    if (loc.getWorld() == null) return;
    loc.getWorld().dropItemNaturally(loc, Items.sheriffBow());
  }
}
