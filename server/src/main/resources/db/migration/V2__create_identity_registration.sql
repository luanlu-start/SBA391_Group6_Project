CREATE TABLE dbo.users (
    id BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_users PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT uq_users_public_id UNIQUE,
    email NVARCHAR(254) NOT NULL CONSTRAINT uq_users_email UNIQUE,
    phone VARCHAR(16) NOT NULL CONSTRAINT uq_users_phone UNIQUE,
    password_hash VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL,
    email_verified_at DATETIME2(6) NULL,
    phone_verified_at DATETIME2(6) NULL,
    fpt_badge BIT NOT NULL DEFAULT 0,
    is_deleted BIT NOT NULL DEFAULT 0,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'DELETED'))
);

CREATE TABLE dbo.roles (
    id BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_roles PRIMARY KEY,
    code VARCHAR(20) NOT NULL CONSTRAINT uq_roles_code UNIQUE,
    CONSTRAINT ck_roles_code CHECK (code IN ('SENDER', 'COURIER', 'MODERATOR', 'ADMIN'))
);
INSERT INTO dbo.roles (code) VALUES ('SENDER'), ('COURIER'), ('MODERATOR'), ('ADMIN');

CREATE TABLE dbo.user_roles (
    user_id BIGINT NOT NULL CONSTRAINT fk_user_roles_user REFERENCES dbo.users(id),
    role_id BIGINT NOT NULL CONSTRAINT fk_user_roles_role REFERENCES dbo.roles(id),
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id)
);

CREATE TABLE dbo.user_profiles (
    user_id BIGINT NOT NULL CONSTRAINT pk_user_profiles PRIMARY KEY
        CONSTRAINT fk_user_profiles_user REFERENCES dbo.users(id),
    full_name NVARCHAR(100) NOT NULL,
    adult_confirmed_at DATETIME2(6) NOT NULL
);

CREATE TABLE dbo.consents (
    id BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_consents PRIMARY KEY,
    user_id BIGINT NOT NULL CONSTRAINT fk_consents_user REFERENCES dbo.users(id),
    policy_type VARCHAR(30) NOT NULL,
    policy_version VARCHAR(50) NOT NULL,
    accepted_at DATETIME2(6) NOT NULL,
    ip VARCHAR(45) NOT NULL,
    CONSTRAINT uq_consents_user_policy UNIQUE (user_id, policy_type, policy_version)
);

CREATE TABLE dbo.otp_codes (
    id BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_otp_codes PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT uq_otp_codes_public_id UNIQUE,
    user_id BIGINT NOT NULL CONSTRAINT fk_otp_codes_user REFERENCES dbo.users(id),
    target NVARCHAR(254) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    code_hash VARCHAR(60) NOT NULL,
    expires_at DATETIME2(6) NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    tries INT NOT NULL DEFAULT 0,
    used_at DATETIME2(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_otp_codes_tries CHECK (tries >= 0)
);
CREATE INDEX ix_otp_codes_user_purpose ON dbo.otp_codes(user_id, purpose, created_at DESC);

CREATE TABLE dbo.blacklist (
    id BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT pk_blacklist PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    value_hash VARCHAR(64) NOT NULL,
    reason NVARCHAR(500) NOT NULL,
    created_by BIGINT NULL CONSTRAINT fk_blacklist_creator REFERENCES dbo.users(id),
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT uq_blacklist_kind_value UNIQUE (kind, value_hash),
    CONSTRAINT ck_blacklist_kind CHECK (kind IN ('ID_NUMBER', 'PHONE', 'DEVICE'))
);
