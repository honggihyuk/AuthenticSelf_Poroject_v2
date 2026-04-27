-- =====================================================================
-- AuthenticSelf V1 — initial schema
-- Creates the four core business tables: users, spaces, furniture, wishlist
-- PRD refs: §3 DB design, §7 class diagram, §6 UC-02 (status enum)
-- All identifiers snake_case, all tables InnoDB + utf8mb4_unicode_ci
-- =====================================================================

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    user_id    VARCHAR(64)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- furniture
-- ---------------------------------------------------------------------
CREATE TABLE furniture (
    furniture_id VARCHAR(64)  NOT NULL,
    type         VARCHAR(32)  NOT NULL,
    style        VARCHAR(64)  NOT NULL,
    size         VARCHAR(100) NOT NULL,
    CONSTRAINT pk_furniture PRIMARY KEY (furniture_id),
    INDEX idx_furniture_type_style (type, style)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- spaces
-- FK user_id -> users.user_id (ON DELETE CASCADE, ON UPDATE CASCADE)
-- per PRD §7 cardinality: User 1 — 0..* Space
-- ---------------------------------------------------------------------
CREATE TABLE spaces (
    room_id       VARCHAR(64)  NOT NULL,
    user_id       VARCHAR(64)  NOT NULL,
    dimensions    VARCHAR(100) NOT NULL,
    main_color    VARCHAR(32)  NOT NULL,
    style         VARCHAR(64)  NOT NULL,
    analysis_date DATETIME     NOT NULL,
    CONSTRAINT pk_spaces PRIMARY KEY (room_id),
    INDEX idx_spaces_user_id (user_id),
    CONSTRAINT fk_spaces_user FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- wishlist
-- FK user_id      -> users.user_id      (CASCADE)
-- FK furniture_id -> furniture.furniture_id (RESTRICT on delete)
-- status ENUM native-typed — non-enum strings rejected at DB level
-- ---------------------------------------------------------------------
CREATE TABLE wishlist (
    wishlist_id  VARCHAR(64)                   NOT NULL,
    user_id      VARCHAR(64)                   NOT NULL,
    furniture_id VARCHAR(64)                   NOT NULL,
    category     VARCHAR(32)                   NOT NULL,
    price        INT                           NOT NULL,
    status       ENUM('Active','Purchased')    NOT NULL DEFAULT 'Active',
    created_at   TIMESTAMP                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT pk_wishlist PRIMARY KEY (wishlist_id),
    INDEX idx_wishlist_user_id (user_id),
    CONSTRAINT fk_wishlist_user FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_wishlist_furniture FOREIGN KEY (furniture_id)
        REFERENCES furniture (furniture_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
