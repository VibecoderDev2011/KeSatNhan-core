package com.nghiadark.kesatnhan.listener;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import com.nghiadark.kesatnhan.arena.Arena;
import com.nghiadark.kesatnhan.gui.JoinGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GuiListener implements Listener {
  private final KeSatNhanPlugin plugin;
  public GuiListener(KeSatNhanPlugin plugin) { this.plugin = plugin; }

  @EventHandler
  public void onClick(InventoryClickEvent e) {
    if (!(e.getInventory().getHolder() instanceof JoinGui)) return;
    e.setCancelled(true);
    if (!(e.getWhoClicked() instanceof Player p)) return;
    if (e.getCurrentItem() == null) return;
    Material t = e.getCurrentItem().getType();
    int slot = e.getRawSlot();
    if (slot == 45) { plugin.gui().open(p, plugin.gui().page(p) - 1); return; }
    if (slot == 53) { plugin.gui().open(p, plugin.gui().page(p) + 1); return; }
    if (slot == 48) { p.closeInventory(); return; }
    if (slot == 49) { // tham gia nhanh: phong cho dong nhat
      Arena best = plugin.arenas().bestWaiting(plugin.game().max());
      if (best == null) { p.sendMessage(plugin.msg().get("no-waiting-room")); return; }
      p.closeInventory();
      if (plugin.game().join(p, best))
        p.sendMessage(plugin.msg().get("quick-join-ok", java.util.Map.of("arena", best.id())));
      return;
    }
    if (t == Material.GRAY_BED || t == Material.YELLOW_BED || t == Material.RED_BED) {
      String name = e.getCurrentItem().getItemMeta().getDisplayName().replaceAll("§.", "");
      Arena ar = plugin.arenas().get(name.toLowerCase());
      if (ar == null) return;
      p.closeInventory();
      plugin.game().join(p, ar);
    }
  }

  @EventHandler
  public void onDrag(InventoryDragEvent e) {
    if (e.getInventory().getHolder() instanceof JoinGui) e.setCancelled(true);
  }
}
