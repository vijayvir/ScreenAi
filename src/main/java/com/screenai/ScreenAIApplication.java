package com.screenai;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;

/**
 * Main application class for ScreenAI-Server
 * 
 * This is a RELAY-ONLY server using Spring WebFlux + Netty for high-performance
 * non-blocking video streaming.
 * 
 * Features:
 * - Spring WebFlux for reactive web framework
 * - Netty for non-blocking I/O
 * - Reactive WebSockets for real-time binary data relay
 * - Room-based session management
 * - Automatic backpressure handling
 */
@SpringBootApplication
public class ScreenAIApplication implements CommandLineRunner {

	@Value("${server.port:8080}")
	private int serverPort;

	public static void main(String[] args) {
		SpringApplication.run(ScreenAIApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		// Print startup information after Spring Boot is fully initialized
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("   ScreenAI-Server (WebFlux + Netty) Started Successfully   ");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("");
		
		// Show local access URL
		System.out.println("📍 WebSocket Endpoint:");
		System.out.println("   Local:   ws://localhost:" + serverPort + "/screenshare");
		
		// Show network IP addresses for remote access
		String networkIp = getNetworkIp();
		if (networkIp != null) {
			System.out.println("   Network: ws://" + networkIp + ":" + serverPort + "/screenshare");
			System.out.println("");
			System.out.println("🌐 Network Access Instructions:");
			System.out.println("   1. Connect client device to same network");
			System.out.println("   2. Use network address: " + networkIp + ":" + serverPort);
		} else {
			System.out.println("   ⚠️  Network IP not detected - check connection");
		}
		
		System.out.println("");
		System.out.println("Server Mode: WebFlux + Netty (Non-Blocking)");
		System.out.println("   ✅ Reactive WebSocket handling");
		System.out.println("   ✅ Non-blocking I/O via Netty");
		System.out.println("   ✅ Automatic backpressure handling");
		System.out.println("   ✅ Binary data relay (no size limits)");
		System.out.println("   ✅ Room management enabled");
		System.out.println("");
		System.out.println("🧪 Test with wscat:");
		System.out.println("   wscat -c ws://localhost:" + serverPort + "/screenshare");
		System.out.println("   > {\"type\":\"create-room\",\"roomId\":\"test\"}");
		System.out.println("");
		System.out.println("═══════════════════════════════════════════════════════════");
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
