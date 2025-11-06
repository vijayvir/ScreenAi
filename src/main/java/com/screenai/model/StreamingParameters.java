package com.screenai.model;

import com.screenai.service.NetworkQualityService.NetworkQuality;

/**
 * Data Transfer Object for streaming parameters
 * Maps network quality to specific encoding settings
 */
public class StreamingParameters {
    
    private int bitrate;           // In bps (bits per second)
    private int frameRate;         // Frames per second
    private double resolutionScale; // Scale factor (0.5-1.0)
    
    public StreamingParameters(int bitrate, int frameRate, double resolutionScale) {
        this.bitrate = bitrate;
        this.frameRate = frameRate;
        this.resolutionScale = Math.max(0.5, Math.min(1.0, resolutionScale));
    }
    
    /**
     * Factory method to create optimal parameters based on network quality
     * 
     * @param quality The assessed network quality
     * @return Optimized streaming parameters
     */
    public static StreamingParameters forQuality(NetworkQuality quality) {
        return switch(quality) {
            case EXCELLENT -> new StreamingParameters(2000000, 30, 1.0);  // 2 Mbps, 30fps, full resolution
            case GOOD      -> new StreamingParameters(1500000, 24, 1.0);  // 1.5 Mbps, 24fps, full resolution
            case FAIR      -> new StreamingParameters(1000000, 15, 0.85); // 1 Mbps, 15fps, 85% resolution
            case POOR      -> new StreamingParameters(500000, 10, 0.75);  // 500 kbps, 10fps, 75% resolution
        };
    }
    
    /**
     * Create parameters with custom settings within configured limits
     */
    public static StreamingParameters withLimits(NetworkQuality quality, 
                                                  int minBitrate, int maxBitrate,
                                                  int minFrameRate, int maxFrameRate) {
        StreamingParameters base = forQuality(quality);
        
        // Clamp to configured limits
        int constrainedBitrate = Math.max(minBitrate, Math.min(maxBitrate, base.bitrate));
        int constrainedFrameRate = Math.max(minFrameRate, Math.min(maxFrameRate, base.frameRate));
        
        return new StreamingParameters(constrainedBitrate, constrainedFrameRate, base.resolutionScale);
    }
    
    // Getters
    public int getBitrate() {
        return bitrate;
    }
    
    public int getFrameRate() {
        return frameRate;
    }
    
    public double getResolutionScale() {
        return resolutionScale;
    }
    
    // Bitrate in kbps for logging
    public int getBitrateKbps() {
        return bitrate / 1000;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        StreamingParameters other = (StreamingParameters) obj;
        return bitrate == other.bitrate &&
               frameRate == other.frameRate &&
               Double.compare(resolutionScale, other.resolutionScale) == 0;
    }
    
    @Override
    public int hashCode() {
        int result = Integer.hashCode(bitrate);
        result = 31 * result + Integer.hashCode(frameRate);
        result = 31 * result + Double.hashCode(resolutionScale);
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("StreamingParameters{bitrate=%d kbps, frameRate=%d fps, resolutionScale=%.2f}",
                            getBitrateKbps(), frameRate, resolutionScale);
    }
}
