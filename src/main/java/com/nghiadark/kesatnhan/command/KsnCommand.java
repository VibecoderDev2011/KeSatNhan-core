package com.nghiadark.kesatnhan.command;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import com.nghiadark.kesatnhan.map.GameMap;
import com.nghiadark.kesatnhan.room.Room;
import com.nghiadark.kesatnhan.room.RoomState;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class KsnCommand implements CommandExecutor, TabCompleter {
  private final KeSatNhanPlugin plugin;
  public KsnCommand(KeSatNhanPlugin plugin) { this.plugin = plugin; }

  @Override
  public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
    if (a.length == 0) { help(s); return true; }
    String sub = a[0].toLowerCase();
    switch (sub) {
      case "help" -> help(s);
      case "helpad" -> helpad(s);
      case "join" -> {
        if (!(s instanceof Player p)) { s.sendMessage(plugin.msg().get("not-player")); return true; }
        if (a.length >= 2) {
          Room r = plugin.rooms().get(a[1]);
          if (r == null) { p.sendMessage(plugin.msg().get("room-not-found", Map.of("room", a[1]))); return true; }
          plugin.game().join(p, r);
        } else plugin.gui().open(p);
      }
      case "leave" -> {
        if (!(s instanceof Player p)) return true;
        Room r = plugin.rooms().byPlayer(p.getUniqueId());
        if (r == null) { p.sendMessage(plugin.msg().get("not-in-room")); return true; }
        boolean spectator = r.spectators().contains(p.getUniqueId());
        if (r.state() == RoomState.PLAYING && !spectator) {
          p.sendMessage(plugin.msg().get("leave-deny-ingame")); return true;
        }
        plugin.game().leave(p, r);
      }
      case "list" -> {
        s.sendMessage("§8§m-----§r §cKẻSátNhân §8§m-----");
        if (plugin.rooms().all().isEmpty()) s.sendMessage("§7Chưa có phòng nào. Admin dùng /ksn setwaiting <room>.");
        for (Room r : plugin.rooms().all())
          s.sendMessage("§e" + r.id() + " §7" + r.state() + " §f" + r.players().size() + "/10");
      }
      case "lista" -> {
        s.sendMessage("§cPhòng đang hoạt động:");
        for (Room r : plugin.rooms().all())
          if (r.state() == RoomState.STARTING || r.state() == RoomState.PLAYING)
            s.sendMessage("§e" + r.id() + " §7" + r.state() + " §f" + r.players().size() + "/10");
      }
      case "reload" -> {
        if (!s.hasPermission("kesatnhan.admin")) { s.sendMessage(plugin.msg().get("no-perm")); return true; }
        plugin.reloadConfig(); plugin.msg().reload(); plugin.rooms().load(); plugin.maps().load();
        s.sendMessage("§aĐã reload KẻSátNhân!");
      }
      case "delete", "setwaiting", "setlobby", "tp", "end", "forcestart",
           "mapcreate", "mapsetspawn", "mapclearspawns", "mapsetspec",
           "maplist", "maptp", "mapdelete" -> admin(s, sub, a);
      default -> help(s);
    }
    return true;
  }

  private void admin(CommandSender s, String sub, String[] a) {
    if (!s.hasPermission("kesatnhan.admin")) { s.sendMessage(plugin.msg().get("no-perm")); return; }
    switch (sub) {
      // ----- phong -----
      case "setwaiting" -> {
        if (!(s instanceof Player p)) { s.sendMessage(plugin.msg().get("not-player")); return; }
        if (a.length < 2) { s.sendMessage("§cDùng: /ksn setwaiting <room>"); return; }
        plugin.rooms().createOrUpdate(a[1], p.getLocation());
        s.sendMessage(plugin.msg().get("room-ok", Map.of("room", a[1].toLowerCase())));
      }
      case "delete" -> {
        if (a.length < 2) { s.sendMessage("§cDùng: /ksn delete <room>"); return; }
        plugin.rooms().delete(a[1]);
        s.sendMessage(plugin.msg().get("room-deleted", Map.of("room", a[1])));
      }
      case "setlobby" -> {
        if (!(s instanceof Player p)) return;
        plugin.rooms().lobby(p.getLocation());
        s.sendMessage("§aĐã đặt sảnh chính tại vị trí của bạn!");
      }
      case "tp" -> {
        if (!(s instanceof Player p)) return;
        if (a.length < 2) return;
        Room r = plugin.rooms().get(a[1]);
        if (r == null || r.waiting() == null) { s.sendMessage(plugin.msg().get("room-not-found", Map.of("room", a[1]))); return; }
        p.teleport(r.waiting());
        s.sendMessage("§aĐã dịch chuyển tới phòng " + r.id());
      }
      case "end" -> {
        if (a.length < 2) return;
        Room r = plugin.rooms().get(a[1]);
        if (r == null) return;
        plugin.game().end(r, plugin.msg().get("game-stopped", Map.of("room", r.id())));
      }
      case "forcestart" -> {
        if (a.length < 2) return;
        Room r = plugin.rooms().get(a[1]);
        if (r == null) return;
        plugin.game().forceStart(r);
        s.sendMessage("§aĐã force-start phòng " + r.id());
      }
      // ----- map -----
      case "mapcreate" -> {
        if (a.length < 3) { s.sendMessage("§cDùng: /ksn mapcreate <map> <world>"); return; }
        if (plugin.maps().get(a[1]) != null) { s.sendMessage(plugin.msg().get("map-exists", Map.of("map", a[1]))); return; }
        if (!plugin.maps().worldExists(a[2])) { s.sendMessage(plugin.msg().get("world-missing", Map.of("world", a[2]))); return; }
        plugin.maps().create(a[1], a[2]);
        s.sendMessage(plugin.msg().get("map-created", Map.of("map", a[1].toLowerCase(), "world", a[2])));
      }
      case "mapsetspawn" -> {
        if (!(s instanceof Player p)) return;
        if (a.length < 2) { s.sendMessage("§cDùng: /ksn mapsetspawn <map>"); return; }
        GameMap m = plugin.maps().get(a[1]);
        if (m == null) { s.sendMessage(plugin.msg().get("map-not-found", Map.of("map", a[1]))); return; }
        if (m.temp()) { s.sendMessage("§cKhông set spawn cho map tạm!"); return; }
        if (m.spawns().size() >= 10) { s.sendMessage(plugin.msg().get("spawn-full", Map.of("map", m.id()))); return; }
        m.spawns().add(p.getLocation());
        plugin.maps().save();
        s.sendMessage(plugin.msg().get("spawn-added", Map.of("n", String.valueOf(m.spawns().size()), "map", m.id())));
      }
      case "mapclearspawns" -> {
        if (a.length < 2) return;
        GameMap m = plugin.maps().get(a[1]);
        if (m == null) return;
        m.spawns().clear();
        plugin.maps().save();
        s.sendMessage(plugin.msg().get("spawns-cleared", Map.of("map", m.id())));
      }
      case "mapsetspec" -> {
        if (!(s instanceof Player p)) return;
        if (a.length < 2) { s.sendMessage("§cDùng: /ksn mapsetspec <map>"); return; }
        GameMap m = plugin.maps().get(a[1]);
        if (m == null) { s.sendMessage(plugin.msg().get("map-not-found", Map.of("map", a[1]))); return; }
        m.spec(p.getLocation());
        plugin.maps().save();
        s.sendMessage(plugin.msg().get("spec-set", Map.of("map", m.id())));
      }
      case "maplist" -> {
        s.sendMessage("§8§m-----§r §cDanh sách Map §8§m-----");
        if (plugin.maps().all().isEmpty()) s.sendMessage("§7Chưa có map nào.");
        for (GameMap m : plugin.maps().all()) {
          String flag = m.temp() ? "§c[TẠM]" : (m.occupied() ? "§c[BẬN]" : "§a[RẢNH]");
          s.sendMessage("§e" + m.id() + " §7world=" + m.world() + " spawn=" + m.spawns().size() + "/10 " + flag);
        }
      }
      case "maptp" -> {
        if (!(s instanceof Player p)) return;
        if (a.length < 2) return;
        GameMap m = plugin.maps().get(a[1]);
        if (m == null) { s.sendMessage(plugin.msg().get("map-not-found", Map.of("map", a[1]))); return; }
        World w = plugin.maps().ensureLoaded(m.world());
        if (w == null) { s.sendMessage(plugin.msg().get("world-missing", Map.of("world", m.world()))); return; }
        p.teleport(m.spawns().isEmpty() ? w.getSpawnLocation() : m.spawns().get(0));
        s.sendMessage("§aĐã dịch chuyển tới map " + m.id());
      }
      case "mapdelete" -> {
        if (a.length < 2) return;
        GameMap m = plugin.maps().get(a[1]);
        if (m == null) return;
        if (m.occupied() || m.temp()) { s.sendMessage("§cMap đang dùng, không thể xóa!"); return; }
        plugin.maps().delete(a[1]);
        s.sendMessage(plugin.msg().get("map-deleted", Map.of("map", a[1])));
      }
    }
  }

  private void help(CommandSender s) {
    s.sendMessage("§8§m-----§r §cKẻSátNhân Help §8§m-----");
    s.sendMessage("§e/ksn join §7- mở GUI chọn phòng");
    s.sendMessage("§e/ksn join <room> §7- vào thẳng phòng chờ");
    s.sendMessage("§e/ksn leave §7- rời phòng (khi chờ / khán giả)");
    s.sendMessage("§e/ksn list §7- xem tất cả phòng");
    s.sendMessage("§e/ksn lista §7- xem phòng đang hoạt động");
  }

  private void helpad(CommandSender s) {
    if (!s.hasPermission("kesatnhan.admin")) { s.sendMessage(plugin.msg().get("no-perm")); return; }
    s.sendMessage("§8§m-----§r §cKẻSátNhân Admin §8§m-----");
    s.sendMessage("§e/ksn setwaiting <room>");
    s.sendMessage("§7→ Tạo phòng mới (hoặc đổi sảnh) tại vị trí đang đứng.");
    s.sendMessage("§e/ksn delete <room>");
    s.sendMessage("§7→ Xóa phòng (phải end trận trước).");
    s.sendMessage("§e/ksn tp <room>");
    s.sendMessage("§7→ Dịch chuyển tới sảnh chờ của phòng.");
    s.sendMessage("§e/ksn end <room>");
    s.sendMessage("§7→ Dừng trận, đưa mọi người về lobby.");
    s.sendMessage("§e/ksn forcestart <room>");
    s.sendMessage("§7→ Ép bắt đầu ngay, không cần đủ 8 người.");
    s.sendMessage("§e/ksn mapcreate <map> <world>");
    s.sendMessage("§7→ Đăng ký map mới từ world có sẵn trong server.");
    s.sendMessage("§e/ksn mapsetspawn <map>");
    s.sendMessage("§7→ Thêm 1 điểm spawn tại vị trí đứng (tối đa 10).");
    s.sendMessage("§e/ksn mapclearspawns <map>");
    s.sendMessage("§7→ Xóa hết điểm spawn để chấm lại.");
    s.sendMessage("§e/ksn mapsetspec <map>");
    s.sendMessage("§7→ Đặt điểm khán giả của map.");
    s.sendMessage("§e/ksn maptp <map>");
    s.sendMessage("§7→ Tới map để kiểm tra.");
    s.sendMessage("§e/ksn maplist");
    s.sendMessage("§7→ Xem tất cả map: số spawn, rảnh hay bận.");
    s.sendMessage("§e/ksn mapdelete <map>");
    s.sendMessage("§7→ Xóa map mẫu.");
    s.sendMessage("§e/ksn reload");
    s.sendMessage("§7→ Nạp lại config, không cần restart server.");
  }

  @Override
  public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
    List<String> base = new ArrayList<>(List.of("help", "join", "leave", "list", "lista"));
    if (s.hasPermission("kesatnhan.admin"))
      base.addAll(List.of("helpad", "delete", "setwaiting", "setlobby", "tp", "end", "forcestart",
          "reload", "mapcreate", "mapsetspawn", "mapclearspawns", "mapsetspec", "maplist", "maptp", "mapdelete"));
    if (a.length == 1) return filter(base, a[0]);
    if (a.length == 2) {
      String sub = a[0].toLowerCase();
      if (List.of("join", "delete", "setwaiting", "tp", "end", "forcestart").contains(sub)) {
        List<String> ids = new ArrayList<>();
        plugin.rooms().all().forEach(r -> ids.add(r.id()));
        return filter(ids, a[1]);
      }
      if (List.of("mapsetspawn", "mapclearspawns", "mapsetspec", "maptp", "mapdelete").contains(sub)) {
        List<String> ids = new ArrayList<>();
        plugin.maps().all().forEach(m -> ids.add(m.id()));
        return filter(ids, a[1]);
      }
    }
    return List.of();
  }

  private List<String> filter(List<String> in, String pre) {
    List<String> out = new ArrayList<>();
    for (String s : in) if (s.toLowerCase().startsWith(pre.toLowerCase())) out.add(s);
    return out;
  }
}
