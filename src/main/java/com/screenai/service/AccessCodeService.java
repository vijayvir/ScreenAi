package com.screenai.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.screenai.model.SessionInfo;
import com.screenai.model.ViewerSession;

/**
 * Service class responsible for managing access codes and session information
 * 
 * This service handles the core functionality for Phase-1 and Phase-2:
 * Phase-1 (Host):
 * 1. Generates random 6-digit access codes
 * 2. Creates unique session IDs (UUID)
 * 3. Generates short-lived authentication tokens
 * 4. Stores session data in memory using HashMap
 * 5. Manages session expiry and cleanup
 * 
 * Phase-2 (Viewer):
 * 1. Validates access codes from viewers
 * 2. Creates viewer-specific tokens
 * 3. Maps viewer tokens to existing sessions
 * 4. Manages viewer session expiry
 * 
 * This is a Spring Service component, meaning Spring will automatically
 * create and manage instances of this class for dependency injection.
 */
@Service
public class AccessCodeService {
    
    private static final Logger logger = LoggerFactory.getLogger(AccessCodeService.class);
    
    /**
     * In-memory storage for access codes and their associated session information (Phase-1)
     * 
     * Key: 6-digit access code (String)
     * Value: SessionInfo object containing sessionId, token, and expiryTime
     * 
     * Using ConcurrentHashMap for thread safety since multiple users
     * might access this simultaneously
     */
    private final Map<String, SessionInfo> sessionStorage = new ConcurrentHashMap<>();
    
    /**
     * In-memory storage for viewer sessions (Phase-2)
     * 
     * Key: Viewer token (String)
     * Value: ViewerSession object containing sessionId and expiryTime
     * 
     * This maps viewer tokens to host sessions, allowing viewers to join
     * Using ConcurrentHashMap for thread safety
     */
    private final Map<String, ViewerSession> viewerStorage = new ConcurrentHashMap<>();
    
    /**
     * Random number generator for creating access codes
     * Using a single instance for better randomness distribution
     */
    private final Random random = new Random();
    
    /**
     * Default session duration in minutes
     * Sessions expire after this time for security
     */
    private static final int SESSION_DURATION_MINUTES = 30;
    
    /**
     * Default viewer session duration in minutes
     * Viewer sessions expire after this time for security
     */
    private static final int VIEWER_SESSION_DURATION_MINUTES = 30;
    
    /**
     * Generates a new 6-digit access code and creates a session
     * 
     * This is the main method called when a host clicks "Start Sharing"
     * It performs the following steps:
     * 1. Generate a random 6-digit code
     * 2. Create a unique session ID (UUID)
     * 3. Generate a secure authentication token
     * 4. Calculate expiry time
     * 5. Store everything in the HashMap
     * 
     * @return Map containing the generated code, sessionId, and token
     */
    public Map<String, String> generateAccessCode() {
        // Step 1: Generate a random 6-digit access code
        String accessCode = generateSixDigitCode();
        
        // Step 2: Create a unique session ID using UUID
        String sessionId = UUID.randomUUID().toString();
        
        // Step 3: Generate a secure authentication token
        String token = generateSecureToken();
        
        // Step 4: Calculate when this session will expire
        LocalDateTime expiryTime = LocalDateTime.now().plus(SESSION_DURATION_MINUTES, ChronoUnit.MINUTES);
        
        // Step 5: Create SessionInfo object with all the data
        SessionInfo sessionInfo = new SessionInfo(sessionId, token, expiryTime);
        
        // Step 6: Store the session in our HashMap
        // Key = access code, Value = session information
        sessionStorage.put(accessCode, sessionInfo);
        
        // Step 7: Log the creation for debugging (remove in production)
        logger.info("Generated new session:");
        logger.info("  Access Code: {}", accessCode);
        logger.info("  Session ID: {}", sessionId);
        logger.info("  Expires at: {}", expiryTime);
        
        // Step 8: Return the data that will be sent to the frontend
        Map<String, String> response = new HashMap<>();
        response.put("code", accessCode);
        response.put("sessionId", sessionId);
        response.put("token", token);
        
        return response;
    }
    
    /**
     * Generates a random 6-digit access code
     * 
     * This method ensures the code is always exactly 6 digits
     * and handles the edge case where random number might be less than 6 digits
     * 
     * @return 6-digit access code as String
     */
    private String generateSixDigitCode() {
        // Generate a random number between 100000 and 999999
        // This ensures we always get exactly 6 digits
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }
    
