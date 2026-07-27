-- Google/OAuth and R2 public URLs can exceed VARCHAR(255).
ALTER TABLE users
    ALTER COLUMN profile_picture_url TYPE VARCHAR(1024);
