#!/bin/bash

echo "========================================"
echo "Delta Bot Web Dashboard Demo"
echo "========================================"
echo ""

echo "🔧 Building the application..."
./gradlew build --quiet

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    echo ""
    
    echo "📋 Setup Instructions:"
    echo "1. Copy .env.example to .env and configure with your Discord credentials:"
    echo "   - TOKEN=your_discord_bot_token"
    echo "   - DISCORD_CLIENT_ID=your_application_client_id"  
    echo "   - DISCORD_CLIENT_SECRET=your_application_client_secret"
    echo ""
    
    echo "2. Set up Discord OAuth2 redirect URI:"
    echo "   - Go to https://discord.com/developers/applications"
    echo "   - Select your application"
    echo "   - In OAuth2 settings, add redirect URI: http://localhost:8080/login/oauth2/code/discord"
    echo ""
    
    echo "🚀 To start the application, run:"
    echo "   ./gradlew run"
    echo ""
    
    echo "🌐 The web dashboard will be available at:"
    echo "   http://localhost:8080"
    echo ""
    
    echo "📱 Features available in the web dashboard:"
    echo "   • Discord Single Sign-On authentication"
    echo "   • View server statistics (today, weekly, custom date)"
    echo "   • Browse and manage support tickets"
    echo "   • Configure ticket system settings"
    echo "   • Adjust warning system parameters"
    echo "   • Real-time data from Discord bot database"
    echo ""
    
    echo "✨ The Discord bot and web server run simultaneously!"
else
    echo "❌ Build failed. Please check the error messages above."
fi

echo "========================================"