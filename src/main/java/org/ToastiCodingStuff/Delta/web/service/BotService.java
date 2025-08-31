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
        return databaseHandler.getTodaysStatistics(guildId);
    }
    
    public String getWeeklyStatistics(String guildId) {
        return databaseHandler.getWeeklyStatistics(guildId);
    }
    
    public String getStatisticsForDate(String guildId, String date) {
        return databaseHandler.getStatisticsForDate(guildId, date);
    }
    
    public List<Map<String, String>> getTicketsByGuild(String guildId) {
        return databaseHandler.getTicketsByGuildWithPriority(guildId);
    }
    
    public String getTicketCategory(String guildId) {
        return databaseHandler.getTicketCategory(guildId);
    }
    
    public String getTicketChannel(String guildId) {
        return databaseHandler.getTicketChannel(guildId);
    }
    
    public boolean setTicketSettings(String guildId, String categoryId, String channelId, String roleId, boolean transcriptEnabled) {
        return databaseHandler.setTicketSettings(guildId, categoryId, channelId, roleId, transcriptEnabled);
    }
    
    public void setWarnSettings(String guildId, int maxWarns, int minutesMuted, String roleId, int warnTimeHours) {
        databaseHandler.setWarnSettings(guildId, maxWarns, minutesMuted, roleId, warnTimeHours);
    }
}