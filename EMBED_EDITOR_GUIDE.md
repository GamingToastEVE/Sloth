# Embed Editor System Guide

The Embed Editor system allows you to create, customize, preview, and send rich Discord embeds through your server. This guide will walk you through all the features.

## Quick Start

### Creating Your First Embed

1. **Create a Basic Embed**
   ```
   /create-embed name:welcome-message
   ```
   This will open a modal where you can enter:
   - **Title**: The main heading of your embed (optional)
   - **Description**: The main content of your embed (required)
   - **Color**: Choose a color (blue, red, green, #FF0000, etc.)
   - **Footer**: Footer text at the bottom (optional)

2. **Preview Your Embed**
   ```
   /preview-embed name:welcome-message
   ```
   This shows you exactly how the embed will look before sending it.

3. **Send Your Embed**
   ```
   /send-embed name:welcome-message
   ```
   Or send to a specific channel:
   ```
   /send-embed name:welcome-message channel:#announcements
   ```

## Advanced Customization

### Adding an Author Section
```
/set-embed-author name:welcome-message author-name:"Server Team" author-icon-url:https://example.com/icon.png
```

### Adding an Image
```
/set-embed-image name:welcome-message image-url:https://example.com/banner.png
```

### Adding a Thumbnail
```
/set-embed-thumbnail name:welcome-message thumbnail-url:https://example.com/logo.png
```

### Adding a Timestamp
```
/set-embed-timestamp name:welcome-message enabled:true
```

## Managing Your Embeds

### List All Embeds
```
/list-embeds
```
Shows all saved embeds for your server.

### Edit an Existing Embed
```
/edit-embed name:welcome-message
```
Opens a modal with the current values pre-filled.

### Delete an Embed
```
/delete-embed name:welcome-message
```
Permanently removes the embed from the database.

## Color Options

You can use named colors or hex codes:

**Named Colors:**
- `red`, `blue`, `green`, `yellow`, `orange`, `pink`, `cyan`, `magenta`, `white`, `black`, `gray`/`grey`

**Hex Codes:**
- `#FF0000` (red)
- `#00FF00` (green)
- `#0000FF` (blue)
- Any valid hex color code

## Image URLs

For images, thumbnails, and author icons, you need direct URLs to image files. These should end with `.png`, `.jpg`, `.jpeg`, or `.gif`.

**Good Examples:**
- `https://example.com/image.png`
- `https://cdn.discordapp.com/attachments/123/456/image.jpg`

**Tips:**
- Use Discord's CDN for reliable hosting
- Upload images to Discord, right-click, and "Copy Link"
- Ensure URLs are publicly accessible

## Best Practices

1. **Use Descriptive Names**: Name your embeds clearly (e.g., `welcome-message`, `server-rules`, `announcement-template`)

2. **Preview Before Sending**: Always preview your embed with `/preview-embed` before sending to ensure it looks correct

3. **Organize Your Embeds**: Keep a list of your embed names for easy reference

4. **Test in a Private Channel**: Create and test embeds in a private channel before using them publicly

5. **Keep It Clean**: Delete unused embeds with `/delete-embed` to keep your embed list manageable

## Example Use Cases

### Welcome Message
```
/create-embed name:welcome
Title: Welcome to Our Server! 🎉
Description: Thanks for joining! Please read the rules in #rules and introduce yourself in #introductions.
Color: blue
Footer: Enjoy your stay!

/set-embed-thumbnail name:welcome thumbnail-url:https://example.com/server-logo.png
/set-embed-timestamp name:welcome enabled:true
```

### Server Rules
```
/create-embed name:rules
Title: Server Rules
Description: 
1. Be respectful to all members
2. No spam or self-promotion
3. Keep content appropriate
4. Follow Discord's ToS
Color: red
Footer: Breaking rules will result in warnings or bans
```

### Announcement Template
```
/create-embed name:announcement
Title: 📢 Important Announcement
Description: [Your announcement here]
Color: #FFD700
Footer: Posted by the Admin Team

/set-embed-author name:announcement author-name:"Admin Team" author-icon-url:https://example.com/admin-icon.png
/set-embed-timestamp name:announcement enabled:true
```

## Permissions

To use the Embed Editor system, you need the **Manage Server** permission in your Discord server.

## Limitations

- Embed names must be unique within your server
- Maximum embed title length: 256 characters
- Maximum description length: 4,000 characters
- Maximum footer length: 2,048 characters
- Images, thumbnails, and icons must be valid image URLs

## Troubleshooting

**Problem: "An embed with this name already exists"**
- Solution: Use `/list-embeds` to see existing names, or use `/edit-embed` to modify the existing one

**Problem: "Failed to create embed"**
- Solution: Check that your description is not empty and all URLs are valid

**Problem: Image not showing**
- Solution: Ensure the URL is a direct link to an image file and is publicly accessible

**Problem: Can't send embed to channel**
- Solution: Make sure the bot has permission to send messages in that channel

## Support

For additional help or to report issues, please visit:
- Support Server: https://discord.gg/dQT53fD8M5
- Discord: **gamingtoasti**
