package me.simplicitee.enchantsplit;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class EnchantSplit extends JavaPlugin implements Listener {

	private static final ItemStack BOOK = new ItemStack(Material.BOOK);
	private static final String EXPERIENCE_COST = "ExperienceCost";
	
	private int expCost = 10;

	public enum PluginMsg {
		NO_PERM("NoPermission", "&cYou don't have permission to use /enchantsplit!"),
		PLAYER_ONLY("PlayerOnly", "&cOnly a player can run /enchantsplit!"),
		NOT_ENCHANTED("NotEnchantedBook", "&cOnly an enchanted book can be split!"),
		NOT_ENOUGH_ENCHANTS("NotEnoughEnchants", "&cThe enchanted book does not have enough enchants to split!"),
		NOT_ENOUGH_BOOKS("NotEnoughBooks", "&cYou do not have enough books in your inventory to split the enchanted book!"),
		NOT_ENOUGH_XP("NotEnoughXP", "&cYou do not have enough xp to split the enchanted book!"),
		SUCCESS("Success", "&aEnchanted book was successfully split!");

		private String path, msg;
		private TextComponent component;

		private PluginMsg(String name, String msg) {
			this.path = "Message." + name;
			this.setText(msg);
		}

		public void setText(String msg) {
			this.msg = ChatColor.translateAlternateColorCodes('&', msg);
			this.component = new TextComponent(this.msg);
		}
	}
	
	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		FileConfiguration config = getConfig();
		
		for (PluginMsg msg : PluginMsg.values()) {
			if (config.contains(msg.path)) {
				msg.setText(config.getString(msg.path));
			}
		}
		
		if (config.contains(EXPERIENCE_COST)) {
			this.expCost = config.getInt(EXPERIENCE_COST);
		}
		
		getServer().getPluginCommand("enchantsplit").setExecutor(this);
		getServer().getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable() {}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!label.equals("enchantsplit")) {
			return false;
		}

		if (!(sender instanceof Player)) {
			message(sender, PluginMsg.PLAYER_ONLY, false);
			return true;
		} else if (!sender.hasPermission("split.enchants.command")) {
			message(sender, PluginMsg.NOT_ENCHANTED, false);
			return true;
		}

		enchantSplit((Player) sender, false);
		return true;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEnchantingTableInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) {
			return;
		} else if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.ENCHANTED_BOOK) {
			return;
		}
		
		event.setCancelled(true);
		enchantSplit(event.getPlayer(), true);
	}

	public void enchantSplit(Player player, boolean natural) {
		ItemStack book = player.getInventory().getItemInMainHand();
		
		if (book.getType() != Material.ENCHANTED_BOOK) {
			message(player, PluginMsg.NOT_ENCHANTED, natural);
			return;
		} else if (natural && !player.hasPermission("split.enchants.natural")) {
			message(player, PluginMsg.NO_PERM, true);
			return;
		}
		
		Map<Enchantment, Integer> enchants = ((EnchantmentStorageMeta) book.getItemMeta()).getStoredEnchants();
		
		if (enchants.size() <= 1) {
			message(player, PluginMsg.NOT_ENOUGH_ENCHANTS, natural);
			return;
		} else if (!player.getInventory().containsAtLeast(BOOK, enchants.size())) {
			message(player, PluginMsg.NOT_ENOUGH_BOOKS, natural);
			return;
		}
		
		if (player.getGameMode() != GameMode.CREATIVE) {
			int exp = expCost <= 0 ? 0 : expCost * enchants.size();
			int total = getTotalExp(player);
			
			if (total < exp) {
				message(player, PluginMsg.NOT_ENOUGH_XP, natural);
				return;
			}

			setTotalExp(player, total - exp);
		}

		player.getInventory().setItemInMainHand(null);
		player.getInventory().removeItem(removals(enchants.size()));
		splitEnchants(enchants).forEach((i) -> player.getWorld().dropItemNaturally(player.getLocation(), i, (item) -> item.setPickupDelay(0)));
		message(player, PluginMsg.SUCCESS, natural);
	} 

	public void message(CommandSender sender, PluginMsg pm, boolean actionBar) {
		if (actionBar && sender instanceof Player) {
			((Player)sender).spigot().sendMessage(ChatMessageType.ACTION_BAR, pm.component);
		} else {
			sender.sendMessage(pm.msg);
		}
	}

	public List<ItemStack> splitEnchants(Map<Enchantment, Integer> enchants) {
		return enchants.entrySet().stream().map((e) -> enchantBook(e.getKey(), e.getValue())).collect(Collectors.toList());
	}
	
	private ItemStack enchantBook(Enchantment enchant, int level) {
		ItemStack is = new ItemStack(Material.ENCHANTED_BOOK);
		EnchantmentStorageMeta im = (EnchantmentStorageMeta) is.getItemMeta();
		im.addStoredEnchant(enchant, level, true);
		is.setItemMeta(im);
		return is;
	}
	
	private ItemStack[] removals(int size) {
		ItemStack[] removals = new ItemStack[size];
		Arrays.fill(removals, BOOK);
		return removals;
	}

	private int getTotalExp(Player player) {
		double a = 1, b = 6, c = 0;
		int d = 2, e = 7;
		int level = player.getLevel();

		if (level >= 32) {
			a = 4.5;
			b = -162.5;
			c = 2220;
			d = 5;
			e = -38;
		} else if (level >= 17) {
			a = 2.5;
			b = -40.5;
			c = 360;
			d = 9;
			e = -158;
		}

		int xpNeeded = d * level + e;

		return (int) (Math.round(a * Math.pow(level, 2) + b * level + c) + player.getExp() * xpNeeded);
	}

	private void setTotalExp(Player player, int amount) {
		double a = 1, b = 6, c = -amount;
		int d = 2, e = 7;

		if (amount >= 1507) {
			a = 4.5;
			b = -162.5;
			c = 2220 - amount;
			d = 5;
			e = -38;
		} else if (amount >= 351) {
			a = 2.5;
			b = -40.5;
			c = 360 - amount;
			d = 9;
			e = -158;
		}

		int level = (int) Math.floor((-b + Math.sqrt(Math.pow(b, 2) - (4 * a * c))) / (2 * a));
		int xpForLevel = (int) (a * Math.pow(level, 2) + (b * level) + (c + amount));
		int xpNeeded = d * level + e;

		player.setLevel(level);
		player.setExp(Math.round((amount - xpForLevel) / (float) xpNeeded));
	}
}
