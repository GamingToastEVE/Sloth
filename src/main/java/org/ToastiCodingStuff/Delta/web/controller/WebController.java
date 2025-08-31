package org.ToastiCodingStuff.Delta.web.controller;

import org.ToastiCodingStuff.Delta.web.service.BotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
public class WebController {
    
    @Autowired
    private BotService botService;
    
    @GetMapping("/")
    public String home() {
        return "index";
    }
    
    @GetMapping("/login")
    public String login() {
        return "login";
    }
    
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        model.addAttribute("user", user);
        model.addAttribute("username", user.getAttribute("username"));
        model.addAttribute("discriminator", user.getAttribute("discriminator"));
        model.addAttribute("avatar", user.getAttribute("avatar"));
        return "dashboard";
    }
    
    @GetMapping("/statistics/{guildId}")
    public String statistics(@PathVariable String guildId, Model model, Authentication authentication) {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        
        String todayStats = botService.getTodaysStatistics(guildId);
        String weeklyStats = botService.getWeeklyStatistics(guildId);
        
        model.addAttribute("user", user);
        model.addAttribute("guildId", guildId);
        model.addAttribute("todayStats", todayStats);
        model.addAttribute("weeklyStats", weeklyStats);
        
        return "statistics";
    }
    
    @GetMapping("/tickets/{guildId}")
    public String tickets(@PathVariable String guildId, Model model, Authentication authentication) {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        
        List<Map<String, String>> tickets = botService.getTicketsByGuild(guildId);
        
        model.addAttribute("user", user);
        model.addAttribute("guildId", guildId);
        model.addAttribute("tickets", tickets);
        
        return "tickets";
    }
    
    @GetMapping("/settings/{guildId}")
    public String settings(@PathVariable String guildId, Model model, Authentication authentication) {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        
        String ticketCategory = botService.getTicketCategory(guildId);
        String ticketChannel = botService.getTicketChannel(guildId);
        
        model.addAttribute("user", user);
        model.addAttribute("guildId", guildId);
        model.addAttribute("ticketCategory", ticketCategory);
        model.addAttribute("ticketChannel", ticketChannel);
        
        return "settings";
    }
    
    @PostMapping("/settings/{guildId}/ticket")
    public String updateTicketSettings(@PathVariable String guildId,
                                     @RequestParam String categoryId,
                                     @RequestParam String channelId,
                                     @RequestParam(required = false) String roleId,
                                     @RequestParam(defaultValue = "false") boolean transcriptEnabled,
                                     Model model) {
        boolean success = botService.setTicketSettings(guildId, categoryId, channelId, roleId, transcriptEnabled);
        model.addAttribute("success", success);
        model.addAttribute("message", success ? "Ticket settings updated successfully!" : "Failed to update ticket settings.");
        return "redirect:/settings/" + guildId;
    }
    
    @PostMapping("/settings/{guildId}/warn")
    public String updateWarnSettings(@PathVariable String guildId,
                                   @RequestParam int maxWarns,
                                   @RequestParam int minutesMuted,
                                   @RequestParam(required = false) String roleId,
                                   @RequestParam int warnTimeHours,
                                   Model model) {
        botService.setWarnSettings(guildId, maxWarns, minutesMuted, roleId, warnTimeHours);
        model.addAttribute("success", true);
        model.addAttribute("message", "Warning settings updated successfully!");
        return "redirect:/settings/" + guildId;
    }
}