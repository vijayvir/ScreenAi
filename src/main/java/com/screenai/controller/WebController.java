package com.aiscreensharing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Web controller for serving the screen sharing viewer page
 */
@Controller
public class WebController {
    
    /**
     * Serves the main screen sharing viewer page
     * @return the name of the Thymeleaf template
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }
    
    /**
     * Alternative endpoint for the viewer
     * @return the name of the Thymeleaf template
     */
    @GetMapping("/viewer")
    public String viewer() {
        return "index";
    }
}
