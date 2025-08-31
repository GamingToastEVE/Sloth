package org.ToastiCodingStuff.Delta.web.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CustomErrorController implements ErrorController {
    
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
        String errorMessage = (String) request.getAttribute("jakarta.servlet.error.message");
        
        model.addAttribute("statusCode", statusCode != null ? statusCode : 500);
        model.addAttribute("errorMessage", errorMessage != null ? errorMessage : "An unexpected error occurred");
        
        return "error";
    }
}