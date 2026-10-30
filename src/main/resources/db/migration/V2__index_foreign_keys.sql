-- Postgres does not automatically index foreign key columns (unlike primary keys).
-- Every per-user query (list/view applications, list saved searches) filters on
-- these columns, so without an index each lookup is a full table scan.
CREATE INDEX idx_applications_user_id ON applications (user_id);
CREATE INDEX idx_saved_searches_user_id ON saved_searches (user_id);
