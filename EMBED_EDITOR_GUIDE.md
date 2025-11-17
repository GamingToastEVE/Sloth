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
You can use either a URL, a local file path, or upload a file directly:

**Using a URL:**
```
/set-embed-image name:welcome-message image-url:https://example.com/banner.png
```

**Using a local file path:**
```
/set-embed-image name:welcome-message image-url:/path/to/image.png
```

**Using file upload (recommended):**
```
/set-embed-image name:welcome-message image-file:[upload file]
```

### Adding a Thumbnail
Same options as images:

**URL:**
```
/set-embed-thumbnail name:welcome-message thumbnail-url:https://example.com/logo.png
```

**Local file path:**
```
/set-embed-thumbnail name:welcome-message thumbnail-url:/home/user/logo.png
```

**File upload:**
```
/set-embed-thumbnail name:welcome-message thumbnail-file:[upload file]
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

## Image URLs and File Paths

You have three options for adding images to your embeds:

### 1. Direct URL
For images already hosted online:
```
/set-embed-image name:announcement image-url:https://cdn.example.com/image.png
```

**Good Examples:**
- `https://example.com/image.png`
- `https://cdn.discordapp.com/attachments/123/456/image.jpg`

### 2. Local File Path
For images stored on your computer or server:
```
/set-embed-image name:announcement image-url:/home/user/Pictures/banner.png
```

**Important Notes:**
- Use absolute file paths (full path from root)
- The bot must have read access to the file
- The file will be uploaded to Discord's CDN and the URL will be stored
- Supported formats: `.png`, `.jpg`, `.jpeg`, `.gif`, `.webp`

**Good Examples:**
- Linux/Mac: `/home/user/images/logo.png`
- Windows: `C:\Users\Username\Pictures\banner.jpg`

### 3. File Upload (Recommended)
Upload a file directly through Discord:
```
/set-embed-image name:announcement image-file:[click to upload]
```

**Advantages:**
- Most convenient method
- No need to worry about file paths
- Works from any device
- File is immediately uploaded to Discord's CDN

### Which Method to Use?

- **File Upload**: Best for most users, especially on desktop
- **URL**: Best when image is already hosted online
- **File Path**: Best for automated scripts or when running bot on a server with local images

## Image URLs

For images, thumbnails, and author icons, you can use:
- Direct URLs ending with image extensions
- Discord CDN URLs
- Local file paths (absolute paths)
- File attachments uploaded through Discord

**Tips:**
- File uploads are automatically converted to Discord CDN URLs
- Local file paths are uploaded and converted to URLs
- Ensure URLs are publicly accessible
- For local files, the bot needs read permission
- Maximum file size: 8MB (Discord limit)

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

**Problem: "Failed to upload file" or "File not found"**
- Solution: 
  - For local file paths: Use absolute paths (full path from root)
  - Ensure the file exists at the specified location
  - Check that the bot has read permissions for the file
  - Verify the file is a valid image format
  - Try using file upload instead of file path

**Problem: Image shows as broken/not loading**
- Solution: 
  - If using a local file path, it's uploaded to Discord CDN automatically
  - Wait a moment for Discord to process the upload
  - Ensure the file is under 8MB (Discord's limit)
  - Try using a URL instead

**Problem: Can't send embed to channel**
- Solution: Make sure the bot has permission to send messages in that channel

## Support

For additional help or to report issues, please visit:
- Support Server: https://discord.gg/dQT53fD8M5
- Discord: **gamingtoasti**
