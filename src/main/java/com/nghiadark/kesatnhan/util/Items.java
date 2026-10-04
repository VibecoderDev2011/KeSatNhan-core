package com.nghiadark.kesatnhan.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class Items {
  private Items() {}

  public static ItemStack murderSword() {
    ItemStack it = new ItemStack(Material.WOODEN_SWORD);
    ItemMeta m = it.getItemMeta();
    m.setDisplayName(ChatColor.RED + "Dao Gỗ Của Sát Nhân");
    m.setLore(List.of(ChatColor.GRAY + "Chém 2 hit để hạ mục tiêu"));
    m.setUnbreakable(true);
    it.setItemMeta(m);
    return it;
  }

  public static boolean isMurderSword(ItemStack it) {
    if (it == null || it.getType() != Material.WOODEN_SWORD || !it.hasItemMeta()) return false;
    return it.getItemMeta().getDisplayName().contains("Sát Nhân");
  }

  public static ItemStack sheriffBow() {
    ItemStack it = new ItemStack(Material.BOW);
    ItemMeta m = it.getItemMeta();
    m.setDisplayName(ChatColor.AQUA + "Cung Của Cảnh Sát");
    m.setLore(List.of(ChatColor.GRAY + "Bắn 1 phát hạ sát nhân", ChatColor.GRAY + "Bắn nhầm dân: cả 2 cùng chết!"));
    m.setUnbreakable(true);
    it.setItemMeta(m);
    return it;
  }

  public static boolean isGameBow(ItemStack it) {
    if (it == null || it.getType() != Material.BOW || !it.hasItemMeta()) return false;
    return it.getItemMeta().getDisplayName().contains("Cảnh Sát");
  }

  public static ItemStack lobbyStar() {
    ItemStack it = new ItemStack(Material.NETHER_STAR);
    ItemMeta m = it.getItemMeta();
    m.setDisplayName(ChatColor.YELLOW + "Về sảnh (click phải)");
    m.setLore(List.of(ChatColor.GRAY + "Click để về sảnh chính"));
    it.setItemMeta(m);
    return it;
  }

  public static ItemStack border() {
    ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    ItemMeta m = it.getItemMeta();
    m.setDisplayName(" ");
    it.setItemMeta(m);
    return it;
  }
}
