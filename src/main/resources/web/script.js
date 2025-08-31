// Modern JavaScript for Delta Bot Dashboard

class DeltaDashboard {
    constructor() {
        this.currentSection = 'overview';
        this.user = null;
        this.guilds = [];
        this.init();
    }

    async init() {
        this.setupNavigation();
        this.setupAuth();
        await this.checkAuthStatus();
        console.log('Delta Dashboard initialized');
    }

    setupNavigation() {
        const navLinks = document.querySelectorAll('.nav-link');
        navLinks.forEach(link => {
            link.addEventListener('click', (e) => {
                e.preventDefault();
                const sectionId = link.getAttribute('href').substring(1);
                this.showSection(sectionId);
                
                // Update active nav link
                navLinks.forEach(l => l.classList.remove('active'));
                link.classList.add('active');
            });
        });
    }

    setupAuth() {
        const loginBtn = document.getElementById('loginBtn');
        const logoutBtn = document.getElementById('logoutBtn');

        if (loginBtn) {
            loginBtn.addEventListener('click', () => {
                window.location.href = '/auth/login';
            });
        }

        if (logoutBtn) {
            logoutBtn.addEventListener('click', () => {
                window.location.href = '/auth/logout';
            });
        }
    }

    async checkAuthStatus() {
        try {
            const response = await fetch('/api/user');
            if (response.ok) {
                this.user = await response.json();
                this.showUserInfo();
                await this.loadGuilds();
            } else {
                this.showLoginButton();
            }
        } catch (error) {
            console.error('Error checking auth status:', error);
            this.showLoginButton();
        }
    }

    showUserInfo() {
        const userInfo = document.getElementById('userInfo');
        const loginSection = document.getElementById('loginSection');
        const userAvatar = document.getElementById('userAvatar');
        const userName = document.getElementById('userName');

        if (userInfo && loginSection && this.user) {
            // Set user avatar
            if (userAvatar && this.user.avatar) {
                userAvatar.src = this.user.avatar;
                userAvatar.style.display = 'block';
            }

            // Set username
            if (userName) {
                userName.textContent = this.user.username;
            }

            // Show user info, hide login button
            userInfo.style.display = 'flex';
            loginSection.style.display = 'none';
        }
    }

    showLoginButton() {
        const userInfo = document.getElementById('userInfo');
        const loginSection = document.getElementById('loginSection');

        if (userInfo && loginSection) {
            userInfo.style.display = 'none';
            loginSection.style.display = 'block';
        }
    }

    async loadGuilds() {
        try {
            const response = await fetch('/api/guilds');
            if (response.ok) {
                const data = await response.json();
                this.guilds = data.guilds || [];
                this.updateGuildsList();
            } else if (response.status === 401) {
                this.showLoginButton();
            }
        } catch (error) {
            console.error('Error loading guilds:', error);
            this.showNotification('Failed to load guilds', 'error');
        }
    }

    updateGuildsList() {
        // Update the overview section to show actual guilds
        const overviewSection = document.getElementById('overview');
        if (overviewSection && this.guilds.length > 0) {
            let guildsList = '<h3>Your Moderated Servers</h3><div class="guilds-list">';
            this.guilds.forEach(guild => {
                const iconUrl = guild.icon || '/default-guild-icon.png';
                guildsList += `
                    <div class="guild-item" data-guild-id="${guild.id}">
                        <img class="guild-icon" src="${iconUrl}" alt="${guild.name}" />
                        <span class="guild-name">${guild.name}</span>
                    </div>
                `;
            });
            guildsList += '</div>';
            
            // Insert after the cards
            const cards = overviewSection.querySelector('.cards');
            if (cards && !overviewSection.querySelector('.guilds-list')) {
                cards.insertAdjacentHTML('afterend', guildsList);
            }
        }
    }

    showSection(sectionId) {
        // Hide all sections
        const sections = document.querySelectorAll('.section');
        sections.forEach(section => {
            section.classList.remove('active');
        });

        // Show selected section
        const targetSection = document.getElementById(sectionId);
        if (targetSection) {
            targetSection.classList.add('active');
            this.currentSection = sectionId;
            
            // Load data for specific sections
            if (sectionId === 'statistics') {
                this.loadStatistics();
            }
        }
    }

    async loadStatistics() {
        try {
            const todayElement = document.getElementById('today-stats');
            const weeklyElement = document.getElementById('weekly-stats');
            
            if (!todayElement || !weeklyElement) return;

            // Show loading state
            todayElement.innerHTML = '<div class="loading">Loading today\'s statistics...</div>';
            weeklyElement.innerHTML = '<div class="loading">Loading weekly statistics...</div>';

            // Use first available guild or demo
            const guildId = this.guilds.length > 0 ? this.guilds[0].id : 'demo';
            
            // Fetch statistics from API
            const response = await fetch(`/api/statistics?guildId=${guildId}`);
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            
            // Update UI with statistics
            this.displayStatistics(todayElement, data.today, 'today');
            this.displayStatistics(weeklyElement, data.weekly, 'weekly');
            
        } catch (error) {
            console.error('Error loading statistics:', error);
            this.showStatisticsError();
        }
    }

