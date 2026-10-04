package com.nghiadark.kesatnhan;

import com.nghiadark.kesatnhan.command.KsnCommand;
import com.nghiadark.kesatnhan.game.GameManager;
import com.nghiadark.kesatnhan.gui.JoinGui;
import com.nghiadark.kesatnhan.listener.GameListener;
import com.nghiadark.kesatnhan.listener.GuiListener;
import com.nghiadark.kesatnhan.map.MapManager;
import com.nghiadark.kesatnhan.room.RoomManager;
import com.nghiadark.kesatnhan.util.Msg;
import org.bukkit.plugin.java.JavaPlugin;

public class KeSatNhanPlugin extends JavaPlugin {

  private Msg msg;
  private RoomManager rooms;
  private MapManager maps;
  private GameManager game;
  private JoinGui joinGui;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    this.msg = new Msg(this);
    this.rooms = new RoomManager(this);
    this.maps = new MapManager(this);
    this.game = new GameManager(this);
    this.joinGui = new JoinGui(this);

    KsnCommand cmd = new KsnCommand(this);
    getCommand("ksn").setExecutor(cmd);
    getCommand("ksn").setTabCompleter(cmd);

    getServer().getPluginManager().registerEvents(new GameListener(this), this);
    getServer().getPluginManager().registerEvents(new GuiListener(this), this);

    getLogger().info("KeSatNhan-core 1.0.0 by NghiaDark enabled.");
  }

  @Override
  public void onDisable() {
    if (rooms != null) rooms.save();
    if (maps != null) { maps.save(); maps.cleanupAll(); }
  }

  public Msg msg() { return msg; }
  public RoomManager rooms() { return rooms; }
  public MapManager maps() { return maps; }
  public GameManager game() { return game; }
  public JoinGui gui() { return joinGui; }
}
