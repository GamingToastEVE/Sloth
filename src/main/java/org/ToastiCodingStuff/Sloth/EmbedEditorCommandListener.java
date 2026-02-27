package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
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
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import org.json.JSONObject;

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

    private EmbedBuilder getBuilderFromMessage(MessageEmbed embed) {
        if (embed == null) return new EmbedBuilder();
        return new EmbedBuilder(embed);
    }

    // Standard Editor-Buttons (ohne Verify Toggle)
    private List<ActionRow> getEditorActionRows(EmbedBuilder builder, DataObject obj, String guildId) {
        List<ActionRow> rows = new ArrayList<>();

        if (obj != null) {
            if (obj.hasKey("fields")) {
                DataArray fields = obj.getArray("fields");
                StringSelectMenu.Builder fieldSelect = StringSelectMenu.create("embed_field_select")
                        .setPlaceholder(t(guildId, "embed_editor.field_select_placeholder"))
                        .setMinValues(1)
                        .setMaxValues(1);
                for (int i = 0; i < fields.length(); i++) {
                    DataObject field = fields.getObject(i);
                    String name = field.getString("name");
                    fieldSelect.addOption(name, String.valueOf(i));
                }
                rows.add(ActionRow.of(fieldSelect.build()));
            }
        }

        if (!builder.getFields().isEmpty()) {
            StringSelectMenu.Builder fieldSelect = StringSelectMenu.create("embed_field_select")
                    .setPlaceholder(t(guildId, "embed_editor.field_select_placeholder"))
                    .setMinValues(1)
                    .setMaxValues(1);
            List<MessageEmbed.Field> fields = builder.getFields();
            for (int i = 0; i < fields.size(); i++) {
                MessageEmbed.Field field = fields.get(i);
                String name = field.getName();
                fieldSelect.addOption(name, String.valueOf(i));
            }
            rows.add(ActionRow.of(fieldSelect.build()));
        }


        // Reihe 1: Texte
        rows.add(ActionRow.of(
                Button.primary("embed_edit_title", t(guildId, "embed_editor.btn_title")).withEmoji(Emoji.fromUnicode("📝")),
                Button.primary("embed_edit_desc", t(guildId, "embed_editor.btn_text")).withEmoji(Emoji.fromUnicode("📄")),
                Button.secondary("embed_edit_footer", t(guildId, "embed_editor.btn_footer")).withEmoji(Emoji.fromUnicode("🔻")),
                Button.secondary("embed_edit_author", t(guildId, "embed_editor.btn_author")).withEmoji(Emoji.fromUnicode("👤"))
        ));

        // Reihe 2: Design
        rows.add(ActionRow.of(
                Button.secondary("embed_edit_color", t(guildId, "embed_editor.btn_colour")).withEmoji(Emoji.fromUnicode("🎨")),
                Button.secondary("embed_edit_image", t(guildId, "embed_editor.btn_images")).withEmoji(Emoji.fromUnicode("🖼️")),
                Button.primary("embed_add_field", t(guildId, "embed_editor.btn_add_field")).withEmoji(Emoji.fromUnicode("➕")),
                Button.danger("embed_clear_fields", t(guildId, "embed_editor.btn_delete_field")).withEmoji(Emoji.fromUnicode("🗑️"))
        ));

        // Reihe 3: Aktionen
        rows.add(ActionRow.of(
                Button.success("embed_publish_start", t(guildId, "embed_editor.btn_send")).withEmoji(Emoji.fromUnicode("✅")),
                Button.primary("embed_save_db", t(guildId, "embed_editor.btn_save")).withEmoji(Emoji.fromUnicode("💾"))
        ));

        System.out.println("Generated " + rows.size() + " action rows for embed editor.");

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
            String gid = event.getGuild().getId();
            event.reply(t(gid, "embed_editor.no_permission")).setEphemeral(true).queue();
            return;
        }
        event.deferReply().setEphemeral(true).queue();
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;
        String guildId = event.getGuild().getId();

        switch (subcommand) {
            case "create":
                EmbedBuilder eb = new EmbedBuilder();
                eb.setDescription(t(guildId, "embed_editor.preview_description"));
                eb.setColor(Color.GRAY);
                event.getHook().editOriginalEmbeds(eb.build()).setComponents(getEditorActionRows(eb, null, guildId)).queue();
                break;

            case "list":
                List<String> names = handler.getCustomEmbedNames(guildId);
                if (names.isEmpty()) {
                    event.getHook().editOriginal(t(guildId, "embed_editor.no_saved_embeds")).queue();
                } else {
                    event.getHook().editOriginal(t(guildId, "embed_editor.saved_embeds_list", String.join("`, `", names))).queue();
                }
                break;

            case "delete":
                String delName = event.getOption("name").getAsString();
                if (handler.deleteCustomEmbed(guildId, delName)) {
                    event.getHook().editOriginal(t(guildId, "embed_editor.embed_deleted", delName)).queue();
                } else {
                    event.getHook().editOriginal(t(guildId, "embed_editor.embed_not_found", delName)).queue();
                }
                break;

            case "load":
                String loadName = event.getOption("name").getAsString();
                String json = handler.getCustomEmbedData(guildId, loadName);
                if (json == null) {
                    event.getHook().editOriginal(t(guildId, "embed_editor.embed_not_found", loadName)).queue();
                    return;
                }
                try {
                    DataObject data = DataObject.fromJson(json);
                    EmbedBuilder loadedBuilder = jsonToEmbedBuilder(data);

                    event.getHook().editOriginalEmbeds(loadedBuilder.build())
                            .setComponents(getEditorActionRows(null, data, guildId))
                            .queue();
                } catch (Exception e) {
                    event.getHook().editOriginal(t(guildId, "embed_editor.error_loading", e.getMessage())).queue();
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

        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;

        // --- Speichern Logik ---
        if (id.equals("embed_save_db")) {
            TextInput nameInput = TextInput.create("input_save_name", TextInputStyle.SHORT)
                    .setPlaceholder(t(guildId, "embed_editor.modal_save_placeholder"))
                    .setRequired(true)
                    .setMaxLength(100)
                    .build();

            event.replyModal(Modal.create("modal_embed_save", t(guildId, "embed_editor.modal_save_title")).addComponents(Label.of(t(guildId, "embed_editor.modal_save_label"), nameInput)).build()).queue();
            return;
        }

        // --- Publish (Senden) Start: Frage nach Verify Button ---
        if (id.equals("embed_publish_start")) {
            // Prüfen ob Verify konfiguriert ist, um den Button überhaupt anzubieten
            boolean hasVerifyConfig = handler.isJustVerifyButton(event.getGuild().getId());

            List<Button> buttons = new ArrayList<>();

            if (hasVerifyConfig) {
                buttons.add(Button.success("embed_publish_choose_true", t(guildId, "embed_editor.btn_with_verify")).withEmoji(Emoji.fromUnicode("🔘")));
            }
            buttons.add(Button.primary("embed_publish_choose_false", hasVerifyConfig ? t(guildId, "embed_editor.btn_without_verify") : t(guildId, "embed_editor.btn_continue_channel")));
            buttons.add(Button.secondary("embed_publish_cancel", t(guildId, "embed_editor.btn_cancel")));

            event.editComponents(ActionRow.of(buttons)).queue();
            return;
        }

        // --- Auswahl getroffen: Mit oder Ohne Verify ---
        if (id.startsWith("embed_publish_choose_")) {
            boolean withVerify = Boolean.parseBoolean(id.replace("embed_publish_choose_", ""));

            // Jetzt Kanal Auswahl anzeigen (Status in ID speichern)
            EntitySelectMenu channelSelect = EntitySelectMenu.create("embed_publish_finish_" + withVerify, EntitySelectMenu.SelectTarget.CHANNEL)
                    .setPlaceholder(t(guildId, "embed_editor.channel_select_placeholder"))
                    .setChannelTypes(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT, net.dv8tion.jda.api.entities.channel.ChannelType.NEWS)
                    .setMinValues(1).setMaxValues(1).build();

            Button cancelBtn = Button.secondary("embed_publish_cancel", t(guildId, "embed_editor.btn_cancel"));

            event.editComponents(ActionRow.of(channelSelect), ActionRow.of(cancelBtn)).queue();
            return;
        }

        // --- Publish Abbrechen ---
        if (id.equals("embed_publish_cancel")) {
            event.editComponents(getEditorActionRows(builder, null, guildId)).queue();
            return;
        }

        // --- Normale Editor Logik (Modals) ---
        switch (id) {
            case "embed_edit_title":
                TextInput titleInput = TextInput.create("input_title", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null ? currentEmbed.getTitle() : "").setRequired(false).build();
                TextInput urlInput = TextInput.create("input_url", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null ? currentEmbed.getUrl() : "").setRequired(false).build();
                event.replyModal(Modal.create("modal_embed_title", t(guildId, "embed_editor.modal_title")).addComponents(Label.of(t(guildId, "embed_editor.label_title"), titleInput), Label.of(t(guildId, "embed_editor.label_url"), urlInput)).build()).queue();
                break;

            case "embed_edit_desc":
                TextInput descInput = TextInput.create("input_desc", TextInputStyle.PARAGRAPH)
                        .setValue(currentEmbed != null ? currentEmbed.getDescription() : "").setMaxLength(4000).setRequired(true).build();

                event.replyModal(Modal.create("modal_embed_desc", t(guildId, "embed_editor.modal_description")).addComponents(Label.of(t(guildId, "embed_editor.label_description"), descInput)).build()).queue();
                break;

            case "embed_edit_footer":
                TextInput footerInput = TextInput.create("input_footer", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null && currentEmbed.getFooter() != null ? currentEmbed.getFooter().getText() : null).setRequired(false).build();
                event.replyModal(Modal.create("modal_embed_footer", t(guildId, "embed_editor.modal_footer")).addComponents(Label.of(t(guildId, "embed_editor.label_footer"), footerInput)).build()).queue();
                break;

            case "embed_edit_author":
                TextInput authorName = TextInput.create("input_author_name", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null && currentEmbed.getAuthor() != null ? currentEmbed.getAuthor().getName() : null).setRequired(false).build();
                event.replyModal(Modal.create("modal_embed_author", t(guildId, "embed_editor.modal_author")).addComponents(Label.of(t(guildId, "embed_editor.label_author"), authorName)).build()).queue();
                break;

            case "embed_edit_color":
                TextInput colorInput = TextInput.create("input_color", TextInputStyle.SHORT).setRequired(true).build();
                event.replyModal(Modal.create("modal_embed_color", t(guildId, "embed_editor.modal_colour")).addComponents(Label.of(t(guildId, "embed_editor.label_colour"), colorInput)).build()).queue();
                break;

            case "embed_edit_image":
                TextInput imgInput = TextInput.create("input_image", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null && currentEmbed.getImage() != null ? currentEmbed.getImage().getUrl() : null).setRequired(false).build();
                TextInput thumbInput = TextInput.create("input_thumb", TextInputStyle.SHORT)
                        .setValue(currentEmbed != null && currentEmbed.getThumbnail() != null ? currentEmbed.getThumbnail().getUrl() : null).setRequired(false).build();
                event.replyModal(Modal.create("modal_embed_image", t(guildId, "embed_editor.modal_image")).addComponents(Label.of(t(guildId, "embed_editor.label_image"), imgInput), Label.of(t(guildId, "embed_editor.label_thumbnail"), thumbInput)).build()).queue();
                break;

            case "embed_add_field":
                TextInput fName = TextInput.create("input_field_name", TextInputStyle.SHORT).setRequired(true).build();
                TextInput fValue = TextInput.create("input_field_value", TextInputStyle.PARAGRAPH).setRequired(true).build();
                TextInput fInline = TextInput.create("input_field_inline", TextInputStyle.SHORT).setValue("no").setRequired(true).build();
                event.replyModal(Modal.create("modal_embed_add_field", t(guildId, "embed_editor.modal_field")).addComponents(Label.of(t(guildId, "embed_editor.label_field_name"), fName), Label.of(t(guildId, "embed_editor.label_field_content"), fValue), Label.of(t(guildId, "embed_editor.label_field_inline"), fInline)).build()).queue();
                break;

            case "embed_clear_fields":
                if (builder.getFields().isEmpty()) {
                    event.reply(t(guildId, "embed_editor.no_fields_to_remove")).setEphemeral(true).queue();
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
            String guildId = event.getGuild() != null ? event.getGuild().getId() : null;

            // Embed zu JSON
            DataObject json = embed.toData();

            handler.saveCustomEmbed(event.getGuild().getId(), name, json.toString());
            event.getHook().sendMessage(t(guildId, "embed_editor.save_success", name)).queue();
            return;
        }

        if (!id.startsWith("modal_embed_")) return;

        MessageEmbed currentEmbed = event.getMessage().getEmbeds().get(0);
        EmbedBuilder builder = getBuilderFromMessage(currentEmbed);
        String guildId2 = event.getGuild() != null ? event.getGuild().getId() : null;

        if (id.startsWith("modal_embed_edit_field_")) {
            String indexStr = id.replace("modal_embed_edit_field_", "");
            int fieldIndex;
            try {
                fieldIndex = Integer.parseInt(indexStr);
            } catch (NumberFormatException e) {
                event.reply(t(guildId2, "embed_editor.invalid_field_index")).setEphemeral(true).queue();
                return;
            }
            if (fieldIndex < 0 || fieldIndex >= builder.getFields().size()) {
                event.reply(t(guildId2, "embed_editor.field_index_out_of_bounds")).setEphemeral(true).queue();
                return;
            }

            if (event.getValue("input_field_name") == null && event.getValue("input_field_value") == null && event.getValue("input_field_inline") == null) {
                builder.getFields().remove(fieldIndex);
                event.editMessageEmbeds(builder.build()).queue();
                event.getHook().editOriginalComponents(getEditorActionRows(builder, null, guildId2)).queue();
                return;
            }
            String fn = builder.getFields().get(fieldIndex).getName();
            if (event.getValue("input_field_name") != null) {
                fn = event.getValue("input_field_name").getAsString();
            }
            String fv = builder.getFields().get(fieldIndex).getName();
            if (event.getValue("input_field_value") != null) {
                fv = event.getValue("input_field_value").getAsString();
            }
            boolean inline = builder.getFields().get(fieldIndex).isInline();
            if (event.getValue("input_field_inline") != null) {
                inline = event.getValue("input_field_inline").getAsString().toLowerCase().matches("^(ja|yes|true|y|j)$");
            }
            builder.getFields().set(fieldIndex, new MessageEmbed.Field(fn, fv, inline));
            event.editMessageEmbeds(builder.build()).queue();
            event.getHook().editOriginalComponents(getEditorActionRows(builder, null, guildId2)).queue();
            return;
        }

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
        event.getHook().editOriginalComponents(getEditorActionRows(builder, null, guildId2)).queue();
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String id = event.getComponentId();
        if (id.startsWith("embed_publish_finish_")) {
            event.deferReply().setEphemeral(true).queue();
            boolean withVerify = Boolean.parseBoolean(id.replace("embed_publish_finish_", ""));

            MessageEmbed embedToSend = event.getMessage().getEmbeds().get(0);
            EmbedBuilder builder = getBuilderFromMessage(embedToSend);
            MessageChannel targetChannel = Objects.requireNonNull(event.getGuild()).getTextChannelById(event.getMentions().getChannels().get(0).getId());
            String guildId = event.getGuild().getId();

            // Button erstellen, falls ausgewählt
            Button verifyButton;
            if (withVerify) {
                if (handler.isJustVerifyButton(guildId)) {
                    String r1 = handler.getJustVerifyButtonRoleToGiveID(guildId);
                    String r2 = handler.getJustVerifyButtonRoleToRemoveID(guildId);
                    String label = handler.getJustVerifyButtonLabel(guildId);
                    String emoji = handler.getJustVerifyButtonEmojiID(guildId);
                    verifyButton = handler.createJustVerifyButton(r1, r2, label, emoji);
                } else {
                    verifyButton = null;
                    event.getHook().sendMessage(t(guildId, "embed_editor.no_verify_button_warning")).setEphemeral(true).queue();
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
                        String msg = verifyButton != null ?
                                t(guildId, "embed_editor.sent_success_with_verify", targetChannel.getAsMention()) :
                                t(guildId, "embed_editor.sent_success", targetChannel.getAsMention());
                        event.getHook().sendMessage(msg).queue();
                        event.getMessage().editMessageComponents(getEditorActionRows(builder, null, guildId)).queue();
                    },
                    e -> {
                        event.getHook().sendMessage(t(guildId, "embed_editor.error_sending", e.getMessage())).setEphemeral(true).queue();
                        event.getMessage().editMessageComponents(getEditorActionRows(builder, null, guildId)).queue();
                    }
            );
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.equals("embed_field_select")) return;

        MessageEmbed currentEmbed = event.getMessage().getEmbeds().isEmpty() ? null : event.getMessage().getEmbeds().get(0);
        EmbedBuilder builder = getBuilderFromMessage(currentEmbed);
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;

        // Feld löschen
        int fieldIndex = Integer.parseInt(event.getValues().get(0));
        if (fieldIndex >= 0 && fieldIndex < builder.getFields().size()) {
            Modal modal = Modal.create("modal_embed_edit_field_" + fieldIndex, t(guildId, "embed_editor.modal_edit_field_title"))
                    .addComponents(
                            Label.of(t(guildId, "embed_editor.label_field_name"), TextInput.create("input_field_name", TextInputStyle.SHORT)
                                    .setValue(builder.getFields().get(fieldIndex).getName())
                                    .setRequired(false)
                                    .build()),
                            Label.of(t(guildId, "embed_editor.label_field_content"), TextInput.create("input_field_value", TextInputStyle.PARAGRAPH)
                                    .setValue(builder.getFields().get(fieldIndex).getValue())
                                    .setRequired(false)
                                    .build()),
                            Label.of(t(guildId, "embed_editor.label_field_inline"), TextInput.create("input_field_inline", TextInputStyle.SHORT)
                                    .setValue(builder.getFields().get(fieldIndex).isInline() ? "yes" : "no")
                                    .setRequired(false)
                                    .build())
                    ).build();
            event.replyModal(modal).queue();
        } else {
            event.reply(t(guildId, "embed_editor.invalid_field_selected")).setEphemeral(true).queue();
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
