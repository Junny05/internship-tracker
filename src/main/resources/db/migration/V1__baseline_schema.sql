CREATE TABLE users (
    id UUID NOT NULL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE saved_searches (
    id UUID NOT NULL PRIMARY KEY,
    keywords VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    user_id UUID NOT NULL REFERENCES users (id)
);

CREATE TABLE applications (
    id UUID NOT NULL PRIMARY KEY,
    company VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    applied_date DATE,
    notes TEXT,
    link VARCHAR(255),
    user_id UUID NOT NULL REFERENCES users (id)
);

CREATE TABLE seen_listings (
    id UUID NOT NULL PRIMARY KEY,
    saved_search_id UUID NOT NULL REFERENCES saved_searches (id),
    external_id VARCHAR(255) NOT NULL,
    seen_at TIMESTAMP NOT NULL,
    UNIQUE (saved_search_id, external_id)
);
