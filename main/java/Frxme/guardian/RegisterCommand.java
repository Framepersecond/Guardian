package Frxme.guardian;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RegisterCommand implements CommandExecutor, TabCompleter {
        private final Guardian plugin;

        public RegisterCommand(Guardian plugin) {
                this.plugin = plugin;
        }

        @Override
        public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                        @NotNull String[] args) {
                if (!(sender instanceof Player player)) {
                        sender.sendMessage(
                                        Component.text("Dieser Befehl kann nur von Spielern verwendet werden!",
                                                        NamedTextColor.RED));
                        return true;
                }

                // Check if player already has a linked account
                if (plugin.getDatabaseManager().isMinecraftAccountLinked(player.getUniqueId())) {
                        player.sendMessage(Component.text(""));
                        player.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                                        .append(Component.text(
                                                        "Dein Minecraft-Konto ist bereits mit einem Web-Account verknüpft!",
                                                        NamedTextColor.GRAY)));
                        player.sendMessage(Component.text(""));
                        return true;
                }

                // Determine role based on permissions
                String role;
                if (player.hasPermission("guardian.admin")) {
                        role = "admin";
                } else if (player.hasPermission("guardian.user")) {
                        role = "user";
                } else {
                        player.sendMessage(Component.text(""));
                        player.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                                        .append(Component.text(
                                                        "Du hast keine Berechtigung, dich für das Web-Interface zu registrieren!",
                                                        NamedTextColor.GRAY)));
                        player.sendMessage(Component.text("  Benötigt: ", NamedTextColor.DARK_GRAY)
                                        .append(Component.text("guardian.user", NamedTextColor.YELLOW))
                                        .append(Component.text(" oder ", NamedTextColor.DARK_GRAY))
                                        .append(Component.text("guardian.admin", NamedTextColor.YELLOW)));
                        player.sendMessage(Component.text(""));
                        return true;
                }

                // Generate registration code
                String code = plugin.getDatabaseManager().createRegistrationCode(
                                player.getUniqueId(),
                                player.getName(),
                                role);

                if (code == null) {
                        player.sendMessage(Component.text(""));
                        player.sendMessage(Component.text("✖ ", NamedTextColor.RED)
                                        .append(Component.text(
                                                        "Fehler beim Erstellen des Registrierungscodes. Bitte versuche es erneut.",
                                                        NamedTextColor.GRAY)));
                        player.sendMessage(Component.text(""));
                        return true;
                }

                // Send success message with the code
                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.DARK_AQUA));
                player.sendMessage(Component.text("  🛡 ", NamedTextColor.AQUA)
                                .append(Component.text("Guardian Web-Registrierung", NamedTextColor.WHITE,
                                                TextDecoration.BOLD)));
                player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.DARK_AQUA));
                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("  Dein Registrierungscode:", NamedTextColor.GRAY));
                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("    ", NamedTextColor.WHITE)
                                .append(Component.text(code, NamedTextColor.GREEN, TextDecoration.BOLD)));
                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("  Rolle: ", NamedTextColor.GRAY)
                                .append(Component.text(role.toUpperCase(),
                                                role.equals("admin") ? NamedTextColor.RED : NamedTextColor.BLUE)));
                player.sendMessage(Component.text("  Gültig für: ", NamedTextColor.GRAY)
                                .append(Component.text("10 Minuten", NamedTextColor.YELLOW)));
                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("  Öffne das Web-Interface und gib diesen Code",
                                NamedTextColor.DARK_GRAY));
                player.sendMessage(Component.text("  zusammen mit deinem gewünschten Benutzernamen",
                                NamedTextColor.DARK_GRAY));
                player.sendMessage(
                                Component.text("  und Passwort ein, um die Registrierung", NamedTextColor.DARK_GRAY));
                player.sendMessage(Component.text("  abzuschließen.", NamedTextColor.DARK_GRAY));
                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.DARK_AQUA));
                player.sendMessage(Component.text(""));

                return true;
        }

        @Override
        public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                        @NotNull String alias, @NotNull String[] args) {
                // /guardian has subcommands, so we handle tab completion for them
                if (args.length == 1) {
                        // Return subcommands that match the partial input
                        List<String> completions = new ArrayList<>();
                        String partial = args[0].toLowerCase();

                        if ("register".startsWith(partial)) {
                                completions.add("register");
                        }

                        return completions;
                }

                // No further arguments for /guardian register
                return new ArrayList<>();
        }
}
