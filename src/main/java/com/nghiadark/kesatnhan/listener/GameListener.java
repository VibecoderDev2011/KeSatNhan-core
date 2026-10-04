package com.nghiadark.kesatnhan.listener;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import com.nghiadark.kesatnhan.arena.Arena;
import com.nghiadark.kesatnhan.arena.ArenaState;
import com.nghiadark.kesatnhan.game.Role;
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

  private Arena arenaOf(UUID id) { return plugin.arenas().byPlayer(id); }

  // ---- SAT NHAN 2 HIT: kiem go chem 2 lan moi chet ----
  @EventHandler
  public void onMelee(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player victim)) return;
    Player atk = null;
    if (e.getDamager() instanceof Player p) atk = p;
    if (atk == null) return;
    Arena a = arenaOf(atk.getUniqueId());
    if (a == null || a.state() != ArenaState.PLAYING) {
      // chan pvp o sảnh cho
      if (a != null) e.setCancelled(true);
      return;
    }
    if (!a.players().contains(victim.getUniqueId())) return;
    if (a.spectators().contains(victim.getUniqueId()) || a.spectators().contains(atk.getUniqueId())) { e.setCancelled(true); return; }

    Role ra = a.roles().get(atk.getUniqueId());
    Role rv = a.roles().get(victim.getUniqueId());

    // dan thuong khong danh duoc nhau
    if (ra != Role.MURDERER && rv != Role.MURDERER) { e.setCancelled(true); return; }
    // chi sat nhan moi chem bang kiem go
    if (ra == Role.MURDERER && Items.isMurderSword(atk.getInventory().getItemInMainHand())) {
      e.setCancelled(true); // tu xu ly 2-hit
      long now = System.currentTimeMillis();
      long resetMs = plugin.getConfig().getInt("hit-reset-seconds", 10) * 1000L;
      Long last = a.hitTime().get(victim.getUniqueId());
      int hits = (last != null && now - last < resetMs) ? a.hits().getOrDefault(victim.getUniqueId(), 0) : 0;
      hits++;
      if (hits >= 2) {
        a.hits().remove(victim.getUniqueId()); a.hitTime().remove(victim.getUniqueId());
        kill(a, victim, atk);
      } else {
        a.hits().put(victim.getUniqueId(), hits); a.hitTime().put(victim.getUniqueId(), now);
        victim.sendMessage(plugin.msg().get("murderer-first-hit"));
        victim.damage(0.1, atk);
      }
      return;
    }
    // sheriff/hero khong duoc chem (chi ban cung) + innocent tay khong
    e.setCancelled(true);
  }

  // ---- CANH SAT 1 SHOT: ten ban 1 phat chet ----
  @EventHandler
  public void onArrow(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Arrow arrow)) return;
    if (!(arrow.getShooter() instanceof Player shooter)) return;
    if (!(e.getEntity() instanceof Player victim)) return;
    Arena a = arenaOf(shooter.getUniqueId());
    if (a == null || a.state() != ArenaState.PLAYING) return;
    if (!a.players().contains(victim.getUniqueId())) return;
    Role rs = a.roles().get(shooter.getUniqueId());
    Role rv = a.roles().get(victim.getUniqueId());
    if (rs != Role.SHERIFF && rs != Role.HERO) { e.setCancelled(true); return; }
    e.setCancelled(true);
    if (rv == Role.MURDERER) {
      kill(a, victim, shooter); // ha sat nhan -> dan thang (checkWin tu xu ly)
    } else {
      // BAN NHAM DAN: ca 2 cung chet + roi cung
      killSilent(a, shooter);
      killSilent(a, victim);
      dropBow(a, shooter.getLocation());
      plugin.game().broadcast(a, plugin.msg().get("sheriff-miss"));
      plugin.game().checkWin(a);
    }
  }

  @EventHandler
  public void onShoot(EntityShootBowEvent e) {
    if (!(e.getEntity() instanceof Player p)) return;
    Arena a = arenaOf(p.getUniqueId());
    if (a == null || a.state() != ArenaState.PLAYING) return;
    Role r = a.roles().get(p.getUniqueId());
    if (r != Role.SHERIFF && r != Role.HERO) { e.setCancelled(true); return; }
    // ten vo han: tra lai 1 mui sau khi ban
    Bukkit.getScheduler().runTaskLater(plugin, () -> p.getInventory().addItem(new ItemStack(Material.ARROW, 1)), 5L);
  }

  // ---- chet tu nhien (roi, lava...) trong tran -> thanh khan gia ----
  @EventHandler
  public void onDeath(PlayerDeathEvent e) {
    Player v = e.getEntity();
    Arena a = arenaOf(v.getUniqueId());
    if (a == null || a.state() != ArenaState.PLAYING) return;
    e.setCancelled(true);
    Role r = a.roles().get(v.getUniqueId());
    // canh sat chet -> roi cung cho dan nhat
    if (r == Role.SHERIFF) {
      dropBow(a, v.getLocation());
      plugin.game().broadcast(a, plugin.msg().get("sheriff-fallen"));
    }
    plugin.game().toSpectator(a, v);
    plugin.game().checkWin(a);
  }

  // ---- dan nhat cung -> thanh HERO ----
  @EventHandler
  public void onPickup(EntityPickupItemEvent e) {
    if (!(e.getEntity() instanceof Player p)) return;
    Arena a = arenaOf(p.getUniqueId());
    if (a == null || a.state() != ArenaState.PLAYING) return;
    if (a.spectators().contains(p.getUniqueId())) { e.setCancelled(true); return; }
    if (Items.isGameBow(e.getItem().getItemStack()) && a.roles().get(p.getUniqueId()) == Role.INNOCENT) {
      a.roles().put(p.getUniqueId(), Role.HERO);
      p.getInventory().addItem(new ItemStack(Material.ARROW, 1));
      plugin.game().broadcast(a, plugin.msg().get("hero-born", Map.of("player", p.getName())));
    } else if (Items.isGameBow(e.getItem().getItemStack())) {
      // sat nhan khong duoc nhat cung
      if (a.roles().get(p.getUniqueId()) == Role.MURDERER) e.setCancelled(true);
    }
  }

  @EventHandler
  public void onDrop(PlayerDropItemEvent e) {
    Arena a = arenaOf(e.getPlayer().getUniqueId());
    if (a == null) return;
    if (a.state() == ArenaState.WAITING || a.state() == ArenaState.STARTING) e.setCancelled(true);
    else if (a.state() == ArenaState.PLAYING && Items.isMurderSword(e.getItemDrop().getItemStack())) e.setCancelled(true);
  }

  // ---- khan gia dung sao Nether de ve sanh ----
  @EventHandler
  public void onStar(PlayerInteractEvent e) {
    ItemStack it = e.getItem();
    if (it == null || it.getType() != Material.NETHER_STAR) return;
    if (!it.hasItemMeta() || !it.getItemMeta().getDisplayName().contains("Về sảnh")) return;
    if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    Arena a = arenaOf(e.getPlayer().getUniqueId());
    if (a == null) return;
    e.setCancelled(true);
    plugin.game().leave(e.getPlayer(), a);
  }

  // ---- thoat game giua tran ----
  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    Arena a = arenaOf(e.getPlayer().getUniqueId());
    if (a == null) return;
    Role r = a.roles().get(e.getPlayer().getUniqueId());
    a.players().remove(e.getPlayer().getUniqueId());
    a.spectators().remove(e.getPlayer().getUniqueId());
    if (a.state() == ArenaState.PLAYING) {
      if (r == Role.SHERIFF) { dropBow(a, e.getPlayer().getLocation()); plugin.game().broadcast(a, plugin.msg().get("sheriff-fallen")); }
      plugin.game().checkWin(a);
    } else if (a.players().size() < plugin.game().min()) plugin.game().cancelCountdown(a, true);
  }

  @EventHandler
  public void onHunger(FoodLevelChangeEvent e) {
    if (!(e.getEntity() instanceof Player p)) return;
    Arena a = arenaOf(p.getUniqueId());
    if (a != null && a.state() != ArenaState.PLAYING) e.setCancelled(true);
  }

  private void kill(Arena a, Player victim, Player killer) {
    Role r = a.roles().get(victim.getUniqueId());
    if (r == Role.SHERIFF) {
      dropBow(a, victim.getLocation());
      plugin.game().broadcast(a, plugin.msg().get("sheriff-fallen"));
    }
    plugin.game().toSpectator(a, victim);
    if (killer != null) victim.sendMessage("§cBạn bị hạ bởi " + killer.getName());
    plugin.game().checkWin(a);
  }

  private void killSilent(Arena a, Player victim) {
    plugin.game().toSpectator(a, victim);
  }

  private void dropBow(Arena a, org.bukkit.Location loc) {
    if (loc.getWorld() == null) return;
    loc.getWorld().dropItemNaturally(loc, Items.sheriffBow());
  }
}
