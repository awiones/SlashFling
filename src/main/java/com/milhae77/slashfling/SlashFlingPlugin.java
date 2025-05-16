
package com.milhae77.slashfling;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SlashFlingPlugin extends JavaPlugin implements Listener, TabCompleter {
    
    // Store cooldown times for players
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private int cooldownSeconds = 30; // Default cooldown time in seconds
    private double flingStrength = 2.5; // Default fling strength
    private int resistanceDuration = 100; // Default duration in ticks (5 seconds)
    
    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Load configuration values
        loadConfig();
        
        // Register events and commands
        Bukkit.getPluginManager().registerEvents(this, this);
        PluginCommand flingCommand = getCommand("fling");
        if (flingCommand != null) {
            flingCommand.setExecutor(this);
            flingCommand.setTabCompleter(this);
        }
        
        getLogger().info("SlashFling has been enabled!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("SlashFling has been disabled!");
    }
    
    /**
     * Loads configuration values from config.yml
     */
    private void loadConfig() {
        reloadConfig();
        cooldownSeconds = getConfig().getInt("cooldown-seconds", 30);
        flingStrength = getConfig().getDouble("fling-strength", 2.5);
        resistanceDuration = getConfig().getInt("resistance-duration-ticks", 100);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("fling")) {
            // Check if sender has permission
            if (!sender.hasPermission("slashfling.use")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }
            
            // Handle reload subcommand
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("slashfling.admin")) {
                    loadConfig();
                    sender.sendMessage(ChatColor.GREEN + "SlashFling configuration reloaded!");
                } else {
                    sender.sendMessage(ChatColor.RED + "You don't have permission to reload the plugin.");
                }
                return true;
            }
            
            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /fling <player>");
                return true;
            }
            
            // Check if the target player is online
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            
            // Check if sender is a player and on cooldown
            if (sender instanceof Player) {
                Player player = (Player) sender;
                UUID playerUUID = player.getUniqueId();
                
                // Skip cooldown check for players with admin permission
                if (!player.hasPermission("slashfling.nocooldown")) {
                    // Check if player is on cooldown
                    if (cooldowns.containsKey(playerUUID)) {
                        long secondsLeft = ((cooldowns.get(playerUUID) / 1000) + cooldownSeconds) - (System.currentTimeMillis() / 1000);
                        if (secondsLeft > 0) {
                            sender.sendMessage(ChatColor.RED + "You must wait " + secondsLeft + " seconds before using this command again.");
                            return true;
                        }
                    }
                    
                    // Set cooldown
                    cooldowns.put(playerUUID, System.currentTimeMillis());
                }
            }
            
            // Check if target has immunity permission
            if (target.hasPermission("slashfling.immune") && !sender.hasPermission("slashfling.admin")) {
                sender.sendMessage(ChatColor.RED + "You cannot fling this player.");
                return true;
            }
            
            // Fling the player upwards with configured strength
            target.setVelocity(target.getVelocity().setY(flingStrength));
            
            // Give resistance to fall damage for the configured duration
            target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, resistanceDuration, 4));
            
            // Send messages
            sender.sendMessage(ChatColor.GREEN + "Flung " + target.getName() + " into the sky!");
            target.sendMessage(ChatColor.YELLOW + "You have been flung into the sky! Enjoy the flight!");
            
            return true;
        }
        return false;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL && player.hasPotionEffect(PotionEffectType.RESISTANCE)) {
                // Only cancel the event if this was likely caused by our fling command
                // Check for resistance level 4 which is what we apply
                PotionEffect effect = player.getPotionEffect(PotionEffectType.RESISTANCE);
                if (effect != null && effect.getAmplifier() == 4) {
                    event.setCancelled(true);
                    player.removePotionEffect(PotionEffectType.RESISTANCE);
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("fling")) {
            if (args.length == 1) {
                if ("reload".startsWith(args[0].toLowerCase()) && sender.hasPermission("slashfling.admin")) {
                    return Collections.singletonList("reload");
                }
                
                List<String> completions = new ArrayList<>();
                String partial = args[0].toLowerCase();
                
                // Only suggest players the sender has permission to fling
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(partial)) {
                        // Don't suggest immune players unless sender is admin
                        if (!player.hasPermission("slashfling.immune") || sender.hasPermission("slashfling.admin")) {
                            completions.add(player.getName());
                        }
                    }
                }
                
                Collections.sort(completions);
                return completions;
            }
        }
        return Collections.emptyList();
    }
}
