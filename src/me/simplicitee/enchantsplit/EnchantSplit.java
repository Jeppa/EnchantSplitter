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
	private static final String EXTRA_COST = "ExtraCost"; 			//Jeppa: added additional costs (if needed), can also be used as fixed costs if ExperienceCost is set to 0 ...
	private static final String NEEDS_SNEAKING = "NeedsSneaking";	//Jeppa: only use 'natural' mode if player is crouching/sneeking...
	
	private int expCost = 10; //default value, in default config it's 5
	private int extraCost = 5; //default value
	
	private boolean needsSneak = true;
	
	private boolean drop = false;	//Jeppa: defines internaly if books should be dropped or given...
	
	private FileConfiguration config = getConfig();
	
	public enum PluginMsg {
		NO_PERM("NoPermission", "&cYou don't have permission to use /enchantsplit!"),
		PLAYER_ONLY("PlayerOnly", "&cOnly a player can run /enchantsplit!"),
		NOT_ENCHANTED("NotEnchantedBook", "&cOnly an enchanted book can be split!"),
		NOT_ENOUGH_ENCHANTS("NotEnoughEnchants", "&cThe enchanted book does not have enough enchants to split!"),
		NOT_ENOUGH_BOOKS("NotEnoughBooks", "&cYou do not have enough books in your inventory to split the enchanted book!"),
		NOT_ENOUGH_XP("NotEnoughXP", "&cYou do not have enough xp to split the enchanted book!"),
		
		NOT_ENOUGH_SLOTS("NotEnoughFreeSlots", "&cThere are not enough free slots in your inventory!"), //add by Jeppa
		TOO_MUCH_BOOKS("TooMuchBooks", "&cPlease split only one book at a time!"), //add by Jeppa
		
		SUCCESS("Success", "&aEnchanted book was successfully split!");

		private String path, msg="Config Error!"; //Jeppa: just msg with some default ;)
		private TextComponent component;

		private PluginMsg(String name, String msg) {
			this.path = "Message." + name;
			setText(msg);
		}

		public void setText(String msg) {
			this.msg = ChatColor.translateAlternateColorCodes('&', msg);
			this.component = new TextComponent(this.msg);
		}
	}
	
	@Override
	public void onEnable() {
		//Jeppa: copy default values and save them...
		config.options().copyDefaults(true);
		this.saveDefaultConfig();
		this.saveConfig();
		
		for (PluginMsg msg : PluginMsg.values()) {
			if (config.contains(msg.path)) {
				msg.setText(config.getString(msg.path)); 
			}
		}
		
		if (config.contains(EXPERIENCE_COST)) {
			this.expCost = config.getInt(EXPERIENCE_COST);
		}
		//Jeppa: additional cost...
		if (config.contains(EXTRA_COST)) {
			this.extraCost = config.getInt(EXTRA_COST);
		}
		//Jeppa: additional config value... sneeking/crouchuing at EnchantTable!
		if (config.contains(NEEDS_SNEAKING)) {
			this.needsSneak = config.getBoolean(NEEDS_SNEAKING);
		}
		
		getServer().getPluginCommand("enchantsplit").setExecutor(this);
		getServer().getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable() {}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!cmd.getName().equalsIgnoreCase("enchantsplit")) { //Jeppa: checking for 'label' won't check alias!
			return false;
		}

		if (!(sender instanceof Player)) {
			message(sender, PluginMsg.PLAYER_ONLY, false);
			return true;
		} else if (!sender.hasPermission("split.enchants.command")) {
			message(sender, PluginMsg.NO_PERM, false); //Jeppa: fix text: NOT_ENCHANTED -> NO_PERM !
			return true;
		}

		//Jeppa: additional help text output like in V1.1
		if (args.length == 0) {
			enchantSplit((Player) sender, false);
		} else {
			if (args[0].equalsIgnoreCase("help")) {
				sender.sendMessage(ChatColor.AQUA+ "Use the /enchantsplit command while holding an " +ChatColor.YELLOW+ "Enchanted Book" +ChatColor.AQUA+ " to split the enchantments into multiple enchanted books. You must also have the same number of books as there are enchantments on the enchanted books.");
			} else {
				sender.sendMessage(ChatColor.RED+ "I think you should use /enchantsplit help !");
			}
		}
		return true;
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEnchantingTableInteract(PlayerInteractEvent event) {
		Material echantTable;
		 //Jeppa: Fix for different names...
		try {
			echantTable = Material.valueOf("ENCHANTMENT_TABLE");
		} catch (Exception e) {
			echantTable = Material.ENCHANTING_TABLE;
		}
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock().getType() != echantTable) {
			return;
		} else if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.ENCHANTED_BOOK) {
			return;
		}
		//Jeppa: sneaking-check...
		if (needsSneak==true && !event.getPlayer().isSneaking()) {
			return;
		}
		
		event.setCancelled(true);
		enchantSplit(event.getPlayer(), true);
	}

	public void enchantSplit(Player player, boolean natural) { //natural = table...
		ItemStack book = player.getInventory().getItemInMainHand();
		
		if (book.getType() != Material.ENCHANTED_BOOK) {
			message(player, PluginMsg.NOT_ENCHANTED, natural);
			return;
		} else if (natural && !player.hasPermission("split.enchants.natural")) {
			message(player, PluginMsg.NO_PERM, true);
			return;
		}
		
		Map<Enchantment, Integer> enchants = ((EnchantmentStorageMeta) book.getItemMeta()).getStoredEnchants();
		
		//Jeppa Fix for stacked books...
		if (book.getAmount() > 1) {
			message(player, PluginMsg.TOO_MUCH_BOOKS, natural);
            return;
		}
		
		if (enchants.size() <= 1) { //<=1 ? :)
			message(player, PluginMsg.NOT_ENOUGH_ENCHANTS, natural);
			return;
		} else if (!player.getInventory().containsAtLeast(BOOK, enchants.size())) {
			message(player, PluginMsg.NOT_ENOUGH_BOOKS, natural);
			return;
		}
		
		if (player.getGameMode() != GameMode.CREATIVE) {
			//Jeppa: some fixes for XP-handling.... etc.
			double exp = expCost <= 0 ? 0 + extraCost : (expCost * enchants.size()) + extraCost; //Jeppa: + extraCost!
			double total = getTotalExp(player); //Jeppa: fix inside!
			
			if (total < exp) {
				message(player, PluginMsg.NOT_ENOUGH_XP, natural);
				return;
			}

			player.giveExp(-(int)exp); //Jeppa: no need for any complicated calculation... just add ;)
		}

		book.setAmount(book.getAmount() - 1);	//Jeppa: another fix for removal of too much books...
		player.getInventory().removeItem(removals(enchants.size()));
		
//		splitEnchants(enchants).forEach((i) -> player.getWorld().dropItemNaturally(player.getLocation(), i, (item) -> item.setPickupDelay(0)));
		//Jeppa: fix/add ... give the books and only drop if no space left...
		splitEnchants(enchants).forEach((i) -> {
			while (freeSlots(player)>0) {
				player.getInventory().addItem(i);
				drop=false;
				return;
			}
			//drop the rest
			drop=true;
			player.getWorld().dropItemNaturally(player.getLocation(), i).setPickupDelay(0);
		}); 

		if (drop) getServer().getScheduler().runTaskLater(this, () -> { //some delay is needed in 'natural' mode...
			message(player, PluginMsg.NOT_ENOUGH_SLOTS, natural);
		},1);
		getServer().getScheduler().runTaskLater(this, () -> {
			message(player, PluginMsg.SUCCESS, natural);
		},(drop&&natural)?60:1);
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

	//Jeppa: new XP calculation...: 
	private double getTotalExp(Player player) {
		int level = player.getLevel();

		double playerLevelXP = getXPforLevel(level);
		double xpForNextLevel = getXPforLevel(level+1)-playerLevelXP;
		return playerLevelXP + Math.round((player.getExp() * xpForNextLevel));
	}
	//Jeppa: sub for getting base XP value for level... 
	private double getXPforLevel(int level) {
		int d = 2;
		double diffVal=level*7;

		if (level >= 32) {
			d = 9;
			diffVal -= level*165 -2220;
		} else if (level >= 17) {
			d = 5;
			diffVal -= level*45 -360;
		}
		return ((Math.pow(level, 2)-level) *d) /2 + diffVal;
	}
	//Jeppa: sub for counting slots...
	private final int freeSlots(Player player) {
		int freeSlot = 0;
		for (ItemStack is : player.getInventory().getStorageContents()){
			if ( is == null || is.getType() == Material.AIR) {
				freeSlot++;
			}
		}
		return freeSlot;
	}
}
