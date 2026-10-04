package com.nghiadark.kesatnhan.gui;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import com.nghiadark.kesatnhan.room.Room;
import com.nghiadark.kesatnhan.room.RoomState;
import com.nghiadark.kesatnhan.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class JoinGui implements InventoryHolder {
  public static final String TITLE = ChatColor.BLACK + "Chọn phòng KẻSátNhân";
  private final KeSatNhanPlugin plugin;
  private final Map<UUID, Integer> pages = new HashMap<>();

  public JoinGui(KeSatNhanPlugin plugin) { this.plugin = plugin; }

  @Override public Inventory getInventory() { return null; }

  public void open(Player p, int page) {
    pages.put(p.getUniqueId(), page);
    List<Room> sorted = sorted();
    int perPage = 28;
    int maxPage = Math.max(0, (sorted.size() - 1) / perPage);
    page = Math.max(0, Math.min(page, maxPage));
    pages.put(p.getUniqueId(), page);

    Inventory inv = Bukkit.createInventory(this, 54, TITLE + " - " + (page + 1));
    ItemStack border = Items.border();
    for (int i = 0; i < 9; i++) { inv.setItem(i, border); inv.setItem(45 + i, border); }
    for (int r = 0; r < 6; r++) { inv.setItem(r * 9, border); inv.setItem(r * 9 + 8, border); }

    int[] inner = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
    int from = page * perPage;
    for (int i = 0; i < perPage && from + i < sorted.size(); i++) {
      inv.setItem(inner[i], bed(sorted.get(from + i)));
    }
    inv.setItem(45, named(Material.ARROW, ChatColor.YELLOW + "Trang trước"));
    inv.setItem(48, named(Material.BARRIER, ChatColor.RED + "Đóng"));
    inv.setItem(49, quickStar());
    inv.setItem(53, named(Material.ARROW, ChatColor.YELLOW + "Trang sau"));
    p.openInventory(inv);
  }

  public void open(Player p) { open(p, pages.getOrDefault(p.getUniqueId(), 0)); }
  public int page(Player p) { return pages.getOrDefault(p.getUniqueId(), 0); }

  private List<Room> sorted() {
    List<Room> yellow = new ArrayList<>(), gray = new ArrayList<>(), red = new ArrayList<>();
    for (Room r : plugin.rooms().all()) {
      if (r.state() == RoomState.PLAYING || r.state() == RoomState.ENDING) red.add(r);
      else if (!r.players().isEmpty()) yellow.add(r);
      else gray.add(r);
    }
    yellow.sort(Comparator.comparingInt(r -> -r.players().size()));
    List<Room> out = new ArrayList<>();
    out.addAll(yellow); out.addAll(gray); out.addAll(red);
    return out;
  }

  private ItemStack bed(Room r) {
    Material m;
    String status;
    int max = plugin.game().max();
    if (r.state() == RoomState.PLAYING || r.state() == RoomState.ENDING) {
      m = Material.RED_BED; status = ChatColor.RED + "Đang trong trận đấu";
    } else if (!r.players().isEmpty()) {
      m = Material.YELLOW_BED; status = ChatColor.YELLOW + "Sẵn sàng";
    } else {
      m = Material.GRAY_BED; status = ChatColor.GRAY + "Chưa bắt đầu";
    }
    ItemStack it = new ItemStack(m);
    ItemMeta meta = it.getItemMeta();
    meta.setDisplayName(ChatColor.GREEN + r.id());
    meta.setLore(List.of(
        ChatColor.GRAY + "Trạng thái: " + status,
        ChatColor.GRAY + "Người chơi: " + ChatColor.WHITE + r.players().size() + "/" + max,
        ChatColor.GRAY + "Map: " + ChatColor.WHITE + "ngẫu nhiên (" + plugin.maps().all().size() + " mẫu sẵn sàng)",
        ChatColor.DARK_GRAY + "Click để vào phòng"));
    it.setItemMeta(meta);
    return it;
  }

  private ItemStack quickStar() {
    Room best = plugin.rooms().bestWaiting(plugin.game().max());
    ItemStack it = new ItemStack(Material.NETHER_STAR);
    ItemMeta meta = it.getItemMeta();
    meta.setDisplayName(ChatColor.AQUA + "Tham gia nhanh");
    if (best != null)
      meta.setLore(List.of(ChatColor.GRAY + "Phòng đề xuất: " + ChatColor.WHITE + best.id() + " (" + best.players().size() + "/10)",
          ChatColor.DARK_GRAY + "Click để vào phòng đông nhất đang chờ"));
    else
      meta.setLore(List.of(ChatColor.RED + "Không có phòng chờ nào!", ChatColor.DARK_GRAY + "Hãy đợi admin tạo phòng"));
    it.setItemMeta(meta);
    return it;
  }

  private ItemStack named(Material m, String name) {
    ItemStack it = new ItemStack(m);
    ItemMeta meta = it.getItemMeta();
    meta.setDisplayName(name);
    it.setItemMeta(meta);
    return it;
  }
}
