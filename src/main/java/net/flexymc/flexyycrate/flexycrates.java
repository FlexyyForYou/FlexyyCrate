package net.flexymc.flexycrates;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class FlexyCrates extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<String, Location> crates = new HashMap<>();
    private final Map<UUID, String> previewing = new HashMap<>();
    private final Map<UUID, Boolean> opening = new HashMap<>();
    private NamespacedKey keyType;
    private File dataFile;
    private YamlConfiguration data;

    @Override public void onEnable() {
        saveDefaultConfig(); keyType = new NamespacedKey(this, "crate_key");
        dataFile = new File(getDataFolder(), "crates.yml");
        if (!dataFile.exists()) { try { getDataFolder().mkdirs(); dataFile.createNewFile(); } catch (IOException e) { getLogger().warning("Could not create crates.yml"); } }
        data = YamlConfiguration.loadConfiguration(dataFile); loadCrates();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("flexycrates")).setExecutor(this);
        Objects.requireNonNull(getCommand("fc")).setExecutor(this);
        getLogger().info("FlexyCrates enabled.");
    }
    @Override public void onDisable() { saveCrates(); }
    private void loadCrates() {
        crates.clear();
        if (data.getConfigurationSection("locations") == null) return;
        for (String id : data.getConfigurationSection("locations").getKeys(false)) {
            String s = "locations." + id + ".";
            World w = Bukkit.getWorld(data.getString(s + "world", ""));
            if (w != null) crates.put(id.toLowerCase(Locale.ROOT), new Location(w, data.getDouble(s+"x"), data.getDouble(s+"y"), data.getDouble(s+"z")));
        }
    }
    private void saveCrates() {
        data.set("locations", null);
        crates.forEach((id,l) -> { String s="locations."+id+"."; data.set(s+"world", l.getWorld().getName()); data.set(s+"x",l.getBlockX()); data.set(s+"y",l.getBlockY()); data.set(s+"z",l.getBlockZ()); });
        try { data.save(dataFile); } catch (IOException e) { getLogger().warning("Could not save crates.yml"); }
    }
    private String title(String id) { return ChatColor.translateAlternateColorCodes('&', getConfig().getString("crates."+id+".title", "&a"+id+" Crate")); }
    private ItemStack makeItem(String path) {
        String mat = getConfig().getString(path+".material", "STONE");
        Material material = Material.matchMaterial(mat); if (material == null) material = Material.STONE;
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, getConfig().getInt(path+".amount", 1))));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (getConfig().contains(path+".name")) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', getConfig().getString(path+".name", "Reward")));
            List<String> lore = getConfig().getStringList(path+".lore"); if (!lore.isEmpty()) { List<String> out=new ArrayList<>(); for(String line:lore) out.add(ChatColor.translateAlternateColorCodes('&', line)); meta.setLore(out); }
            if (getConfig().contains(path+".enchants")) for(String ench : getConfig().getConfigurationSection(path+".enchants").getKeys(false)) {
                org.bukkit.enchantments.Enchantment e = org.bukkit.enchantments.Enchantment.getByName(ench.toUpperCase(Locale.ROOT));
                if (e != null) meta.addEnchant(e, getConfig().getInt(path+".enchants."+ench, 1), true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    private ItemStack filler() { ItemStack i=new ItemStack(Material.GRAY_STAINED_GLASS_PANE); ItemMeta m=i.getItemMeta(); if(m!=null){m.setDisplayName(" ");i.setItemMeta(m);} return i; }
    private List<String> rewards(String id) { return getConfig().getStringList("crates."+id+".rewards"); }
    private String normalize(String s) { return s.toLowerCase(Locale.ROOT).replace(" ", ""); }
    private void openPreview(Player p, String id) {
        if (!getConfig().contains("crates."+id)) { p.sendMessage(ChatColor.RED+"Unknown crate: "+id); return; }
        Inventory inv=Bukkit.createInventory(null, 36, title(id)); for(int i=0;i<36;i++) inv.setItem(i,filler());
        List<String> rs=rewards(id); int[] slots={10,11,12,13,14,15,16,19,20,21,22,23,24,25};
        for(int n=0;n<rs.size() && n<slots.length;n++) {
            String r=rs.get(n), path="rewards."+r; ItemStack it=makeItem(path); ItemMeta meta=it.getItemMeta();
            if(meta!=null){List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>(); lore.add(""); lore.add(ChatColor.GRAY+"Choose this reward when opening."); lore.add(ChatColor.GRAY+String.format(Locale.US,"%.2f%% Chance", getConfig().getDouble("crates."+id+".chances."+r, 100.0/Math.max(1,rs.size())))); meta.setLore(lore);it.setItemMeta(meta);} inv.setItem(slots[n],it);
        }
        ItemStack key=new ItemStack(Material.TRIPWIRE_HOOK); ItemMeta km=key.getItemMeta(); if(km!=null){km.setDisplayName(ChatColor.YELLOW+"Open "+title(id));km.setLore(List.of(ChatColor.GRAY+"Keys: "+ChatColor.WHITE+countKeys(p,id),ChatColor.GREEN+"Click to start scrolling!"));key.setItemMeta(km);} inv.setItem(31,key);
        previewing.put(p.getUniqueId(),id); p.openInventory(inv);
    }
    private int countKeys(Player p,String id){int n=0; for(ItemStack i:p.getInventory().getContents()) if(isKey(i,id)) n+=i.getAmount(); return n;}
    private boolean isKey(ItemStack i,String id){return i!=null&&i.getType()==Material.TRIPWIRE_HOOK&&i.hasItemMeta()&&id.equalsIgnoreCase(i.getItemMeta().getPersistentDataContainer().get(keyType,PersistentDataType.STRING));}
    private boolean takeKey(Player p,String id){for(int s=0;s<p.getInventory().getSize();s++){ItemStack i=p.getInventory().getItem(s);if(isKey(i,id)){if(i.getAmount()>1)i.setAmount(i.getAmount()-1);else p.getInventory().setItem(s,null);return true;}}return false;}
    private ItemStack createKey(String id,int amount){ItemStack i=new ItemStack(Material.TRIPWIRE_HOOK,Math.max(1,Math.min(64,amount)));ItemMeta m=i.getItemMeta();if(m!=null){m.setDisplayName(ChatColor.translateAlternateColorCodes('&',getConfig().getString("crates."+id+".key-name","&e"+id+" Key")));m.setLore(List.of(ChatColor.GRAY+"Opens "+title(id)));m.getPersistentDataContainer().set(keyType,PersistentDataType.STRING,id);i.setItemMeta(m);}return i;}
    private String chooseReward(String id){List<String> rs=rewards(id);if(rs.isEmpty())return null;double total=0;for(String r:rs)total+=Math.max(0,getConfig().getDouble("crates."+id+".chances."+r,1));if(total<=0)return rs.get(new Random().nextInt(rs.size()));double pick=Math.random()*total;for(String r:rs){pick-=Math.max(0,getConfig().getDouble("crates."+id+".chances."+r,1));if(pick<=0)return r;}return rs.get(rs.size()-1);}
    private void startOpening(Player p,String id){if(opening.getOrDefault(p.getUniqueId(),false))return;if(!takeKey(p,id)){p.closeInventory();p.sendMessage(ChatColor.RED+"CRATE NOT OPENED:");p.sendMessage(ChatColor.GRAY+"» Crate: "+ChatColor.WHITE+title(id));p.sendMessage(ChatColor.GRAY+"» You can't afford the open cost: "+ChatColor.WHITE+"x1 "+ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',getConfig().getString("crates."+id+".key-name","&e"+id+" Key"))));return;}
        opening.put(p.getUniqueId(),true);previewing.remove(p.getUniqueId());String win=chooseReward(id);if(win==null){opening.remove(p.getUniqueId());p.sendMessage(ChatColor.RED+"This crate has no rewards configured.");return;}
        Inventory inv=Bukkit.createInventory(null,27,title(id));p.openInventory(inv);final int[] tick={0};final int total=70;new BukkitRunnable(){public void run(){if(!p.isOnline()||!p.getOpenInventory().getTitle().equals(title(id))){opening.remove(p.getUniqueId());cancel();return;}for(int i=0;i<27;i++)inv.setItem(i,filler());for(int slot=10;slot<=16;slot++){String r=rewards(id).get(new Random().nextInt(rewards(id).size()));inv.setItem(slot,makeItem("rewards."+r));}ItemStack center=makeItem("rewards."+(tick[0]>=total-1?win:rewards(id).get(new Random().nextInt(rewards(id).size()))));ItemMeta cm=center.getItemMeta();if(cm!=null){cm.setEnchantmentGlintOverride(true);center.setItemMeta(cm);}inv.setItem(13,center);p.playSound(p.getLocation(),Sound.UI_BUTTON_CLICK,0.4f,1.2f);tick[0]++;if(tick[0]>=total){cancel();opening.remove(p.getUniqueId());p.getInventory().addItem(makeItem("rewards."+win)).values().forEach(left->p.getWorld().dropItemNaturally(p.getLocation(),left));p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,1,1);p.sendMessage(ChatColor.GRAY+"You received "+ChatColor.GOLD+"x"+getConfig().getInt("rewards."+win+".amount",1)+" "+ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',getConfig().getString("rewards."+win+".name",win)))+ChatColor.GRAY+" from "+title(id)+ChatColor.GRAY+".");p.closeInventory();}}}.runTaskTimer(this,0,Math.max(1,getConfig().getInt("animation.tick-interval",2)));}
    @EventHandler public void onInteract(PlayerInteractEvent e){if(e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getClickedBlock()==null)return;Player p=e.getPlayer();Location clicked=e.getClickedBlock().getLocation();for(var entry:crates.entrySet())if(entry.getValue().getWorld().equals(clicked.getWorld())&&entry.getValue().getBlockX()==clicked.getBlockX()&&entry.getValue().getBlockY()==clicked.getBlockY()&&entry.getValue().getBlockZ()==clicked.getBlockZ()){e.setCancelled(true);openPreview(p,entry.getKey());return;}}
    @EventHandler public void onClick(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String id=previewing.get(p.getUniqueId());if(id==null||!e.getView().getTitle().equals(title(id)))return;e.setCancelled(true);if(e.getRawSlot()==31)startOpening(p,id);}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] a){if(a.length==0){sender.sendMessage(ChatColor.AQUA+"FlexyCrates "+ChatColor.GRAY+"Commands: /fc list, /fc preview <crate>, /fc setcrate <crate>, /fc givekey <player> <crate> [amount], /fc reload");return true;}String sub=a[0].toLowerCase(Locale.ROOT);
        if(sub.equals("reload")){if(!sender.hasPermission("flexycrates.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}reloadConfig();sender.sendMessage(ChatColor.GREEN+"FlexyCrates config reloaded.");return true;}
        if(sub.equals("list")){sender.sendMessage(ChatColor.GREEN+"Crates: "+String.join(", ",Objects.requireNonNull(getConfig().getConfigurationSection("crates")).getKeys(false)));return true;}
        if(sub.equals("preview")&&a.length>=2&&sender instanceof Player p){openPreview(p,normalize(a[1]));return true;}
        if(sub.equals("setcrate")&&a.length>=2&&sender instanceof Player p){if(!p.hasPermission("flexycrates.admin")){p.sendMessage(ChatColor.RED+"No permission.");return true;}String id=normalize(a[1]);if(!getConfig().contains("crates."+id)){p.sendMessage(ChatColor.RED+"Unknown crate.");return true;}Block b=p.getTargetBlockExact(6);if(b==null){p.sendMessage(ChatColor.RED+"Look at the block you want to use as this crate.");return true;}crates.put(id,b.getLocation());saveCrates();spawnHologram(b.getLocation(),id);p.sendMessage(ChatColor.GREEN+"Set "+title(id)+ChatColor.GREEN+" at that block.");return true;}
        if(sub.equals("givekey")&&a.length>=3){if(!sender.hasPermission("flexycrates.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}Player target=Bukkit.getPlayerExact(a[1]);String id=normalize(a[2]);int amount=a.length>=4?Math.max(1,Math.min(64,parseInt(a[3],1))):1;if(target==null||!getConfig().contains("crates."+id)){sender.sendMessage(ChatColor.RED+"Usage: /fc givekey <online-player> <crate> [amount]");return true;}target.getInventory().addItem(createKey(id,amount)).values().forEach(left->target.getWorld().dropItemNaturally(target.getLocation(),left));sender.sendMessage(ChatColor.GREEN+"Gave "+amount+" "+title(id)+ChatColor.GREEN+" key(s) to "+target.getName()+".");return true;}
        sender.sendMessage(ChatColor.RED+"Invalid command. Use /fc");return true;}
    private int parseInt(String s,int def){try{return Integer.parseInt(s);}catch(Exception ex){return def;}}
    private void spawnHologram(Location loc,String id){World w=loc.getWorld();for(Entity en:w.getNearbyEntities(loc.clone().add(0,1,0),0.5,2.5,0.5))if(en instanceof ArmorStand as&&as.getScoreboardTags().contains("flexycrate_"+id))as.remove();String[] lines={title(id),ChatColor.GRAY+"From Keyall",ChatColor.GRAY+"Next Keyall in "+getConfig().getString("keyall-countdown","4m 33s"),ChatColor.GREEN+countKeysPlaceholder(id)+" Keys"};for(int n=0;n<lines.length;n++){Location at=loc.clone().add(0,2.25-n*0.25,0.5);String line=lines[n];ArmorStand as=w.spawn(at,ArmorStand.class,a->{a.setInvisible(true);a.setMarker(true);a.setGravity(false);a.setCustomNameVisible(true);a.setCustomName(line);a.setSmall(true);a.setInvulnerable(true);a.addScoreboardTag("flexycrate_"+id);});}}
    private String countKeysPlaceholder(String id){return getConfig().getString("crates."+id+".hologram-keys","0");}
}
