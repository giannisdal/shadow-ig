-- Existing Graph API databases required publication timestamps. Public pages may omit them.
-- Hibernate ddl-auto=update does not relax existing NOT NULL constraints.
ALTER TABLE IF EXISTS processed_instagram_content ALTER COLUMN published_at DROP NOT NULL;
