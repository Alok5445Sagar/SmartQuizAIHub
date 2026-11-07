-- DEV ONLY: demo accounts. Loaded only when the 'dev' profile adds classpath:db/dev to spring.flyway.locations.
-- Never enable this location in production.

INSERT IGNORE INTO users (enterprise_id, full_name, password, role, active, preferred_language) VALUES
('admin',                'System Admin',         'admin123',    'ADMIN',       TRUE, 'en'),
('gaurav.a.bhola',       'Gaurav Bhola',         'password123', 'SME',         TRUE, 'en'),
('birendra.kumar.singh', 'Birendra Kumar Singh', 'password123', 'SME',         TRUE, 'en'),
('divya.madhanasekar',   'Divya Madhanasekar',   'password123', 'SME',         TRUE, 'en'),
('swati.avinash.nikam',  'Swati Nikam',          'password123', 'SME',         TRUE, 'en'),
('indugu.hari.prasad',   'Indugu Hari Prasad',   'password123', 'SME',         TRUE, 'en'),
('participant.aarav',    'Aarav Mehta',          'password123', 'PARTICIPANT', TRUE, 'en'),
('participant.anaya',    'Anaya Kapoor',         'password123', 'PARTICIPANT', TRUE, 'en'),
('participant.vihaan',   'Vihaan Sharma',        'password123', 'PARTICIPANT', TRUE, 'en'),
('participant.kiara',    'Kiara Iyer',           'password123', 'PARTICIPANT', TRUE, 'en'),
('participant.advik',    'Advik Nair',           'password123', 'PARTICIPANT', TRUE, 'en'),
('participant.saanvi',   'Saanvi Reddy',         'password123', 'PARTICIPANT', TRUE, 'en'),
('participant.arjun',    'Arjun Patel',          'password123', 'PARTICIPANT', TRUE, 'en'),
('participant.mira',     'Mira Desai',           'password123', 'PARTICIPANT', TRUE, 'en'),
('participant.ishaan',   'Ishaan Verma',         'password123', 'PARTICIPANT', TRUE, 'en'),
('participant.tara',     'Tara Joshi',           'password123', 'PARTICIPANT', TRUE, 'en');

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