    /**
     * Generates a secure authentication token
     * 
     * For Phase-1, this creates a random string token
     * In future phases, this will be replaced with JWT tokens
     * 
     * @return Secure token as String
     */
    private String generateSecureToken() {
        // Generate a random UUID and remove hyphens for a cleaner token
        // This creates a 32-character random string
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Retrieves session information by access code
     * 
     * This method is used to validate access codes and get session details
     * It also checks if the session has expired
     * 
     * @param accessCode The 6-digit code to look up
     * @return SessionInfo if found and not expired, null otherwise
     */
    public SessionInfo getSessionByCode(String accessCode) {
        // Validate input parameter
        if (accessCode == null || accessCode.trim().isEmpty()) {
            logger.warn("Access code is null or empty");
            return null;
        }
        
        // Get the session from our HashMap
        SessionInfo session = sessionStorage.get(accessCode);
        
        // If no session found, return null
        if (session == null) {
            logger.debug("No session found for access code: {}", accessCode);
            return null;
        }
        
        // Check if the session has expired
        if (session.isExpired()) {
            // Remove expired session from storage
            sessionStorage.remove(accessCode);
            logger.info("Session expired and removed: {}", accessCode);
            return null;
        }
        
        // Return the valid session
        return session;
    }
    
    /**
     * Validates if an access code exists and is not expired
     * 
     * @param accessCode The code to validate
     * @return true if code is valid and not expired, false otherwise
     */
    public boolean isValidAccessCode(String accessCode) {
        if (accessCode == null || accessCode.trim().isEmpty()) {
            logger.warn("Access code is null or empty for validation");
            return false;
        }
        
        SessionInfo session = getSessionByCode(accessCode);
        return session != null;
    }
    
    /**
     * Removes a session from storage
     * 
     * This can be called when a session ends or needs to be invalidated
     * 
     * @param accessCode The code of the session to remove
     * @return true if session was removed, false if not found
     */
    public boolean removeSession(String accessCode) {
        if (accessCode == null || accessCode.trim().isEmpty()) {
            logger.warn("Access code is null or empty for removal");
            return false;
        }
        
        SessionInfo removed = sessionStorage.remove(accessCode);
        if (removed != null) {
            logger.info("Session removed: {}", accessCode);
            return true;
        }
        return false;
    }
    
    /**
     * Gets the total number of active sessions
     * 
     * Useful for monitoring and debugging
     * 
     * @return Number of active sessions
     */
    public int getActiveSessionCount() {
        return sessionStorage.size();
    }
    
    /**
     * Cleans up expired sessions from storage
     * 
     * This method iterates through all sessions and removes expired ones
     * Should be called periodically to prevent memory leaks
     */
    public void cleanupExpiredSessions() {
        try {
            int removedCount = 0;
            
            // Create a copy of keys to avoid ConcurrentModificationException
            // when removing items during iteration
            String[] codes = sessionStorage.keySet().toArray(new String[0]);
            
            if (codes == null) {
                logger.warn("Session storage keys array is null");
                return;
            }
            
            for (String code : codes) {
                if (code == null) {
                    logger.debug("Found null access code in storage, removing");
                    sessionStorage.remove(code);
                    removedCount++;
                    continue;
                }
                
                SessionInfo session = sessionStorage.get(code);
                if (session != null && session.isExpired()) {
                    sessionStorage.remove(code);
                    removedCount++;
                }
            }
            
            if (removedCount > 0) {
                logger.info("Cleaned up {} expired sessions", removedCount);
            }
        } catch (Exception e) {
            logger.error("Error during session cleanup", e);
        }
    }
    
    /**
     * Gets all active access codes (for debugging purposes)
     * 
     * @return Set of all active access codes
     */
    public Map<String, SessionInfo> getAllSessions() {
        // Return a copy to prevent external modification
        return new HashMap<>(sessionStorage);
    }
    
    /**
     * Phase-2: Allows a viewer to join a screen sharing session using an access code
     * 
     * This method handles viewer joining functionality:
     * 1. Validates the access code exists and is not expired
     * 2. Retrieves the sessionId from the validated code
     * 3. Generates a unique viewer token
     * 4. Stores the viewer session in memory
     * 5. Returns the sessionId and viewerToken to the viewer
     * 
     * @param accessCode The 6-digit access code provided by the viewer
     * @return Map containing sessionId and viewerToken if valid, null otherwise
     */
    public Map<String, String> joinSession(String accessCode) {
        // Step 1: Validate input parameter
        if (accessCode == null || accessCode.trim().isEmpty()) {
            logger.warn("Viewer attempted to join with null or empty access code");
            return null;
        }
        
        // Step 2: Validate if the access code exists and is not expired
        SessionInfo sessionInfo = getSessionByCode(accessCode);
        
        if (sessionInfo == null) {
            logger.warn("Viewer attempted to join with invalid or expired access code: {}", accessCode);
            return null;
        }
        
        // Step 3: Get the sessionId from the validated session
        String sessionId = sessionInfo.getSessionId();
        logger.info("Viewer successfully validated access code: {} for session: {}", accessCode, sessionId);
        
        // Step 4: Generate a unique viewer token
        String viewerToken = generateSecureToken();
        
        // Step 5: Calculate when this viewer session will expire
        LocalDateTime expiryTime = LocalDateTime.now().plus(VIEWER_SESSION_DURATION_MINUTES, ChronoUnit.MINUTES);
        
        // Step 6: Create ViewerSession object with sessionId and expiry time
        ViewerSession viewerSession = new ViewerSession(sessionId, expiryTime);
        
        // Step 7: Store the viewer session in the HashMap
        // Key = viewer token, Value = viewer session information
        viewerStorage.put(viewerToken, viewerSession);
        
        // Step 8: Log the viewer joining for debugging
        logger.info("Viewer joined session:");
        logger.info("  Access Code: {}", accessCode);
        logger.info("  Session ID: {}", sessionId);
        logger.info("  Viewer Token: {}", viewerToken);
        logger.info("  Expires at: {}", expiryTime);
        
        // Step 9: Return the data that will be sent to the viewer
        Map<String, String> response = new HashMap<>();
        response.put("message", "Access granted");
        response.put("sessionId", sessionId);
        response.put("viewerToken", viewerToken);
        
        return response;
    }
    
    /**
     * Validates a viewer token and retrieves the associated sessionId
     * 
     * This method is used to verify that a viewer token is valid and not expired
     * It also checks if the viewer session has expired
     * 
     * @param viewerToken The viewer token to validate
     * @return SessionId if valid, null otherwise
     */
    public String validateViewerToken(String viewerToken) {
        // Validate input parameter
        if (viewerToken == null || viewerToken.trim().isEmpty()) {
            logger.warn("Viewer token is null or empty for validation");
            return null;
        }
        
        // Get the viewer session from our HashMap
        ViewerSession viewerSession = viewerStorage.get(viewerToken);
        
        // If no session found, return null
        if (viewerSession == null) {
            logger.debug("No viewer session found for token: {}", viewerToken);
            return null;
        }
        
        // Check if the viewer session has expired
        if (viewerSession.isExpired()) {
            // Remove expired viewer session from storage
            viewerStorage.remove(viewerToken);
            logger.info("Viewer session expired and removed: {}", viewerToken);
            return null;
        }
        
        // Return the valid session ID
        return viewerSession.getSessionId();
    }
    
    /**
     * Cleanup expired viewer sessions from storage
     * 
     * This method iterates through all viewer sessions and removes expired ones
     * Should be called periodically to prevent memory leaks
     */
    public void cleanupExpiredViewerSessions() {
        try {
            int removedCount = 0;
            
            // Create a copy of keys to avoid ConcurrentModificationException
            String[] tokens = viewerStorage.keySet().toArray(new String[0]);
            
            if (tokens == null) {
                logger.warn("Viewer storage keys array is null");
                return;
            }
            
            for (String token : tokens) {
                if (token == null) {
                    logger.debug("Found null viewer token in storage, removing");
                    viewerStorage.remove(token);
                    removedCount++;
                    continue;
                }
                
                ViewerSession viewerSession = viewerStorage.get(token);
                if (viewerSession != null && viewerSession.isExpired()) {
                    viewerStorage.remove(token);
                    removedCount++;
                }
            }
            
            if (removedCount > 0) {
                logger.info("Cleaned up {} expired viewer sessions", removedCount);
            }
        } catch (Exception e) {
            logger.error("Error during viewer session cleanup", e);
        }
    }
}
