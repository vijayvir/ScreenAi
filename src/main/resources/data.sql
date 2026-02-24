-- =====================================================
-- ScreenAI Initial Data (Development Profile)
-- =====================================================

-- Insert default admin user (password: Admin@123)
-- BCrypt hash for 'Admin@123' with cost factor 12
-- Generated using: new BCryptPasswordEncoder(12).encode("Admin@123")
INSERT INTO users (username, password_hash, role, enabled)
SELECT 'admin', '$2a$12$8K1p/a0dL1LXMIgoEDFrwOfMQkLgpaeXCWgZ8HCc9p6yVJm8MN/qG', 'ADMIN', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');
