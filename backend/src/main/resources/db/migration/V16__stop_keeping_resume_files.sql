-- The original resume file and its extracted text are only needed while the resume is read into a profile.
-- New uploads keep neither (only the file name, shown on the profile), and existing copies of the text are cleared.
ALTER TABLE resume_files ALTER COLUMN text_content DROP NOT NULL;
UPDATE resume_files SET text_content = NULL;
