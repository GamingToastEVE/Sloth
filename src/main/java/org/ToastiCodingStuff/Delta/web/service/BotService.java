package org.ToastiCodingStuff.Delta.web.service;

import org.ToastiCodingStuff.Delta.DatabaseHandler;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class BotService {
    
    private final DatabaseHandler databaseHandler;
    
    public BotService() {
        this.databaseHandler = new DatabaseHandler();
    }
    
    public String getTodaysStatistics(String guildId) {
        try {
            return databaseHandler.getTodaysStatistics(guildId);
        } catch (Exception e) {
            return "Error retrieving today's statistics: " + e.getMessage();
        }
    }
    
    public String getWeeklyStatistics(String guildId) {
        try {
            return databaseHandler.getWeeklyStatistics(guildId);
        } catch (Exception e) {
            return "Error retrieving weekly statistics: " + e.getMessage();
        }
    }
    
    public String getStatisticsForDate(String guildId, String date) {
        try {
            return databaseHandler.getStatisticsForDate(guildId, date);
        } catch (Exception e) {
            return "Error retrieving statistics for date: " + e.getMessage();
        }
    }
    
    public List<Map<String, String>> getTicketsByGuild(String guildId) {
        try {
            return databaseHandler.getTicketsByGuildWithPriority(guildId);
        } catch (Exception e) {
            System.err.println("Error retrieving tickets for guild " + guildId + ": " + e.getMessage());
            return List.of(); // Return empty list on error
        }
    }
    
    public String getTicketCategory(String guildId) {
        try {
            return databaseHandler.getTicketCategory(guildId);
        } catch (Exception e) {
            return null;
        }
    }
    
    public String getTicketChannel(String guildId) {
        try {
            return databaseHandler.getTicketChannel(guildId);
        } catch (Exception e) {
            return null;
        }
    }
    
    public boolean setTicketSettings(String guildId, String categoryId, String channelId, String roleId, boolean transcriptEnabled) {
        try {
            return databaseHandler.setTicketSettings(guildId, categoryId, channelId, roleId, transcriptEnabled);
        } catch (Exception e) {
            System.err.println("Error setting ticket settings for guild " + guildId + ": " + e.getMessage());
            return false;
        }
    }
    
    public void setWarnSettings(String guildId, int maxWarns, int minutesMuted, String roleId, int warnTimeHours) {
        try {
            databaseHandler.setWarnSettings(guildId, maxWarns, minutesMuted, roleId, warnTimeHours);
        } catch (Exception e) {
            System.err.println("Error setting warn settings for guild " + guildId + ": " + e.getMessage());
            throw new RuntimeException("Failed to update warning settings", e);
        }
    }
}