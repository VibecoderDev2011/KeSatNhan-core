package com.nghiadark.kesatnhan.command;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import com.nghiadark.kesatnhan.arena.Arena;
import com.nghiadark.kesatnhan.arena.ArenaState;
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
      case "join" -> {
        if (!(s instanceof Player p)) { s.sendMessage(plugin.msg().get("not-player")); return true; }
        if (a.length >= 2) {
          Arena ar = plugin.arenas().get(a[1]);
          if (ar == null) { p.sendMessage(plugin.msg().get("arena-not-found", Map.of("arena", a[1]))); return true; }
          plugin.game().join(p, ar);
        } else plugin.gui().open(p);
      }
      case "leave" -> {
        if (!(s instanceof Player p)) return true;
        Arena ar = plugin.arenas().byPlayer(p.getUniqueId());
        if (ar == null) { p.sendMessage(plugin.msg().get("not-in-arena")); return true; }
        boolean spectator = ar.spectators().contains(p.getUniqueId());
        if (ar.state() == ArenaState.PLAYING && !spectator) {
          p.sendMessage(plugin.msg().get("leave-deny-ingame")); return true;
        }
        plugin.game().leave(p, ar);
      }
      case "list" -> {
        s.sendMessage("§8§m-----§r §cKẻSátNhân §8§m-----");
        for (Arena ar : plugin.arenas().all())
          s.sendMessage("§e" + ar.id() + " §7" + ar.state() + " §f" + ar.players().size() + "/10");
      }
      case "lista" -> {
        s.sendMessage("§cĐấu trường đang hoạt động:");
        for (Arena ar : plugin.arenas().all())
          if (ar.state() == ArenaState.STARTING || ar.state() == ArenaState.PLAYING)
            s.sendMessage("§e" + ar.id() + " §7" + ar.state() + " §f" + ar.players().size() + "/10");
      }
      case "reload" -> {
        if (!s.hasPermission("kesatnhan.admin")) { s.sendMessage(plugin.msg().get("no-perm")); return true; }
        plugin.reloadConfig(); plugin.msg().reload(); plugin.arenas().load();
        s.sendMessage("§aĐã reload KẻSátNhân!");
      }
      case "create", "delete", "pos1", "pos2", "setwaiting", "setspec", "setlobby",
           "tp", "end", "forcestart" -> admin(s, sub, a);
      default -> help(s);
    }
    return true;
  }

  private void admin(CommandSender s, String sub, String[] a) {
    if (!s.hasPermission("kesatnhan.admin")) { s.sendMessage(plugin.msg().get("no-perm")); return; }
    switch (sub) {
      case "create" -> {
        if (a.length < 2) { s.sendMessage("§cDùng: /ksn create <arena>"); return; }
        if (plugin.arenas().get(a[1]) != null) { s.sendMessage(plugin.msg().get("arena-exists", Map.of("arena", a[1]))); return; }
        plugin.arenas().create(a[1]);
        s.sendMessage(plugin.msg().get("arena-created", Map.of("arena", a[1].toLowerCase())));
      }
      case "delete" -> {
        if (a.length < 2) { s.sendMessage("§cDùng: /ksn delete <arena>"); return; }
        plugin.arenas().delete(a[1]);
        s.sendMessage(plugin.msg().get("arena-deleted", Map.of("arena", a[1])));
      }
      case "pos1", "pos2" -> {
        if (!(s instanceof Player p)) { s.sendMessage(plugin.msg().get("not-player")); return; }
        if (a.length < 2) { s.sendMessage("§cDùng: /ksn " + sub + " <arena>"); return; }
        Arena ar = plugin.arenas().get(a[1]);
        if (ar == null) { s.sendMessage(plugin.msg().get("arena-not-found", Map.of("arena", a[1]))); return; }
        if (sub.equals("pos1")) { ar.pos1(p.getLocation()); s.sendMessage(plugin.msg().get("pos1-set", Map.of("arena", ar.id()))); }
        else { ar.pos2(p.getLocation()); s.sendMessage(plugin.msg().get("pos2-set", Map.of("arena", ar.id()))); }
        plugin.arenas().save();
      }
      case "setwaiting", "setspec" -> {
        if (!(s instanceof Player p)) return;
        if (a.length < 2) { s.sendMessage("§cDùng: /ksn " + sub + " <arena>"); return; }
        Arena ar = plugin.arenas().get(a[1]);
        if (ar == null) { s.sendMessage(plugin.msg().get("arena-not-found", Map.of("arena", a[1]))); return; }
        if (sub.equals("setwaiting")) { ar.waiting(p.getLocation()); s.sendMessage(plugin.msg().get("waiting-set", Map.of("arena", ar.id()))); }
        else { ar.spec(p.getLocation()); s.sendMessage(plugin.msg().get("spec-set", Map.of("arena", ar.id()))); }
        plugin.arenas().save();
      }
      case "setlobby" -> {
        if (!(s instanceof Player p)) return;
        plugin.arenas().lobby(p.getLocation());
        s.sendMessage(plugin.msg().get("lobby-set"));
      }
      case "tp" -> {
        if (!(s instanceof Player p)) return;
        if (a.length < 2) return;
        Arena ar = plugin.arenas().get(a[1]);
        if (ar == null || ar.waiting() == null) { s.sendMessage(plugin.msg().get("arena-not-found", Map.of("arena", a[1]))); return; }
        p.teleport(ar.waiting());
        s.sendMessage("§aĐã dịch chuyển tới " + ar.id());
      }
      case "end" -> {
        if (a.length < 2) return;
        Arena ar = plugin.arenas().get(a[1]);
        if (ar == null) return;
        plugin.game().end(ar, plugin.msg().get("game-stopped", Map.of("arena", ar.id())));
      }
      case "forcestart" -> {
        if (a.length < 2) return;
        Arena ar = plugin.arenas().get(a[1]);
        if (ar == null) return;
        plugin.game().forceStart(ar);
        s.sendMessage("§aĐã force-start " + ar.id());
      }
    }
  }

  private void help(CommandSender s) {
    boolean admin = s.hasPermission("kesatnhan.admin");
    s.sendMessage("§8§m-----§r §cKẻSátNhân Help §8§m-----");
    s.sendMessage("§e/ksn join §7- mở GUI chọn phòng");
    s.sendMessage("§e/ksn join <arena> §7- vào thẳng phòng");
    s.sendMessage("§e/ksn leave §7- rời phòng (khi chờ / khán giả)");
    s.sendMessage("§e/ksn list §7- xem tất cả đấu trường");
    s.sendMessage("§e/ksn lista §7- xem đấu trường đang hoạt động");
    if (admin) {
      s.sendMessage("§c/ksn create <arena>, delete, pos1/pos2 <arena>");
      s.sendMessage("§c/ksn setwaiting/setspec <arena>, setlobby, tp <arena>");
      s.sendMessage("§c/ksn forcestart/end <arena>, reload");
    }
  }

  @Override
  public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
    if (a.length == 1) return filter(List.of("help","join","leave","list","lista","create","delete","pos1","pos2","setwaiting","setspec","setlobby","tp","end","forcestart","reload"), a[0]);
    if (a.length == 2 && List.of("join","delete","pos1","pos2","setwaiting","setspec","tp","end","forcestart","create").contains(a[0].toLowerCase())) {
      List<String> ids = new ArrayList<>();
      plugin.arenas().all().forEach(ar -> ids.add(ar.id()));
      return filter(ids, a[1]);
    }
    return List.of();
  }

  private List<String> filter(List<String> in, String pre) {
    List<String> out = new ArrayList<>();
    for (String s : in) if (s.toLowerCase().startsWith(pre.toLowerCase())) out.add(s);
    return out;
  }
}
