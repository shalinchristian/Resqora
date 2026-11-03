-- Demo mode is removed from the app. If the old shared demo account (demo@resqora.app, with a
-- password published in the code) was ever created, remove it and its data so nobody can log in to it.
-- Resumes have no ON DELETE CASCADE from users, so they go first; analyses, AI answers and job
-- suggestions cascade from resumes, and email codes and AI usage cascade from users.
DELETE FROM resumes WHERE user_id IN (SELECT id FROM users WHERE email = 'demo@resqora.app');
DELETE FROM users WHERE email = 'demo@resqora.app';
