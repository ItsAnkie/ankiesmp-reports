package net.voltical.reports.gui;

import net.kyori.adventure.text.Component;
import net.voltical.reports.ReportsPlugin;
import net.voltical.reports.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The first chest menu: Player Report / Bug Report / Suggestion / Close. */
public final class MainMenu {

    private MainMenu() {}

    /** Custom holder so InventoryClickEvent can identify this menu and its actions. */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        public final Map<Integer, String> actions = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(ReportsPlugin plugin, Player player) {
        FileConfiguration cfg = plugin.getConfig();
        int rows = clampRows(cfg.getInt("gui.main.rows", 3));
        Component title = Text.color(cfg.getString("gui.main.title", "&8Report Menu"));

        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, title);
        holder.inventory = inv;

        if (cfg.getBoolean("gui.main.filler", true)) {
            Material fillerMat = material(cfg.getString("gui.main.filler-material", "GRAY_STAINED_GLASS_PANE"),
                    Material.GRAY_STAINED_GLASS_PANE, plugin);
            ItemStack filler = simple(fillerMat, " ");
            for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
        }

        place(plugin, cfg, inv, holder, "player-report", player);
        place(plugin, cfg, inv, holder, "bug-report", player);
        place(plugin, cfg, inv, holder, "suggestion", player);
        place(plugin, cfg, inv, holder, "close", player);

        player.openInventory(inv);
    }

    private static void place(ReportsPlugin plugin, FileConfiguration cfg, Inventory inv,
                              Holder holder, String key, Player viewer) {
        String base = "gui.main.items." + key;
        int slot = cfg.getInt(base + ".slot", -1);
        if (slot < 0 || slot >= inv.getSize()) return;

        Material mat = material(cfg.getString(base + ".material", "STONE"), Material.STONE, plugin);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = cfg.getString(base + ".name", "");
            if (!name.isEmpty()) meta.displayName(Text.item(name));

            List<String> lore = cfg.getStringList(base + ".lore");
            if (!lore.isEmpty()) {
                List<Component> lc = new ArrayList<>();
                for (String line : lore) lc.add(Text.item(line));
                meta.lore(lc);
            }

            // Show the viewer's own head on the Player Report button.
            if (meta instanceof SkullMeta skull && key.equals("player-report")) {
                skull.setOwningPlayer(viewer);
            }
            item.setItemMeta(meta);
        }

        inv.setItem(slot, item);
        holder.actions.put(slot, key);
    }

    private static ItemStack simple(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    static Material material(String name, Material fallback, ReportsPlugin plugin) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name.toUpperCase());
        if (m == null) {
            plugin.getLogger().warning("Unknown material '" + name + "', using " + fallback + " instead.");
            return fallback;
        }
        return m;
    }

    private static int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }
}
