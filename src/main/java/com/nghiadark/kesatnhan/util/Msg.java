package com.nghiadark.kesatnhan.util;

import com.nghiadark.kesatnhan.KeSatNhanPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

public class Msg {
  private final KeSatNhanPlugin plugin;
  private YamlConfiguration cfg;

  public Msg(KeSatNhanPlugin plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    File f = new File(plugin.getDataFolder(), "messages.yml");
    if (!f.exists()) plugin.saveResource("messages.yml", false);
    cfg = YamlConfiguration.loadConfiguration(f);
  }

  public String get(String key, Map<String, String> ph) {
    String s = cfg.getString(key, key);
    if (ph != null) for (var e : ph.entrySet()) s = s.replace("{" + e.getKey() + "}", e.getValue());
    return color(cfg.getString("prefix", "") + s);
  }

  public String get(String key) { return get(key, null); }

  public static String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
}
