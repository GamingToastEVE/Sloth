package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EmbedEditorCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public EmbedEditorCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    private EmbedBuilder getBuilderFromMessage(MessageEmbed embed) {
        if (embed == null) return new EmbedBuilder();
        return new EmbedBuilder(embed);
    }

    // Standard Editor-Buttons (ohne Verify Toggle)
    private List<ActionRow> getEditorActionRows() {
        List<ActionRow> rows = new ArrayList<>();

        // Reihe 1: Texte
        rows.add(ActionRow.of(
                Button.primary("embed_edit_title", "Title").withEmoji(Emoji.fromUnicode("📝")),
                Button.primary("embed_edit_desc", "Text").withEmoji(Emoji.fromUnicode("📄")),
                Button.secondary("embed_edit_footer", "Footer").withEmoji(Emoji.fromUnicode("🔻")),
                Button.secondary("embed_edit_author", "Author").withEmoji(Emoji.fromUnicode("👤"))
        ));

        // Reihe 2: Design
        rows.add(ActionRow.of(
                Button.secondary("embed_edit_color", "colour").withEmoji(Emoji.fromUnicode("🎨")),
                Button.secondary("embed_edit_image", "images").withEmoji(Emoji.fromUnicode("🖼️")),
                Button.primary("embed_add_field", "add field").withEmoji(Emoji.fromUnicode("➕")),
                Button.danger("embed_clear_fields", "delete field").withEmoji(Emoji.fromUnicode("🗑️"))
        ));

        // Reihe 3: Aktionen
        rows.add(ActionRow.of(
                Button.success("embed_publish_start", "Send").withEmoji(Emoji.fromUnicode("✅")),
                Button.primary("embed_save_db", "Save").withEmoji(Emoji.fromUnicode("💾"))
        ));

        return rows;
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("embed")) return;
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        if (subcommand.equals("delete") || subcommand.equals("load")) {
            String focusedOption = event.getFocusedOption().getName();
            if (focusedOption.equals("name")) {
                String guildId = event.getGuild().getId();
                String[] names = handler.getCustomEmbedNames(guildId).toArray(new String[0]);
                String typed = event.getFocusedOption().getValue();
                List<Choice> filtered = Stream.of(names)
                        .filter(word -> word.startsWith(typed))
                        .map(word -> new Choice(word, word))
                        .collect(Collectors.toList());

                event.replyChoices(filtered).queue();
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("embed")) return;
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ No permissions.").setEphemeral(true).queue();
            return;
        }
        event.deferReply().setEphemeral(true).queue();
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;
        String guildId = event.getGuild().getId();

        switch (subcommand) {
            case "create":
                handler.insertOrUpdateGlobalStatistic("embed create");
                EmbedBuilder eb = new EmbedBuilder();
                eb.setDescription("This is a preview. Use the buttons to edit the embed.");
                eb.setColor(Color.GRAY);
                event.getHook().editOriginalEmbeds(eb.build()).setComponents(getEditorActionRows()).queue();
                break;

            case "list":
                handler.insertOrUpdateGlobalStatistic("embed list");
                List<String> names = handler.getCustomEmbedNames(guildId);
                if (names.isEmpty()) {
                    event.getHook().editOriginal("📂 No saved Embeds found.").queue();
                } else {
                    event.getHook().editOriginal("📂 **Saved Embeds:**\n`" + String.join("`, `", names) + "`").queue();
                }
                break;

            case "delete":
                handler.insertOrUpdateGlobalStatistic("embed delete");
                String delName = event.getOption("name").getAsString();
                if (handler.deleteCustomEmbed(guildId, delName)) {
                    event.getHook().editOriginal("🗑️ Embed `" + delName + "` got deleted.").queue();
                } else {
                    event.getHook().editOriginal("❌ Embed `" + delName + "` not found.").queue();
                }
                break;

            case "load":
                handler.insertOrUpdateGlobalStatistic("embed load");
                String loadName = event.getOption("name").getAsString();
                String json = handler.getCustomEmbedData(guildId, loadName);
                if (json == null) {
                    event.getHook().editOriginal("❌ Embed `" + loadName + "` not found.").queue();
                    return;
                }
                try {
                    DataObject data = DataObject.fromJson(json);
                    EmbedBuilder loadedBuilder = jsonToEmbedBuilder(data);

                    event.getHook().editOriginalEmbeds(loadedBuilder.build())
                            .setComponents(getEditorActionRows())
                            .queue();
                } catch (Exception e) {
                    event.getHook().editOriginal("❌ Error loading Embed: " + e.getMessage()).queue();
                }
                break;
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("embed_")) return;

        MessageEmbed currentEmbed = event.getMessage().getEmbeds().isEmpty() ? null : event.getMessage().getEmbeds().get(0);
        EmbedBuilder builder = getBuilderFromMessage(currentEmbed);

        // --- Speichern Logik ---
        if (id.equals("embed_save_db")) {
            TextInput nameInput = TextInput.create("input_save_name", TextInputStyle.SHORT)
                    .setPlaceholder("z.B. welcome_message")
                    .setRequired(true)
                    .setMaxLength(100)
                    .build();

            event.replyModal(Modal.create("modal_embed_save", "Save embed").addComponents(Label.of("Embed Name:", nameInput)).build()).queue();
            return;
        }

        // --- Publish (Senden) Start: Frage nach Verify Button ---
        if (id.equals("embed_publish_start")) {
            // Prüfen ob Verify konfiguriert ist, um den Button überhaupt anzubieten
            boolean hasVerifyConfig = handler.isJustVerifyButton(event.getGuild().getId());

            List<Button> buttons = new ArrayList<>();

            if (hasVerifyConfig) {
                buttons.add(Button.success("embed_publish_choose_true", "With Verify Button").withEmoji(Emoji.fromUnicode("🔘")));
            }
            buttons.add(Button.primary("embed_publish_choose_false", hasVerifyConfig ? "Without Verify Button" : "Weiter (Kanal wählen)"));
            buttons.add(Button.secondary("embed_publish_cancel", "Cancel"));

            event.editComponents(ActionRow.of(buttons)).queue();
            return;
        }

        // --- Auswahl getroffen: Mit oder Ohne Verify ---
        if (id.startsWith("embed_publish_choose_")) {
            boolean withVerify = Boolean.parseBoolean(id.replace("embed_publish_choose_", ""));

            // Jetzt Kanal Auswahl anzeigen (Status in ID speichern)
            EntitySelectMenu channelSelect = EntitySelectMenu.create("embed_publish_finish_" + withVerify, EntitySelectMenu.SelectTarget.CHANNEL)
                    .setPlaceholder("choose a channel to send the embed to")
                    .setChannelTypes(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT, net.dv8tion.jda.api.entities.channel.ChannelType.NEWS)
                    .setMinValues(1).setMaxValues(1).build();

            Button cancelBtn = Button.secondary("embed_publish_cancel", "cancel");

            event.editComponents(ActionRow.of(channelSelect), ActionRow.of(cancelBtn)).queue();
            return;
        }

        // --- Publish Abbrechen ---
        if (id.equals("embed_publish_cancel")) {
            event.editComponents(getEditorActionRows()).queue();
            return;
        }

        // --- Normale Editor Logik (Modals) ---
        switch (id) {
            case "embed_edit_title":
                TextInput titleInput = TextInput.create("input_title", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null ? currentEmbed.getTitle() : "").setRequired(false).build();
                TextInput urlInput = TextInput.create("input_url", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null ? currentEmbed.getUrl() : "").setRequired(false).build();
                event.replyModal(Modal.create("modal_embed_title", "Title").addComponents(Label.of("Title: ", titleInput), Label.of("URL Input: ", urlInput)).build()).queue();
                break;

            case "embed_edit_desc":
                TextInput descInput = TextInput.create("input_desc", TextInputStyle.PARAGRAPH)
                        .setValue(currentEmbed != null ? currentEmbed.getDescription() : "").setMaxLength(4000).setRequired(true).build();

                event.replyModal(Modal.create("modal_embed_desc", "Description").addComponents(Label.of("Description: ", descInput)).build()).queue();
                break;

            case "embed_edit_footer":
                TextInput footerInput = TextInput.create("input_footer", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null && currentEmbed.getFooter() != null ? currentEmbed.getFooter().getText() : null).setRequired(false).build();
                event.replyModal(Modal.create("modal_embed_footer", "Footer").addComponents(Label.of("Footer: ", footerInput)).build()).queue();
                break;

            case "embed_edit_author":
                TextInput authorName = TextInput.create("input_author_name", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null && currentEmbed.getAuthor() != null ? currentEmbed.getAuthor().getName() : null).setRequired(false).build();
                event.replyModal(Modal.create("modal_embed_author", "Author").addComponents(Label.of("Author: ", authorName)).build()).queue();
                break;

            case "embed_edit_color":
                TextInput colorInput = TextInput.create("input_color", TextInputStyle.SHORT).setRequired(true).build();
                event.replyModal(Modal.create("modal_embed_color", "Colour").addComponents(Label.of("Colour: ", colorInput)).build()).queue();
                break;

            case "embed_edit_image":
                TextInput imgInput = TextInput.create("input_image", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null && currentEmbed.getImage() != null ? currentEmbed.getImage().getUrl() : null).setRequired(false).build();
                TextInput thumbInput = TextInput.create("input_thumb", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null && currentEmbed.getThumbnail() != null ? currentEmbed.getThumbnail().getUrl() : null).setRequired(false).build();
                event.replyModal(Modal.create("modal_embed_image", "Image").addComponents(Label.of("Image: ", imgInput), Label.of("Thumbnail: ", thumbInput)).build()).queue();
                break;

            case "embed_add_field":
                TextInput fName = TextInput.create("input_field_name", TextInputStyle.SHORT).setRequired(true).build();
                TextInput fValue = TextInput.create("input_field_value", TextInputStyle.PARAGRAPH).setRequired(true).build();
                TextInput fInline = TextInput.create("input_field_inline", TextInputStyle.SHORT).setValue("no").setRequired(true).build();
                event.replyModal(Modal.create("modal_embed_add_field", "field").addComponents(Label.of("Field Name: ", fName), Label.of("Field Content: ", fValue), Label.of("Inline True/False: ", fInline)).build()).queue();
                break;

            case "embed_clear_fields":
                if (builder.getFields().isEmpty()) {
                    event.reply("❌ No fields to remove!").setEphemeral(true).queue();
                    return;
                }
                builder.getFields().remove(builder.getFields().size() - 1);
                event.editMessageEmbeds(builder.build()).queue();
                break;
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();

        // Speichern Handler
        if (id.equals("modal_embed_save")) {
            event.deferReply().setEphemeral(true).queue();
            String name = event.getValue("input_save_name").getAsString();
            MessageEmbed embed = event.getMessage().getEmbeds().get(0);

            // Embed zu JSON
            DataObject json = embed.toData();

            handler.saveCustomEmbed(event.getGuild().getId(), name, json.toString());
            event.getHook().sendMessage("💾 Embed successfully saved as `" + name + "`!").queue();
            return;
        }

        if (!id.startsWith("modal_embed_")) return;

        MessageEmbed currentEmbed = event.getMessage().getEmbeds().get(0);
        EmbedBuilder builder = getBuilderFromMessage(currentEmbed);

        // Editor Handler
        switch (id) {
            case "modal_embed_title":
                String t = event.getValue("input_title").getAsString();
                String u = event.getValue("input_url").getAsString();
                builder.setTitle(t.isEmpty() ? null : t, u.isEmpty() ? null : u);
                break;
            case "modal_embed_desc":
                builder.setDescription(processLineBreaks(event.getValue("input_desc").getAsString()));
                break;
            case "modal_embed_footer":
                String f = event.getValue("input_footer").getAsString();
                builder.setFooter(f.isEmpty() ? null : f);
                break;
            case "modal_embed_author":
                String a = event.getValue("input_author_name").getAsString();
                builder.setAuthor(a.isEmpty() ? null : a);
                break;
            case "modal_embed_color":
                String c = event.getValue("input_color").getAsString();
                try {
                    if (ColorUtil.isValid(c)) builder.setColor(ColorUtil.toAwt(c));
                    else builder.setColor(Color.decode(c));
                } catch (Exception ignored) {}
                break;
            case "modal_embed_image":
                String i = event.getValue("input_image").getAsString();
                String th = event.getValue("input_thumb").getAsString();
                builder.setImage(i.isEmpty() ? null : i);
                builder.setThumbnail(th.isEmpty() ? null : th);
                break;
            case "modal_embed_add_field":
                String fn = event.getValue("input_field_name").getAsString();
                String fv = processLineBreaks(event.getValue("input_field_value").getAsString());
                boolean inline = event.getValue("input_field_inline").getAsString().toLowerCase().matches("^(ja|yes|true|y|j)$");
                builder.addField(fn, fv, inline);
                break;
        }
        event.editMessageEmbeds(builder.build()).queue();
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String id = event.getComponentId();
        if (id.startsWith("embed_publish_finish_")) {
            event.deferReply().setEphemeral(true).queue();
            boolean withVerify = Boolean.parseBoolean(id.replace("embed_publish_finish_", ""));

            MessageEmbed embedToSend = event.getMessage().getEmbeds().get(0);
            MessageChannel targetChannel = Objects.requireNonNull(event.getGuild()).getTextChannelById(event.getMentions().getChannels().get(0).getId());

            // Button erstellen, falls ausgewählt
            Button verifyButton;
            if (withVerify) {
                String guildId = event.getGuild().getId();
                if (handler.isJustVerifyButton(guildId)) {
                    String r1 = handler.getJustVerifyButtonRoleToGiveID(guildId);
                    String r2 = handler.getJustVerifyButtonRoleToRemoveID(guildId);
                    String label = handler.getJustVerifyButtonLabel(guildId);
                    String emoji = handler.getJustVerifyButtonEmojiID(guildId);
                    verifyButton = handler.createJustVerifyButton(r1, r2, label, emoji);
                } else {
                    verifyButton = null;
                    event.getHook().sendMessage("⚠️ Warning: No verify button found.").setEphemeral(true).queue();
                }
            } else {
                verifyButton = null;
            }

            // Senden (mit oder ohne Button)
            var action = targetChannel.sendMessageEmbeds(embedToSend);
            if (verifyButton != null) {
                action.addComponents(ActionRow.of(verifyButton));
            }

            action.queue(
                    s -> {
                        event.getHook().sendMessage("✅ Sent in " + targetChannel.getAsMention() + (verifyButton != null ? " (with Verify Button)" : "")).queue();
                        event.getMessage().editMessageComponents(getEditorActionRows()).queue();
                    },
                    e -> {
                        event.getHook().sendMessage("❌ Error sending embed: " + e.getMessage()).setEphemeral(true).queue();
                        event.getMessage().editMessageComponents(getEditorActionRows()).queue();
                    }
            );
        }
    }

    private EmbedBuilder jsonToEmbedBuilder(DataObject json) {
        EmbedBuilder eb = new EmbedBuilder();

        if (json.hasKey("title")) eb.setTitle(processLineBreaks(json.getString("title")), json.getString("url", null));
        if (json.hasKey("description")) eb.setDescription(processLineBreaks(json.getString("description")));
        if (json.hasKey("color")) eb.setColor(json.getInt("color"));
        if (json.hasKey("timestamp")) eb.setTimestamp(Instant.parse(json.getString("timestamp")));

        if (json.hasKey("footer")) {
            DataObject footer = json.getObject("footer");
            eb.setFooter(footer.getString("text"), footer.getString("icon_url", null));
        }

        if (json.hasKey("image")) eb.setImage(json.getObject("image").getString("url"));
        if (json.hasKey("thumbnail")) eb.setThumbnail(json.getObject("thumbnail").getString("url"));

        if (json.hasKey("author")) {
            DataObject author = json.getObject("author");
            eb.setAuthor(author.getString("name"), author.getString("url", null), author.getString("icon_url", null));
        }

        if (json.hasKey("fields")) {
            DataArray fields = json.getArray("fields");
            for (int i = 0; i < fields.length(); i++) {
                DataObject field = fields.getObject(i);
                eb.addField(field.getString("name"), field.getString("value"), field.getBoolean("inline", false));
            }
        }

        return eb;
    }

    private String getFormattingHelpText() {
        return "\n\n**ℹ️ Formatting Guide:**\n" +
                "• **bold text** - Use `**text**`\n" +
                "• *italic text* - Use `*text*`\n" +
                "• __underlined text__ - Use `__text__`\n" +
                "• ~~strikethrough~~ - Use `~~text~~`\n" +
                "• `inline code` - Use `` `text` ``\n" +
                "• [links](https://example.com) - Use `[text](url)`\n" +
                "\n*Note: Titles only support plain text, but descriptions and footers support all formatting.*";
    }

    /**
     * Validates if the text contains formatting characters and provides feedback
     */
    private String validateTextWithFormatting(String text, String fieldName, int maxLength) {
        if (text.length() > maxLength) {
            return "❌ " + fieldName + " must be " + maxLength + " characters or less!";
        }

        // Check if title contains formatting characters (since titles don't support formatting)
        if ("Title".equals(fieldName) && containsFormattingCharacters(text)) {
            return "⚠️ " + fieldName + " contains formatting characters. " +
                    "Discord embed titles only support plain text. " +
                    "Consider moving formatting to the description.";
        }

        return null; // No validation errors
    }

    /**
     * Checks if text contains Discord markdown formatting characters
     */
    private boolean containsFormattingCharacters(String text) {
        return text.contains("**") || text.contains("*") || text.contains("__") ||
                text.contains("~~") || text.contains("`") || text.contains("[");
    }

    public String processLineBreaks(String text) {
        if (text == null) return "";

        // 1. Replace literal "\r\n" (Windows style literal) with a real newline
        String result = text.replaceAll("\\\\r\\\\n", "\n");

        // 2. Replace literal "\n" (Unix style literal) with a real newline
        // We use "\\\\n" in regex to match a literal "\n" string
        result = result.replaceAll("\\\\n", "\n");

        return result;
    }
}
