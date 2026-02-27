package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

public class SetupWizardListener extends ListenerAdapter {
    private final DatabaseHandler handler;

    public SetupWizardListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    // ==================== LANGUAGE HELPER METHODS ====================

    private String t(String guildId, String key) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guildId, key);
        }
        return key;
    }

    private String t(String guildId, String key, Object... args) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guildId, key, args);
        }
        try {
            return String.format(key, args);
        } catch (Exception e) {
            return key;
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().equals("setup_start")) {
            if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
                String guildId = event.getGuild().getId();
                event.reply(t(guildId, "setup_wizard.no_permission")).setEphemeral(true).queue();
                return;
            }
            // Step 1: Language
            String guildId = event.getGuild().getId();
            event.editMessage(new MessageEditBuilder().setComponents(buildLanguageStep(guildId)).useComponentsV2().build()).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getComponentId().equals("setup_lang_select")) {
            String langCode = event.getValues().get(0);
            String guildId = event.getGuild().getId();
            handler.updateGuildLanguage(guildId, langCode);

            // Step 2: Log Channel
            event.editMessage(new MessageEditBuilder().setComponents(buildLogChannelStep(guildId)).useComponentsV2().build()).queue();
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (event.getComponentId().equals("setup_log_select")) {
            String channelId = event.getMentions().getChannels().get(0).getId();
            String guildId = event.getGuild().getId();
            handler.setLogChannel(guildId, channelId);

            // Step 3: Finalize
            event.editMessage(new MessageEditBuilder().setComponents(buildFinalStep(guildId)).useComponentsV2().build()).queue();
        }
    }

    private Container buildLanguageStep(String guildId) {
        return Container.of(
                TextDisplay.of(t(guildId, "setup_wizard.step1_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step1_description")),
                ActionRow.of(
                        StringSelectMenu.create("setup_lang_select")
                                .addOption("English", "en", Emoji.fromUnicode("🇺🇸"))
                                .addOption("Deutsch", "de", Emoji.fromUnicode("🇩🇪"))
                                .build()
                )
        ).withAccentColor(0xF1C40F);
    }

    private Container buildLogChannelStep(String guildId) {
        return Container.of(
                TextDisplay.of(t(guildId, "setup_wizard.step2_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step2_description")),
                ActionRow.of(
                        EntitySelectMenu.create("setup_log_select", EntitySelectMenu.SelectTarget.CHANNEL)
                                .setPlaceholder(t(guildId, "setup_wizard.select_channel_placeholder"))
                                .build()
                ),
                ActionRow.of(Button.secondary("setup_finish", t(guildId, "setup_wizard.btn_skip_finalize")))
        ).withAccentColor(0xE67E22);
    }

    private Container buildFinalStep(String guildId) {
        return Container.of(
                TextDisplay.of(t(guildId, "setup_wizard.complete_title")),
                TextDisplay.of(t(guildId, "setup_wizard.complete_description")),
                ActionRow.of(Button.primary("help_systems", t(guildId, "setup_wizard.btn_manage_systems")))
        ).withAccentColor(0x2ECC71);
    }
}
