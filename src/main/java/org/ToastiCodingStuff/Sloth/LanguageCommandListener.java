package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

/**
 * Command listener for language settings
 */
public class LanguageCommandListener extends ListenerAdapter {

    private final LanguageManager langManager;

    public LanguageCommandListener(LanguageManager langManager) {
        this.langManager = langManager;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("language")) return;
        if (!event.isFromGuild()) {
            event.reply(langManager.getTranslation(LanguageManager.DEFAULT_LANGUAGE, "language.server_only")).setEphemeral(true).queue();
            return;
        }

        // Check permissions
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            String msg = langManager.get(event.getGuild().getId(), "general.permission_denied");
            event.reply(msg).setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        Container container = buildLanguageSettingsContainer(guildId);

        MessageCreateBuilder messageBuilder = new MessageCreateBuilder().setComponents(container);
        event.reply(messageBuilder.useComponentsV2().build()).setEphemeral(true).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("lang_")) return;

        if (!event.isFromGuild() || event.getGuild() == null) return;

        String guildId = event.getGuild().getId();

        // Check permissions
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            String msg = langManager.get(guildId, "general.permission_denied");
            event.reply(msg).setEphemeral(true).queue();
            return;
        }

        if (componentId.startsWith("lang_set_")) {
            String newLang = componentId.replace("lang_set_", "");
            handleLanguageChange(event, guildId, newLang);
        } else if (componentId.equals("lang_refresh")) {
            refreshLanguageSettings(event, guildId);
        }
    }

    private void handleLanguageChange(ButtonInteractionEvent event, String guildId, String newLang) {
        if (!langManager.isValidLanguage(newLang)) {
            event.reply(langManager.get(guildId, "language.invalid")).setEphemeral(true).queue();
            return;
        }

        boolean success = langManager.setGuildLanguage(guildId, newLang);

        if (success) {
            // Refresh the display with new language
            Container container = buildLanguageSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();
        } else {
            event.reply(langManager.get(guildId, "language.change_failed")).setEphemeral(true).queue();
        }
    }

    private void refreshLanguageSettings(ButtonInteractionEvent event, String guildId) {
        Container container = buildLanguageSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private Container buildLanguageSettingsContainer(String guildId) {
        String currentLang = langManager.getGuildLanguage(guildId);
        String currentLangDisplay = langManager.getLanguageFlag(currentLang) + " " +
                                    langManager.getLanguageDisplayName(currentLang);

        // Build language selection buttons
        StringBuilder availableLangsText = new StringBuilder();
        for (String lang : langManager.getAvailableLanguages()) {
            String flag = langManager.getLanguageFlag(lang);
            String name = langManager.getLanguageDisplayName(lang);
            String status = lang.equals(currentLang) ? " ✓" : "";
            availableLangsText.append(String.format("%s **%s**%s\n", flag, name, status));
        }

        return Container.of(
                TextDisplay.of("# " + langManager.get(guildId, "language.title")),
                TextDisplay.of(langManager.get(guildId, "language.description")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## " + langManager.get(guildId, "language.current")),
                TextDisplay.of("**" + currentLangDisplay + "**"),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## " + langManager.get(guildId, "language.available")),
                TextDisplay.of(availableLangsText.toString()),

                ActionRow.of(
                        Button.of(currentLang.equals(LanguageManager.ENGLISH)
                                        ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS
                                        : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "lang_set_en", "🇬🇧 English"),
                        Button.of(currentLang.equals(LanguageManager.GERMAN)
                                        ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS
                                        : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "lang_set_de", "🇩🇪 Deutsch")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("lang_refresh", langManager.get(guildId, "buttons.refresh"))
                )
        ).withAccentColor(0x3498DB); // Blue
    }
}

