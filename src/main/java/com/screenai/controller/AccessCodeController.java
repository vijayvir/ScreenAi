package com.screenai.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.screenai.service.AccessCodeService;

/**
 * REST Controller for handling access code operations
 * 
 * This controller provides REST API endpoints for the screen sharing application.
 * It handles HTTP requests and responses, delegating business logic to services.
 * 
 * Key responsibilities:
 * - Expose REST endpoints for frontend communication
 * - Handle HTTP request/response mapping
 * - Validate requests and return appropriate responses
 * - Delegate business logic to service layer
 * 
 * Phase-1 (Host): /api/start endpoint for session creation
 * Phase-2 (Viewer): /api/join endpoint for viewer joining
 * 
 * This is a Spring REST Controller, meaning Spring will automatically
 * register these endpoints and handle HTTP routing.
 */
@RestController
@RequestMapping("/api")
public class AccessCodeController {
    
    /**
     * Service dependency injection
     * 
     * Spring automatically injects an instance of AccessCodeService
     * when this controller is created. This is called "Dependency Injection"
     * and is a core feature of Spring Framework.
     */
    @Autowired
    private AccessCodeService accessCodeService;
    
    /**
     * REST endpoint to start a new screen sharing session
     * 
     * This endpoint is called when a host clicks "Start Sharing" button.
     * It triggers the creation of a new session with:
     * - Random 6-digit access code
     * - Unique session ID (UUID)
     * - Short-lived authentication token
     * - Session expiry time
     * 
     * HTTP Method: POST
     * URL: /api/start
     * 
     * Request: No request body required (empty POST request)
     * Response: JSON containing code, sessionId, and token
     * 
     * Example Response:
     * {
     *   "code": "123456",
     *   "sessionId": "550e8400-e29b-41d4-a716-446655440000",
     *   "token": "a1b2c3d4e5f6789012345678901234567890abcd"
     * }
     * 
     * @return ResponseEntity containing the session data or error message
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startSharing() {
        try {
            // Log the request for debugging
            System.out.println("Received request to start screen sharing session");
            
            // Call the service to generate access code and session data
            // This will:
            // 1. Generate a random 6-digit code
            // 2. Create a unique session ID
            // 3. Generate a secure token
            // 4. Store everything in HashMap
            Map<String, String> sessionData = accessCodeService.generateAccessCode();
            
            // Log the response for debugging
            System.out.println("Generated session data:");
            System.out.println("  Code: " + sessionData.get("code"));
            System.out.println("  Session ID: " + sessionData.get("sessionId"));
            System.out.println("  Token: " + sessionData.get("token"));
            
            // Return successful response with HTTP 200 OK status
            // ResponseEntity allows us to control HTTP status codes and headers
            return ResponseEntity.ok(sessionData);
            
        } catch (Exception e) {
            // Handle any unexpected errors
            System.err.println("Error starting screen sharing session: " + e.getMessage());
            e.printStackTrace();
            
            // Return error response with HTTP 500 Internal Server Error
            // In a real application, you might want to return more specific error messages
            Map<String, String> errorResponse = Map.of(
                "error", "Failed to start screen sharing session",
                "message", e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * REST endpoint to join a screen sharing session (Phase-2)
     * 
     * This endpoint is called when a viewer wants to join an existing session.
     * It validates the access code and creates a viewer session.
     * 
     * HTTP Method: POST
     * URL: /api/join
     * 
     * Request Body: JSON containing access code
     * {
     *   "code": "123456"
     * }
     * 
     * Response (Success):
     * {
     *   "message": "Access granted",
     *   "sessionId": "uuid-session-id",
     *   "viewerToken": "random-generated-viewer-token"
     * }
     * 
     * Response (Error):
     * {
     *   "error": "Invalid or expired access code"
     * }
     * 
     * @param requestBody Map containing the access code
     * @return ResponseEntity containing sessionId and viewerToken if valid, error otherwise
     */
    @PostMapping("/join")
    public ResponseEntity<Map<String, String>> joinSession(@RequestBody Map<String, String> requestBody) {
        try {
            // Log the request for debugging
            System.out.println("Received request to join screen sharing session");
            
            // Step 1: Extract access code from request body
            String accessCode = requestBody != null ? requestBody.get("code") : null;
            
            if (accessCode == null || accessCode.trim().isEmpty()) {
                System.err.println("Access code is missing in request body");
                Map<String, String> errorResponse = Map.of(
                    "error", "Access code is required"
                );
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Step 2: Call the service to validate code and create viewer session
            // This will:
            // 1. Validate if the access code exists and is not expired
            // 2. Generate a unique viewer token
            // 3. Store viewer session in HashMap
            // 4. Return sessionId and viewerToken
            Map<String, String> result = accessCodeService.joinSession(accessCode);
            
            if (result != null) {
                // Step 3: Success - viewer joined successfully
                System.out.println("Viewer successfully joined session:");
                System.out.println("  Access Code: " + accessCode);
                System.out.println("  Session ID: " + result.get("sessionId"));
                System.out.println("  Viewer Token: " + result.get("viewerToken"));
                
                return ResponseEntity.ok(result);
            } else {
                // Step 4: Failure - invalid or expired access code
                System.err.println("Failed to join session - invalid or expired access code: " + accessCode);
                Map<String, String> errorResponse = Map.of(
                    "error", "Invalid or expired access code"
                );
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
        } catch (Exception e) {
            // Handle any unexpected errors
            System.err.println("Error joining screen sharing session: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, String> errorResponse = Map.of(
                "error", "Failed to join screen sharing session",
                "message", e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * REST endpoint to validate an access code
     * 
     * This endpoint can be used to check if an access code is valid
     * (not implemented in Phase-1, but prepared for future phases)
     * 
     * HTTP Method: POST
     * URL: /api/validate
     * 
     * @param accessCode The 6-digit code to validate
     * @return ResponseEntity indicating if the code is valid
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateAccessCode(String accessCode) {
        try {
            // Validate input parameter
            if (accessCode == null || accessCode.trim().isEmpty()) {
                Map<String, Object> errorResponse = Map.of(
                    "code", "null",
                    "valid", false,
                    "message", "Access code cannot be null or empty"
                );
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Check if the access code is valid and not expired
            boolean isValid = accessCodeService.isValidAccessCode(accessCode);
            
            // Prepare response data
            Map<String, Object> response = Map.of(
                "code", accessCode,
                "valid", isValid,
                "message", isValid ? "Access code is valid" : "Access code is invalid or expired"
            );
            
            // Return appropriate HTTP status based on validation result
            if (isValid) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            System.err.println("Error validating access code: " + e.getMessage());
            
            Map<String, Object> errorResponse = Map.of(
                "error", "Failed to validate access code",
                "message", e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    /**
     * REST endpoint to get session statistics
     * 
     * This endpoint provides information about active sessions
     * Useful for monitoring and debugging
     * 
     * HTTP Method: GET
     * URL: /api/stats
     * 
     * @return ResponseEntity containing session statistics
     */
    @PostMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSessionStats() {
        try {
            // Get the number of active sessions
            int activeSessions = accessCodeService.getActiveSessionCount();
            
            // Prepare response data
            Map<String, Object> response = Map.of(
                "activeSessions", activeSessions,
                "message", "Session statistics retrieved successfully"
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Error getting session stats: " + e.getMessage());
            
            Map<String, Object> errorResponse = Map.of(
                "error", "Failed to get session statistics",
                "message", e.getMessage()
            );
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
