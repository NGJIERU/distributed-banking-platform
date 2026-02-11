-- Add MFA backup codes column to users table
ALTER TABLE auth.users ADD COLUMN IF NOT EXISTS mfa_backup_codes TEXT;

COMMENT ON COLUMN auth.users.mfa_backup_codes IS 'Comma-separated hashed backup codes for MFA recovery';
