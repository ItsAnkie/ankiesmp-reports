package net.voltical.reports.gui;

import net.kyori.adventure.text.Component;
import net.voltical.reports.ReportsPlugin;
import net.voltical.reports.util.Placeholders;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Paginated player picker built from online players (excluding the viewer). */
public final class PlayerSelectMenu {

    private PlayerSelectMenu() {}

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;      // top 45 slots for heads
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 49;
    private static final int SLOT_NEXT = 53;

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        public int page;
        public int totalPages;
        public int prevSlot = -1;
        public int nextSlot = -1;
        public int backSlot = -1;
        public final Map<Integer, UUID> slotToTarget = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(ReportsPlugin plugin, Player viewer, int page) {
        FileConfiguration cfg = plugin.getConfig();

        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(viewer.getUniqueId())) targets.add(online);
        }
        targets.sort(Comparator.comparing(p -> p.getName().toLowerCase()));

        int totalPages = Math.max(1, (int) Math.ceil(targets.size() / (double) PER_PAGE));
        if (page < 0) page = 0;
        if (page > totalPages - 1) page = totalPages - 1;

        String baseTitle = cfg.getString("gui.player-select.title", "&8Select a player");
        String titleStr = totalPages > 1 ? baseTitle + " &8(" + (page + 1) + "/" + totalPages + ")" : baseTitle;
        Component title = Text.color(titleStr);

        Holder holder = new Holder();
        holder.page = page;
        holder.totalPages = totalPages;
        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        holder.inventory = inv;

        int start = page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, targets.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            Player t = targets.get(i);
            inv.setItem(slot, head(cfg, t));
            holder.slotToTarget.put(slot, t.getUniqueId());
            slot++;
        }

        holder.backSlot = SLOT_BACK;
        inv.setItem(SLOT_BACK, nav(Material.BARRIER, cfg.getString("gui.player-select.back-name", "&cBack")));

        if (page > 0) {
            holder.prevSlot = SLOT_PREV;
            inv.setItem(SLOT_PREV, nav(Material.ARROW, cfg.getString("gui.player-select.previous-name", "&ePrevious")));
        }
        if (page < totalPages - 1) {
            holder.nextSlot = SLOT_NEXT;
            inv.setItem(SLOT_NEXT, nav(Material.ARROW, cfg.getString("gui.player-select.next-name", "&eNext")));
        }

        viewer.openInventory(inv);
    }

    private static ItemStack head(FileConfiguration cfg, Player target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(target);
            String name = Placeholders.apply(cfg.getString("gui.player-select.head-name", "&e{player}"),
                    "{player}", target.getName());
            skull.displayName(Text.item(name));

            List<String> loreRaw = cfg.getStringList("gui.player-select.head-lore");
            if (!loreRaw.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreRaw) {
                    lore.add(Text.item(Placeholders.apply(line, "{player}", target.getName())));
                }
                skull.lore(lore);
            }
            item.setItemMeta(skull);
        }
        return item;
    }

    private static ItemStack nav(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(name));
            item.setItemMeta(meta);
        }
        return item;
    }
}
