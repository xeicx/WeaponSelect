package com.xeicx.weaponselect;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Particle;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class WeaponSelectPlugin extends JavaPlugin implements Listener {

    private final String MENU_TITLE = ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Choose Your Weapon";
    private File dataFile;
    private FileConfiguration data;
    private NamespacedKey itemKey;
    private NamespacedKey selectedKey;

    // cooldowns: uuid -> weapon -> lastUseMillis
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    // default cooldown seconds per weapon
    private final Map<String, Integer> defaultCooldowns = Map.of(
            "SWORD", 60,
            "MACE", 90,
            "AXE", 45,
            "BOW", 120,
            "CROSSBOW", 100,
            "TRIDENT", 80
    );

    @Override
    public void onEnable() {
        getLogger().info("WeaponSelect plugin enabling...");

        itemKey = new NamespacedKey(this, "weapon_item");
        selectedKey = new NamespacedKey(this, "selected_weapon");

        // data file
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        // register events
        Bukkit.getPluginManager().registerEvents(this, this);

        getLogger().info("WeaponSelect enabled.");
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("WeaponSelect disabled.");
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save data.yml: " + e.getMessage());
        }
    }

    private boolean hasChosen(UUID uuid) {
        return data.getBoolean("chosen." + uuid.toString(), false);
    }

    private void setChosen(UUID uuid, String weaponId) {
        data.set("chosen." + uuid.toString(), true);
        data.set("weapon." + uuid.toString(), weaponId);
        saveData();
    }

    private String getPlayerWeapon(UUID uuid) {
        return data.getString("weapon." + uuid.toString(), null);
    }

    // Open menu for a player
    private void openWeaponMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);

        // Blank fill (optional)
        ItemStack filler = makeGlassPane();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Place the 6 weapons in the center row slots 11..16 (0-based)
        inv.setItem(11, createWeaponItem(Material.NETHERITE_SWORD, "Sword",
                List.of(
                        ChatColor.GRAY + "Ability: Blade Surge",
                        ChatColor.GRAY + "Unleash a short Strength boost.",
                        ChatColor.YELLOW + "Cooldown: " + defaultCooldowns.get("SWORD") + "s"
                ), "SWORD"));

        inv.setItem(12, createWeaponItem(Material.NETHERITE_PICKAXE, "Mace",
                List.of(
                        ChatColor.GRAY + "Ability: Crushing Blow",
                        ChatColor.GRAY + "Radial knockback + damage to nearby foes.",
                        ChatColor.YELLOW + "Cooldown: " + defaultCooldowns.get("MACE") + "s"
                ), "MACE"));

        inv.setItem(13, createWeaponItem(Material.NETHERITE_AXE, "Axe",
                List.of(
                        ChatColor.GRAY + "Ability: Cleave",
                        ChatColor.GRAY + "Damage nearby enemies in a short radius.",
                        ChatColor.YELLOW + "Cooldown: " + defaultCooldowns.get("AXE") + "s"
                ), "AXE"));

        inv.setItem(14, createWeaponItem(Material.BOW, "Bow",
                List.of(
                        ChatColor.GRAY + "Ability: Rapid Volley",
                        ChatColor.GRAY + "Fire a volley of fast arrows.",
                        ChatColor.YELLOW + "Cooldown: " + defaultCooldowns.get("BOW") + "s"
                ), "BOW"));

        inv.setItem(15, createWeaponItem(Material.CROSSBOW, "Crossbow",
                List.of(
                        ChatColor.GRAY + "Ability: Piercing Shot",
                        ChatColor.GRAY + "Launch a single powerful bolt.",
                        ChatColor.YELLOW + "Cooldown: " + defaultCooldowns.get("CROSSBOW") + "s"
                ), "CROSSBOW"));

        inv.setItem(16, createWeaponItem(Material.TRIDENT, "Trident",
                List.of(
                        ChatColor.GRAY + "Ability: Storm Throw",
                        ChatColor.GRAY + "Throw a trident that stuns/hits targets.",
                        ChatColor.YELLOW + "Cooldown: " + defaultCooldowns.get("TRIDENT") + "s"
                ), "TRIDENT"));

        p.openInventory(inv);
    }

    private ItemStack makeGlassPane() {
        ItemStack is = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(" ");
        is.setItemMeta(im);
        return is;
    }

    private ItemStack createWeaponItem(Material mat, String display, List<String> lore, String id) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + display);
        meta.setLore(lore.stream().map(s -> s).collect(Collectors.toList()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        // mark with persistent data so we can identify which button was clicked
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    // Give the player the chosen item (with a selected tag)
    private void givePlayerWeapon(Player p, String id) {
        ItemStack item;
        switch (id) {
            case "SWORD":
                item = new ItemStack(Material.NETHERITE_SWORD);
                break;
            case "MACE":
                // Use pickaxe as mace representation
                item = new ItemStack(Material.NETHERITE_PICKAXE);
                break;
            case "AXE":
                item = new ItemStack(Material.NETHERITE_AXE);
                break;
            case "BOW":
                item = new ItemStack(Material.BOW);
                break;
            case "CROSSBOW":
                item = new ItemStack(Material.CROSSBOW);
                break;
            case "TRIDENT":
                item = new ItemStack(Material.TRIDENT);
                break;
            default:
                item = new ItemStack(Material.STICK);
        }

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + id.substring(0, 1) + id.substring(1).toLowerCase());
        meta.getPersistentDataContainer().set(selectedKey, PersistentDataType.STRING, id);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Chosen weapon: " + ChatColor.YELLOW + id);
        lore.add("");
        lore.add(ChatColor.GRAY + "Right-click to use the weapon ability.");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);

        // give the item (try to put in inventory)
        p.getInventory().addItem(item);
    }

    /* Events */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // open menu for players who haven't chosen yet
        if (!hasChosen(p.getUniqueId())) {
            // delay one tick to avoid "can't open while joining" issues
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline()) openWeaponMenu(p);
                }
            }.runTaskLater(this, 1L);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getView().getTitle().equals(MENU_TITLE)) {
            e.setCancelled(true); // prevent item moving
            if (e.getCurrentItem() == null) return;
            ItemMeta meta = e.getCurrentItem().getItemMeta();
            if (meta == null) return;
            String id = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
            if (id == null) return;
            // selection logic
            if (!(e.getWhoClicked() instanceof Player)) return;
            Player p = (Player) e.getWhoClicked();

            // Give weapon, close menu, save choice
            givePlayerWeapon(p, id);
            setChosen(p.getUniqueId(), id);

            p.closeInventory();
            p.sendMessage(ChatColor.GREEN + "You have chosen: " + ChatColor.YELLOW + id);
        }
    }

    // Use ability on right-click while holding chosen weapon in main hand
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return; // only main hand
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return;
        String id = item.getItemMeta().getPersistentDataContainer().get(selectedKey, PersistentDataType.STRING);
        if (id == null) return;

        // confirm this player actually chose this weapon (safety)
        String chosen = getPlayerWeapon(p.getUniqueId());
        if (chosen == null || !chosen.equals(id)) return;

        // check cooldown
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(p.getUniqueId(), Collections.emptyMap()).getOrDefault(id, 0L);
        int cdSeconds = defaultCooldowns.getOrDefault(id, 60);
        long availableAt = last + (cdSeconds * 1000L);
        if (now < availableAt) {
            long remain = (availableAt - now + 999) / 1000;
            p.sendMessage(ChatColor.RED + "Ability on cooldown: " + ChatColor.YELLOW + remain + ChatColor.RED + "s");
            e.setCancelled(true);
            return;
        }

        // perform ability based on id
        switch (id) {
            case "SWORD":
                doSwordAbility(p);
                break;
            case "MACE":
                doMaceAbility(p);
                break;
            case "AXE":
                doAxeAbility(p);
                break;
            case "BOW":
                doBowAbility(p);
                break;
            case "CROSSBOW":
                doCrossbowAbility(p);
                break;
            case "TRIDENT":
                doTridentAbility(p);
                break;
            default:
                p.sendMessage(ChatColor.RED + "Unknown weapon ability.");
                return;
        }

        // set cooldown
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(id, System.currentTimeMillis());
        p.sendMessage(ChatColor.GREEN + "Ability activated. Cooldown: " + ChatColor.YELLOW + cdSeconds + ChatColor.GREEN + "s");
        e.setCancelled(true);
    }

    /* Abilities (simple, safe implementations) */

    private void doSwordAbility(Player p) {
        // Strength II for 8s (use getByName to avoid constant differences)
        PotionEffectType pet = PotionEffectType.getByName("INCREASE_DAMAGE");
        if (pet != null) {
            p.addPotionEffect(new PotionEffect(pet, 8 * 20, 1));
        }
        p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 1);
    }

    private void doMaceAbility(Player p) {
        // Radial knockback + small damage to nearby living entities within 4 blocks
        Collection<Entity> nearby = p.getNearbyEntities(4, 4, 4);
        int hit = 0;
        for (Entity ent : nearby) {
            if (ent instanceof LivingEntity && !(ent instanceof Player && ((Player) ent).equals(p))) {
                LivingEntity le = (LivingEntity) ent;
                Vector dir = le.getLocation().toVector().subtract(p.getLocation().toVector()).normalize();
                le.setVelocity(dir.multiply(1.2).setY(0.6));
                double health = le.getHealth();
                le.damage(4.0, p); // 2 hearts
                hit++;
            }
        }
        p.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, p.getLocation().add(0, 1, 0), 1);
        p.sendMessage(ChatColor.GRAY + "Mace hit " + hit + " target(s).");
    }

    private void doAxeAbility(Player p) {
        // Cleave: damage nearby entities with a short radius
        Collection<Entity> nearby = p.getNearbyEntities(3, 3, 3);
        int hit = 0;
        for (Entity ent : nearby) {
            if (ent instanceof LivingEntity && !(ent instanceof Player && ((Player) ent).equals(p))) {
                LivingEntity le = (LivingEntity) ent;
                le.damage(6.0, p); // 3 hearts
                hit++;
            }
        }
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 10);
        p.sendMessage(ChatColor.GRAY + "Axe cleaved " + hit + " target(s).");
    }

    private void doBowAbility(Player p) {
        // Rapid volley: launch 4 arrows quickly
        World w = p.getWorld();
        new BukkitRunnable() {
            int runs = 0;

            @Override
            public void run() {
                if (runs++ >= 4) cancel();
                Arrow a = p.launchProjectile(Arrow.class);
                a.setShooter(p);
                a.setVelocity(p.getLocation().getDirection().multiply(2.0));
                a.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
            }
        }.runTaskTimer(this, 0L, 4L); // every 4 ticks
    }

    private void doCrossbowAbility(Player p) {
        // Piercing shot: launch a single fast arrow
        Arrow a = p.launchProjectile(Arrow.class);
        a.setShooter(p);
        a.setVelocity(p.getLocation().getDirection().multiply(3.0));
        a.setFireTicks(0);
        a.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 8);
    }

    private void doTridentAbility(Player p) {
        // Launch a trident projectile (if supported), else launch fast arrow as fallback
        try {
            p.launchProjectile(org.bukkit.entity.Trident.class);
        } catch (Throwable t) {
            // fallback: strong arrow
            Arrow a = p.launchProjectile(Arrow.class);
            a.setVelocity(p.getLocation().getDirection().multiply(2.5));
        }
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 8);
    }
}
