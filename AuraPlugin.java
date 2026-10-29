package pl.aura;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class AuraPlugin extends JavaPlugin implements CommandExecutor {

    static final int OUTER = 16, INNER = 10;

    static class Aura {
        TextDisplay display;
        int lit = 0; // ile klejnotow juz sie zapalilo
    }

    // nazwa setu -> kolor klejnotow
    static final Map<String, Integer> COLORS = Map.of(
            "DIAMOND", 0x4DE8FF,
            "NETHERITE", 0xD9B8FF,
            "GOLDEN", 0xFFD24A,
            "IRON", 0xE0E0E0,
            "CHAINMAIL", 0x9AA0A6,
            "LEATHER", 0xB5651D);

    final Map<UUID, Aura> auras = new HashMap<>();
    double angle = 0;
    long ticks = 0;

    @Override
    public void onEnable() {
        getCommand("aura").setExecutor(this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 0L, 1L);
    }

    @Override
    public void onDisable() {
        auras.values().forEach(a -> a.display.remove());
        auras.clear();
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p)) return true;
        Aura old = auras.remove(p.getUniqueId());
        if (old != null) {
            old.display.remove();
            p.sendMessage(Component.text("Aura wylaczona", NamedTextColor.GRAY));
            return true;
        }
        Aura a = new Aura();
        a.display = p.getWorld().spawn(p.getLocation().add(0, 2.5, 0), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(true);
            d.setPersistent(false);
        });
        auras.put(p.getUniqueId(), a);
        p.sendMessage(Component.text("Aura wlaczona", NamedTextColor.AQUA));
        return true;
    }

    // wykrywa set po zalozonej zbroi: zwraca "DIAMOND", "NETHERITE" itd., "MIXED" albo "NONE"
    String detectSet(Player p) {
        ItemStack[] armor = p.getInventory().getArmorContents();
        String prefix = null;
        int count = 0;
        for (ItemStack it : armor) {
            if (it == null || it.getType().isAir()) continue;
            String n = it.getType().name();
            String pre = n.substring(0, n.indexOf('_'));
            count++;
            if (prefix == null) prefix = pre;
            else if (!prefix.equals(pre)) return "MIXED";
        }
        if (count == 0) return "NONE";
        return count == 4 ? prefix : "MIXED";
    }

    void gem(Location l, Color c) {
        var w = l.getWorld();
        w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(c, 1.3f));
        w.spawnParticle(Particle.DUST, l.clone().add(0, 0.09, 0), 1, 0, 0, 0, 0, new Particle.DustOptions(c, 0.8f));
        w.spawnParticle(Particle.DUST, l.clone().add(0, -0.09, 0), 1, 0, 0, 0, 0, new Particle.DustOptions(c, 0.8f));
    }

    void tick() {
        angle += 0.06;
        ticks++;
        Iterator<Map.Entry<UUID, Aura>> it = auras.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Aura a = e.getValue();
            if (p == null || !p.isOnline()) {
                a.display.remove();
                it.remove();
                continue;
            }

            String set = detectSet(p);
            int rgb = COLORS.getOrDefault(set, 0xFFFFFF);
            Color color = Color.fromRGB(rgb);

            // klejnoty zapalaja sie jeden po drugim
            if (ticks % 3 == 0 && a.lit < OUTER + INNER) {
                a.lit++;
                Location l = p.getLocation().add(0, 1, 0);
                p.getWorld().spawnParticle(Particle.END_ROD, l, 6, 0.5, 0.5, 0.5, 0.02);
            }

            Location base = p.getLocation().add(0, 1, 0);
            for (int i = 0; i < OUTER && i < a.lit; i++) {
                double ang = angle + i * 2 * Math.PI / OUTER;
                gem(base.clone().add(Math.cos(ang) * 1.2, 0.5, Math.sin(ang) * 1.2), color);
            }
            for (int j = 0; j < INNER && OUTER + j < a.lit; j++) {
                double ang = -angle * 1.3 + j * 2 * Math.PI / INNER;
                gem(base.clone().add(Math.cos(ang) * 0.8, -0.3, Math.sin(ang) * 0.8), color);
            }

            // nick + set nad graczem
            a.display.teleport(p.getLocation().add(0, 2.5, 0));
            if (ticks % 20 == 0 || a.lit == 1) {
                String setName = switch (set) {
                    case "NONE" -> "Brak setu";
                    case "MIXED" -> "Mieszany set";
                    case "GOLDEN" -> "Gold Set";
                    default -> set.charAt(0) + set.substring(1).toLowerCase() + " Set";
                };
                a.display.text(Component.text(p.getName(), TextColor.color(rgb)).append(Component.newline())
                        .append(Component.text(setName, NamedTextColor.GRAY)));
            }
        }
    }
}
