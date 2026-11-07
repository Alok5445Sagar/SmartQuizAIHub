-- DEV ONLY: demo accounts. Loaded only when the 'dev' profile adds classpath:db/dev to spring.flyway.locations.
-- Never enable this location in production.
-- Passwords are BCrypt hashes of the documented demo passwords (admin: admin123, others: password123).
-- Change them after first login on any shared environment.

INSERT IGNORE INTO users (enterprise_id, full_name, password, role, active, preferred_language) VALUES
('admin',                'System Admin',         '{bcrypt}$2b$10$X47DmI4fahrKa8FrZQQ3I.0wkGczZikUrLEKviXPxRO9zZWXnvj7a',    'ADMIN',       TRUE, 'en'),
('gaurav.a.bhola',       'Gaurav Bhola',         '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'SME',         TRUE, 'en'),
('birendra.kumar.singh', 'Birendra Kumar Singh', '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'SME',         TRUE, 'en'),
('divya.madhanasekar',   'Divya Madhanasekar',   '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'SME',         TRUE, 'en'),
('swati.avinash.nikam',  'Swati Nikam',          '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'SME',         TRUE, 'en'),
('indugu.hari.prasad',   'Indugu Hari Prasad',   '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'SME',         TRUE, 'en'),
('participant.aarav',    'Aarav Mehta',          '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'PARTICIPANT', TRUE, 'en'),
('participant.anaya',    'Anaya Kapoor',         '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'PARTICIPANT', TRUE, 'en'),
('participant.vihaan',   'Vihaan Sharma',        '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'PARTICIPANT', TRUE, 'en'),
('participant.kiara',    'Kiara Iyer',           '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'PARTICIPANT', TRUE, 'en'),
('participant.advik',    'Advik Nair',           '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'PARTICIPANT', TRUE, 'en'),
('participant.saanvi',   'Saanvi Reddy',         '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'PARTICIPANT', TRUE, 'en'),
('participant.arjun',    'Arjun Patel',          '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'PARTICIPANT', TRUE, 'en'),
('participant.mira',     'Mira Desai',           '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'PARTICIPANT', TRUE, 'en'),
('participant.ishaan',   'Ishaan Verma',         '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'PARTICIPANT', TRUE, 'en'),
('participant.tara',     'Tara Joshi',           '{bcrypt}$2b$10$ErxfCLmP01gR7JwQBv81QOvh6NJi884NQMvRYVmzkwP3/3lnDQXb2', 'PARTICIPANT', TRUE, 'en');

INSERT INTO user_stack_mapping (enterprise_id, stack_id)
SELECT m.enterprise_id, st.stack_id
FROM (
    SELECT 'gaurav.a.bhola' AS enterprise_id, 'Spring Cloud' AS stack_name UNION ALL
    SELECT 'gaurav.a.bhola', 'Spring Boot' UNION ALL
    SELECT 'gaurav.a.bhola', 'Core Java' UNION ALL
    SELECT 'birendra.kumar.singh', 'Spring Boot' UNION ALL
    SELECT 'birendra.kumar.singh', 'Spring Core' UNION ALL
    SELECT 'birendra.kumar.singh', 'Spring Cloud' UNION ALL
    SELECT 'divya.madhanasekar', 'Spring Core' UNION ALL
    SELECT 'divya.madhanasekar', 'Spring MVC & REST' UNION ALL
    SELECT 'swati.avinash.nikam', 'Spring MVC & REST' UNION ALL
    SELECT 'swati.avinash.nikam', 'Spring ORM & Data JPA' UNION ALL
    SELECT 'indugu.hari.prasad', 'Spring ORM & Data JPA' UNION ALL
    SELECT 'indugu.hari.prasad', 'Core Java'
) m
JOIN technology_stacks st ON st.stack_name = m.stack_name
WHERE NOT EXISTS (
    SELECT 1 FROM user_stack_mapping u WHERE u.enterprise_id = m.enterprise_id AND u.stack_id = st.stack_id
);