    displayStatistics(element, statsText, type) {
        if (!statsText || statsText.includes('No statistics')) {
            element.innerHTML = `<div class="no-data">No ${type} statistics available yet.<br><small>Statistics will appear once the bot starts collecting data.</small></div>`;
            return;
        }
        
        // Process and format the statistics text
        const formattedStats = this.formatStatisticsText(statsText);
        element.innerHTML = formattedStats;
    }

    formatStatisticsText(text) {
        // Convert plain text statistics to formatted HTML
        if (!text) return '<div class="no-data">No data available</div>';
        
        // Replace specific patterns to make them more readable
        let formatted = text
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>') // Bold text
            .replace(/🔸/g, '<span class="stat-icon">⚠️</span>') // Warning icon
            .replace(/🦶/g, '<span class="stat-icon">👢</span>') // Kick icon  
            .replace(/🔨/g, '<span class="stat-icon">🔨</span>') // Ban icon
            .replace(/🎫/g, '<span class="stat-icon">🎫</span>') // Ticket icon
            .replace(/🛡️/g, '<span class="stat-icon">🛡️</span>') // Shield icon
            .replace(/\n/g, '<br>'); // Line breaks

        return `<div class="formatted-stats">${formatted}</div>`;
    }

    showStatisticsError() {
        const todayElement = document.getElementById('today-stats');
        const weeklyElement = document.getElementById('weekly-stats');
        
        const errorMessage = '<div class="error-message">Unable to load statistics.<br><small>Please try again later.</small></div>';
        
        if (todayElement) todayElement.innerHTML = errorMessage;
        if (weeklyElement) weeklyElement.innerHTML = errorMessage;
    }

    // Method to refresh data
    refreshData() {
        if (this.currentSection === 'statistics') {
            this.loadStatistics();
        }
        console.log('Data refreshed');
    }

    // Method to show notifications
    showNotification(message, type = 'info') {
        const notification = document.createElement('div');
        notification.className = `notification notification-${type}`;
        notification.textContent = message;
        
        // Add styles
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 1rem 1.5rem;
            border-radius: 8px;
            color: white;
            font-weight: 500;
            z-index: 1000;
            transform: translateX(100%);
            transition: transform 0.3s ease-in-out;
        `;
        
        // Set background color based on type
        switch(type) {
            case 'success': notification.style.background = 'var(--success-color)'; break;
            case 'error': notification.style.background = 'var(--danger-color)'; break;
            case 'warning': notification.style.background = 'var(--warning-color)'; break;
            default: notification.style.background = 'var(--primary-color)';
        }
        
        document.body.appendChild(notification);
        
        // Animate in
        setTimeout(() => notification.style.transform = 'translateX(0)', 100);
        
        // Remove after 5 seconds
        setTimeout(() => {
            notification.style.transform = 'translateX(100%)';
            setTimeout(() => notification.remove(), 300);
        }, 5000);
    }
}

// Global function for navigation (called from HTML)
function showSection(sectionId) {
    if (window.dashboard) {
        window.dashboard.showSection(sectionId);
    }
}

// Initialize dashboard when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    window.dashboard = new DeltaDashboard();
    
    // Add some demo functionality
    console.log('🤖 Delta Bot Dashboard loaded successfully!');
    
    // Optional: Auto-refresh statistics every 5 minutes
    setInterval(() => {
        if (window.dashboard.currentSection === 'statistics') {
            window.dashboard.refreshData();
        }
    }, 300000); // 5 minutes
});

// Add some CSS for dynamic elements
const style = document.createElement('style');
style.textContent = `
    .formatted-stats {
        line-height: 1.6;
    }
    
    .stat-icon {
        display: inline-block;
        margin-right: 0.5rem;
    }
    
    .no-data {
        text-align: center;
        color: var(--text-muted);
        font-style: italic;
        padding: 2rem;
    }
    
    .error-message {
        text-align: center;
        color: var(--danger-color);
        padding: 2rem;
    }
    
    .notification {
        box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
    }

    .user-section {
        margin-left: auto;
    }

    .user-info {
        display: flex;
        align-items: center;
        gap: 0.5rem;
    }

    .user-avatar {
        width: 32px;
        height: 32px;
        border-radius: 50%;
    }

    .login-btn, .logout-btn {
        padding: 0.5rem 1rem;
        background: var(--primary-color);
        color: white;
        border: none;
        border-radius: 4px;
        cursor: pointer;
        transition: background 0.2s;
    }

    .login-btn:hover, .logout-btn:hover {
        background: var(--primary-hover);
    }

    .logout-btn {
        background: var(--danger-color);
        font-size: 0.9rem;
    }

    .logout-btn:hover {
        background: #c53030;
    }

    .guilds-list {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
        gap: 1rem;
        margin-top: 2rem;
    }

    .guild-item {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 1rem;
        background: var(--card-bg);
        border-radius: 8px;
        border: 1px solid var(--border-color);
        cursor: pointer;
        transition: all 0.2s;
    }

    .guild-item:hover {
        border-color: var(--primary-color);
        transform: translateY(-2px);
    }

    .guild-icon {
        width: 40px;
        height: 40px;
        border-radius: 50%;
        background: var(--bg-secondary);
    }

    .guild-name {
        font-weight: 500;
        color: var(--text-color);
    }
`;
document.head.appendChild(style);