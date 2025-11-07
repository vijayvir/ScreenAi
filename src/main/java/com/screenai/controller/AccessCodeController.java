package com.screenai.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.screenai.service.AccessCodeService;


 // (Host): /api/start endpoint for session creation
 // (Viewer): /api/join endpoint for viewer joining

@RestController
@RequestMapping("/api")
public class AccessCodeController {
    
   
    @Autowired
    private AccessCodeService accessCodeService;
    
   
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
    
   
    @PostMapping("/join")
    public ResponseEntity<Map<String, String>> joinSession(@RequestBody Map<String, String> requestBody) {
        try {
            // Log the request for debugging
            System.out.println("Received request to join screen sharing session");
            
            // Extract access code from request body
            String accessCode = requestBody != null ? requestBody.get("code") : null;
            
            if (accessCode == null || accessCode.trim().isEmpty()) {
                System.err.println("Access code is missing in request body");
                Map<String, String> errorResponse = Map.of(
                    "error", "Access code is required"
                );
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
           
            Map<String, String> result = accessCodeService.joinSession(accessCode);
            
            if (result != null) {
                //  Success - viewer joined successfully
                System.out.println("Viewer successfully joined session:");
                System.out.println("  Access Code: " + accessCode);
                System.out.println("  Session ID: " + result.get("sessionId"));
                System.out.println("  Viewer Token: " + result.get("viewerToken"));
                System.out.println("  Host Created At: " + result.get("hostCreatedAt"));
                System.out.println("  Viewer Joined At: " + result.get("viewerJoinedAt"));
                
                return ResponseEntity.ok(result);
            } else {
                // Failure - invalid or expired access code
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
    
  /*/
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
    }*/


}
