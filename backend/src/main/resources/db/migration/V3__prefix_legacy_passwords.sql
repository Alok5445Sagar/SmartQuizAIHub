-- Passwords used to be stored in plain text (NoOpPasswordEncoder).
-- Tag them as {noop} so the DelegatingPasswordEncoder can still verify them;
-- each one is re-hashed with BCrypt on that user's next successful login.
UPDATE users SET password = CONCAT('{noop}', password) WHERE password NOT LIKE '{%}%';
