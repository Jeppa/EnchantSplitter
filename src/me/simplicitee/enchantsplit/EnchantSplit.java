package me.simplicitee.enchantsplit;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatColor;

public class EnchantSplit extends JavaPlugin implements CommandExecutor {

	private static final ItemStack BOOK = new ItemStack(Material.BOOK);
	private static final String ERROR_NO_PERM = "ErrorMessage.NoPermission";
	private static final String ERROR_PLAYER_ONLY = "ErrorMessage.PlayerOnly";
	private static final String ERROR_ENCHANTED = "ErrorMessage.NotEnchantedBook";
	private static final String ERROR_ENOUGH_ENCHANTS = "ErrorMessage.NotEnoughEnchants";
	private static final String ERROR_ENOUGH_BOOKS = "ErrorMessage.NotEnoughBooks";
	private static final String ERROR_ENOUGH_XP = "ErrorMessage.NotEnoughXP";
	private static final String EXPERIENCE_COST = "ExperienceCost";
	
	private String noPerms = ChatColor.RED + "You don't have permission to use /enchantsplit!";
	private String playerOnly = ChatColor.RED + "Only a player can run /enchantsplit!";
	private String notEnoughEnchants = ChatColor.RED + "The enchanted book does not have enough enchants to split!";
	private String notEnchanted = ChatColor.RED + "Only an enchanted book can be split!";
	private String notEnoughBooks = ChatColor.RED + "You do not have enough books in your inventory to split the enchanted book!";
	private String notEnoughXp = ChatColor.RED + "You do not have enough xp to split the enchanted book!";
	private int expCost = 10;
	
	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		FileConfiguration config = getConfig();
		
		if (config.contains(ERROR_NO_PERM)) {
			this.noPerms = ChatColor.translateAlternateColorCodes('&', config.getString(ERROR_NO_PERM));
		}
		
		if (config.contains(ERROR_PLAYER_ONLY)) {
			this.playerOnly = ChatColor.translateAlternateColorCodes('&', config.getString(ERROR_PLAYER_ONLY));
		}
		
		if (config.contains(ERROR_ENCHANTED)) {
			this.notEnchanted = ChatColor.translateAlternateColorCodes('&', config.getString(ERROR_ENCHANTED));
		}
		
		if (config.contains(ERROR_ENOUGH_ENCHANTS)) {
			this.notEnoughEnchants = ChatColor.translateAlternateColorCodes('&', config.getString(ERROR_ENOUGH_ENCHANTS));
		}
		
		if (config.contains(ERROR_ENOUGH_BOOKS)) {
			this.notEnoughBooks = ChatColor.translateAlternateColorCodes('&', config.getString(ERROR_ENOUGH_BOOKS));
		}
		
		if (config.contains(ERROR_ENOUGH_XP)) {
			this.notEnoughXp = ChatColor.translateAlternateColorCodes('&', config.getString(ERROR_ENOUGH_XP));
		}
		
		if (config.contains(EXPERIENCE_COST)) {
			this.expCost = config.getInt(EXPERIENCE_COST);
		}
		
		getServer().getPluginCommand("enchantsplit").setExecutor(this);
	}
	
	@Override
	public void onDisable() {}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(playerOnly);
			return true;
		} else if (!sender.hasPermission("split.enchants")) {
			sender.sendMessage(noPerms);
			return true;
		}
		
		Player player = (Player) sender;
		ItemStack book = player.getInventory().getItemInMainHand();
		
		if (book.getType() != Material.ENCHANTED_BOOK) {
			player.sendMessage(notEnchanted);
			return true;
		}
		
		Map<Enchantment, Integer> enchants = ((EnchantmentStorageMeta) book.getItemMeta()).getStoredEnchants();
		
		if (enchants.size() <= 1) {
			player.sendMessage(notEnoughEnchants);
			return true;
		} else if (!player.getInventory().containsAtLeast(BOOK, enchants.size())) {
			player.sendMessage(notEnoughBooks);
			return true;
		}
		
		int exp = expCost <= 0 || player.getGameMode() == GameMode.CREATIVE ? 0 : expCost * enchants.size();
		
		if (player.getTotalExperience() < exp) {
			player.sendMessage(notEnoughXp);
			return true;
		}
		
		player.setTotalExperience(player.getTotalExperience() - exp);
		player.getInventory().setItemInMainHand(null);
		player.getInventory().removeItem(removals(enchants.size()));
		enchants.keySet().stream()
		.map((e) -> enchantBook(e, enchants.get(e)))
		.forEach((i) -> player.getWorld().dropItemNaturally(player.getLocation(), i, (item) -> item.setPickupDelay(0)));
		return true;
	}

	public List<ItemStack> splitEnchants(Map<Enchantment, Integer> enchants) {
		return enchants.keySet().stream().map((e) -> enchantBook(e, enchants.get(e))).collect(Collectors.toList());
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
}
