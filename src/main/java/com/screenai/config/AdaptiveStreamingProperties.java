package com.screenai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for adaptive streaming
 * Binds to screenai.adaptive.* properties in application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "screenai.adaptive")
public class AdaptiveStreamingProperties {
    
    private boolean enabled = true;
    private long assessmentInterval = 10000; // 10 seconds
    
    private BitrateSettings bitrate = new BitrateSettings();
    private FrameRateSettings framerate = new FrameRateSettings();
    private ResolutionSettings resolution = new ResolutionSettings();
    
    // Getters and setters
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public long getAssessmentInterval() {
        return assessmentInterval;
    }
    
    public void setAssessmentInterval(long assessmentInterval) {
        this.assessmentInterval = assessmentInterval;
    }
    
    public BitrateSettings getBitrate() {
        return bitrate;
    }
    
    public void setBitrate(BitrateSettings bitrate) {
        this.bitrate = bitrate;
    }
    
    public FrameRateSettings getFramerate() {
        return framerate;
    }
    
    public void setFramerate(FrameRateSettings framerate) {
        this.framerate = framerate;
    }
    
    public ResolutionSettings getResolution() {
        return resolution;
    }
    
    public void setResolution(ResolutionSettings resolution) {
        this.resolution = resolution;
    }
    
    // Inner classes for nested properties
    public static class BitrateSettings {
        private int max = 2000000;  // 2 Mbps
        private int min = 500000;   // 500 kbps
        
        public int getMax() {
            return max;
        }
        
        public void setMax(int max) {
            this.max = max;
        }
        
        public int getMin() {
            return min;
        }
        
        public void setMin(int min) {
            this.min = min;
        }
    }
    
    public static class FrameRateSettings {
        private int max = 30;
        private int min = 10;
        
        public int getMax() {
            return max;
        }
        
        public void setMax(int max) {
            this.max = max;
        }
        
        public int getMin() {
            return min;
        }
        
        public void setMin(int min) {
            this.min = min;
        }
    }
    
    public static class ResolutionSettings {
        private double scaleDown = 0.75;
        
        public double getScaleDown() {
            return scaleDown;
        }
        
        public void setScaleDown(double scaleDown) {
            this.scaleDown = scaleDown;
        }
    }
}
