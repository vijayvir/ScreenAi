package com.screenai.service;

import com.screenai.dto.AuthResponse;
import com.screenai.dto.ErrorResponse.ErrorCode;
import com.screenai.dto.LoginRequest;
import com.screenai.dto.RefreshTokenRequest;
import com.screenai.dto.RegisterRequest;
import com.screenai.exception.AuthException;
import com.screenai.model.User;
import com.screenai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Service for user authentication and authorization.
 * Handles registration, login, token refresh, and logout with security best practices.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final SecurityAuditService auditService;
    private final PasswordEncoder passwordEncoder;

    // Password policy settings
    private final int minPasswordLength;
    private final boolean requireUppercase;
    private final boolean requireLowercase;
    private final boolean requireDigit;
    private final boolean requireSpecial;

    // Account lockout settings
    private final int maxLoginAttempts;
    private final int lockoutDurationMinutes;

    // Password validation patterns
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile("[!@#$%^&*(),.?\":{}|<>]");

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService,
            SecurityAuditService auditService,
            @Value("${security.password.min-length:8}") int minPasswordLength,
            @Value("${security.password.require-uppercase:true}") boolean requireUppercase,
            @Value("${security.password.require-lowercase:true}") boolean requireLowercase,
            @Value("${security.password.require-digit:true}") boolean requireDigit,
            @Value("${security.password.require-special:true}") boolean requireSpecial,
            @Value("${security.lockout.max-attempts:5}") int maxLoginAttempts,
            @Value("${security.lockout.duration-minutes:15}") int lockoutDurationMinutes) {

        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.passwordEncoder = new BCryptPasswordEncoder(12); // Cost factor 12

        this.minPasswordLength = minPasswordLength;
        this.requireUppercase = requireUppercase;
        this.requireLowercase = requireLowercase;
        this.requireDigit = requireDigit;
        this.requireSpecial = requireSpecial;
        this.maxLoginAttempts = maxLoginAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;

        log.info("AuthService initialized with password policy: minLength={}, requireUppercase={}, requireLowercase={}, requireDigit={}, requireSpecial={}",
                minPasswordLength, requireUppercase, requireLowercase, requireDigit, requireSpecial);
    }

    /**
     * Register a new user.
     */
    public Mono<AuthResponse> register(RegisterRequest request, String ipAddress) {
        return validatePassword(request.getPassword())
                .then(userRepository.existsByUsername(request.getUsername().toLowerCase()))
                .flatMap(exists -> {
                    if (exists) {
                        return auditService.logRegistrationFailure(request.getUsername(), ipAddress, "Username already exists")
                                .then(Mono.error(new AuthException(ErrorCode.AUTH_008)));
                    }

                    User user = new User();
                    user.setUsername(request.getUsername().toLowerCase());
                    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                    user.setRole("USER");
                    user.setEnabled(true);

                    return userRepository.save(user)
                            .flatMap(savedUser -> generateTokensAndResponse(savedUser, ipAddress, true));
                });
    }

    /**
     * Authenticate user and generate tokens.
     */
    public Mono<AuthResponse> login(LoginRequest request, String ipAddress) {
        String username = request.getUsername().toLowerCase();

        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.defer(() -> {
                    auditService.logLoginFailure(username, ipAddress, "User not found").subscribe();
                    return Mono.error(new AuthException(ErrorCode.AUTH_001));
                }))
                .flatMap(user -> {
                    // Check if account is locked
                    if (user.isAccountLocked()) {
                        return auditService.logLoginFailure(username, ipAddress, "Account locked")
                                .then(Mono.error(new AuthException(ErrorCode.AUTH_002)));
                    }

                    // Validate password
                    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                        return handleFailedLogin(user, ipAddress);
                    }

                    // Successful login - reset failed attempts
                    user.resetFailedAttempts();
                    return userRepository.save(user)
                            .flatMap(savedUser -> generateTokensAndResponse(savedUser, ipAddress, false));
                });
    }

    /**
     * Refresh access token using refresh token.
     */
    public Mono<AuthResponse> refreshToken(RefreshTokenRequest request, String ipAddress) {
        return userRepository.findByRefreshToken(request.getRefreshToken())
                .switchIfEmpty(Mono.error(new AuthException(ErrorCode.AUTH_005)))
                .flatMap(user -> {
                    // Check if refresh token is expired
                    if (user.getRefreshTokenExpiresAt() == null ||
                            LocalDateTime.now().isAfter(user.getRefreshTokenExpiresAt())) {
                        return auditService.logTokenRefresh(user.getUsername(), ipAddress, false, "Refresh token expired")
                                .then(Mono.error(new AuthException(ErrorCode.AUTH_003)));
                    }

                    // Generate new tokens (rotate refresh token for security)
                    String newAccessToken = jwtService.generateAccessToken(user.getUsername(), user.getRole());
                    String newRefreshToken = jwtService.generateRefreshToken();
                    LocalDateTime refreshExpiresAt = LocalDateTime.now()
                            .plusSeconds(jwtService.getRefreshTokenExpirationMillis() / 1000);

                    user.setRefreshToken(newRefreshToken);
                    user.setRefreshTokenExpiresAt(refreshExpiresAt);
                    user.setUpdatedAt(LocalDateTime.now());

                    return userRepository.save(user)
                            .flatMap(savedUser -> auditService.logTokenRefresh(savedUser.getUsername(), ipAddress, true, null)
                                    .thenReturn(AuthResponse.success(
                                            newAccessToken,
                                            newRefreshToken,
                                            jwtService.getAccessTokenExpirationSeconds(),
                                            savedUser.getUsername(),
                                            savedUser.getRole()
                                    )));
                });
    }

    /**
     * Logout user by invalidating refresh token.
     */
    public Mono<AuthResponse> logout(String username, String ipAddress) {
        return userRepository.findByUsername(username)
                .flatMap(user -> {
                    user.setRefreshToken(null);
                    user.setRefreshTokenExpiresAt(null);
                    user.setUpdatedAt(LocalDateTime.now());

                    return userRepository.save(user)
                            .flatMap(savedUser -> auditService.logLogout(username, ipAddress)
                                    .thenReturn(AuthResponse.message("Logged out successfully")));
                })
                .switchIfEmpty(Mono.just(AuthResponse.message("Logged out successfully")));
    }

    /**
     * Validate password against policy.
     */
    private Mono<Void> validatePassword(String password) {
        StringBuilder errors = new StringBuilder();

        if (password.length() < minPasswordLength) {
            errors.append("Password must be at least ").append(minPasswordLength).append(" characters. ");
        }
        if (requireUppercase && !UPPERCASE_PATTERN.matcher(password).find()) {
            errors.append("Password must contain at least one uppercase letter. ");
        }
        if (requireLowercase && !LOWERCASE_PATTERN.matcher(password).find()) {
            errors.append("Password must contain at least one lowercase letter. ");
        }
        if (requireDigit && !DIGIT_PATTERN.matcher(password).find()) {
            errors.append("Password must contain at least one digit. ");
        }
        if (requireSpecial && !SPECIAL_PATTERN.matcher(password).find()) {
            errors.append("Password must contain at least one special character. ");
        }

        if (!errors.isEmpty()) {
            return Mono.error(new AuthException(ErrorCode.AUTH_009, errors.toString().trim()));
        }

        return Mono.empty();
    }

    /**
     * Handle failed login attempt with lockout logic.
     */
    private Mono<AuthResponse> handleFailedLogin(User user, String ipAddress) {
        user.incrementFailedAttempts();

        if (user.getFailedLoginAttempts() >= maxLoginAttempts) {
            user.lockAccount(lockoutDurationMinutes);
            return userRepository.save(user)
                    .flatMap(savedUser -> auditService.logAccountLocked(user.getUsername(), ipAddress, lockoutDurationMinutes)
                            .then(Mono.error(new AuthException(ErrorCode.AUTH_002))));
        }

        return userRepository.save(user)
                .flatMap(savedUser -> auditService.logLoginFailure(user.getUsername(), ipAddress, "Invalid password")
                        .then(Mono.error(new AuthException(ErrorCode.AUTH_001))));
    }

    /**
     * Generate tokens and create auth response.
     */
    private Mono<AuthResponse> generateTokensAndResponse(User user, String ipAddress, boolean isRegistration) {
        String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken();
        LocalDateTime refreshExpiresAt = LocalDateTime.now()
                .plusSeconds(jwtService.getRefreshTokenExpirationMillis() / 1000);

        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiresAt(refreshExpiresAt);
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user)
                .flatMap(savedUser -> {
                    Mono<Void> auditMono = isRegistration
                            ? auditService.logRegistrationSuccess(savedUser.getUsername(), ipAddress)
                            : auditService.logLoginSuccess(savedUser.getUsername(), ipAddress);

                    return auditMono.thenReturn(AuthResponse.success(
                            accessToken,
                            refreshToken,
                            jwtService.getAccessTokenExpirationSeconds(),
                            savedUser.getUsername(),
                            savedUser.getRole()
                    ));
                });
    }

    /**
     * Validate access token and return user info.
     */
    public Mono<User> validateAccessToken(String token) {
        try {
            String username = jwtService.extractUsername(token);
            return userRepository.findByUsername(username)
                    .filter(user -> user.getEnabled() && !user.isAccountLocked());
        } catch (Exception e) {
            return Mono.empty();
        }
    }
}
