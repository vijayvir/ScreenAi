package com.screenai;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.screenai.service.ScreenCaptureService;

/**
 * Main application class for ScreenAI
 * 
 * This application provides real-time screen sharing capabilities using:
 * - Spring Boot for the web framework
 * - WebSockets for real-time communication
 * - JavaCV for cross-platform screen capture
 * - Thymeleaf for the web interface
 * 
 * The application is cross-platform compatible and works on Windows, macOS, and Linux.
 */
@SpringBootApplication
@EnableScheduling
public class ScreenAIApplication implements CommandLineRunner {

	@Autowired
	private ScreenCaptureService screenCaptureService;

	public static void main(String[] args) {
		// Ensure GUI access is available (not headless mode)
		System.setProperty("java.awt.headless", "false");
		
		SpringApplication app = new SpringApplication(ScreenAIApplication.class);
		app.setHeadless(false); // Ensure Spring Boot doesn't run in headless mode
		app.run(args);
	}

	@Override
	public void run(String... args) throws Exception {
		// Print startup information after Spring Boot is fully initialized
		System.out.println("=================================");
		System.out.println("ScreenAI Server Started");
		System.out.println("=================================");
		
		// Show local access URL
		System.out.println("Local access: http://localhost:8080");
		
		// Show network IP addresses for WiFi access
		String networkIp = getNetworkIp();
		if (networkIp != null) {
			System.out.println("Network access: http://" + networkIp + ":8080");
			System.out.println("");
			System.out.println("To view from another device on same WiFi:");
			System.out.println("1. Connect your other device to the same WiFi network");
			System.out.println("2. Open a web browser on that device");
			System.out.println("3. Navigate to: http://" + networkIp + ":8080");
		} else {
			System.out.println("Network IP not detected - check your WiFi connection");
		}
		
		System.out.println("=================================");
		
		// Initialize screen capture (but don't auto-start)
		try {
			screenCaptureService.initialize();
			if (screenCaptureService.isInitialized()) {
				System.out.println("Screen capture initialized successfully!");
				System.out.println("Capture method: " + screenCaptureService.getCaptureMethod());
				System.out.println("Ready to start capture when requested via API");
			} else {
				System.out.println("Warning: Screen capture could not be initialized");
			}
		} catch (Exception e) {
			System.out.println("Error initializing screen capture: " + e.getMessage());
		}
		
		System.out.println("=================================");
	}
	
	/**
	 * Gets the network IP address for WiFi access from other devices
	 * @return Network IP address or null if not found
	 */
	private String getNetworkIp() {
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();
				
				// Skip loopback and non-active interfaces
				if (networkInterface.isLoopback() || !networkInterface.isUp()) {
					continue;
				}
				
				Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress address = addresses.nextElement();
					
					// Look for IPv4 addresses that are not loopback
					if (!address.isLoopbackAddress() && address.isSiteLocalAddress()) {
						String ip = address.getHostAddress();
						// Prefer common WiFi network ranges
						if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
							return ip;
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error detecting network IP: " + e.getMessage());
		}
		return null;
	}
}
