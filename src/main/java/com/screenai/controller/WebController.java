package com.screenai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Web controller for serving the screen sharing viewer page
 */
@Controller
public class WebController {
    
    /**
     * Serves the main viewer page
     * @return the name of the Thymeleaf template
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }
}
