-- Reference data: technology stacks and topics.
-- Idempotent so it can run on a database that was previously seeded by hand or by the old DataInitializer.

INSERT INTO technology_stacks (stack_name)
SELECT s.stack_name FROM (
    SELECT 'Spring Cloud' AS stack_name UNION ALL
    SELECT 'Spring Boot' UNION ALL
    SELECT 'Spring Core' UNION ALL
    SELECT 'Spring MVC & REST' UNION ALL
    SELECT 'Spring ORM & Data JPA' UNION ALL
    SELECT 'Core Java'
) s
WHERE NOT EXISTS (SELECT 1 FROM technology_stacks t WHERE t.stack_name = s.stack_name);

INSERT INTO topics (topic_name, stack_id)
SELECT v.topic_name, st.stack_id
FROM (
    SELECT 'Spring Cloud' AS stack_name, 'Introduction to Spring Cloud' AS topic_name UNION ALL
    SELECT 'Spring Cloud', 'Service Discovery design pattern - Eureka Server & Discovery Client' UNION ALL
    SELECT 'Spring Cloud', 'Eureka Heartbeats & Self Preservation' UNION ALL
    SELECT 'Spring Cloud', 'Spring Cloud Loadbalancer' UNION ALL
    SELECT 'Spring Cloud', 'Spring Cloud OpenFeign' UNION ALL
    SELECT 'Spring Cloud', 'Resilience4J- Circuit Breaker' UNION ALL
    SELECT 'Spring Cloud', 'Spring Boot Actuator' UNION ALL
    SELECT 'Spring Boot', 'Spring Boot Auto-Configuration' UNION ALL
    SELECT 'Spring Boot', 'Spring Boot Starters' UNION ALL
    SELECT 'Spring Boot', 'Embedded Server Configuration' UNION ALL
    SELECT 'Spring Boot', 'Application Properties & Profiles' UNION ALL
    SELECT 'Spring Boot', 'Spring Boot Testing' UNION ALL
    SELECT 'Spring Core', 'Introduction to Spring Core' UNION ALL
    SELECT 'Spring Core', 'Dependency Injection & IoC' UNION ALL
    SELECT 'Spring Core', 'Bean Lifecycle & Scopes' UNION ALL
    SELECT 'Spring Core', 'Spring AOP' UNION ALL
    SELECT 'Spring MVC & REST', 'Introduction to Spring MVC' UNION ALL
    SELECT 'Spring MVC & REST', 'REST Controllers & Mappings' UNION ALL
    SELECT 'Spring MVC & REST', 'Exception Handling in REST' UNION ALL
    SELECT 'Spring MVC & REST', 'Request & Response DTOs' UNION ALL
    SELECT 'Spring ORM & Data JPA', 'Introduction to Spring Data JPA' UNION ALL
    SELECT 'Spring ORM & Data JPA', 'Entity Relationships' UNION ALL
    SELECT 'Spring ORM & Data JPA', 'JPQL & Native Queries' UNION ALL
    SELECT 'Spring ORM & Data JPA', 'Transactions & Locking' UNION ALL
    SELECT 'Core Java', 'Introduction to Core Java' UNION ALL
    SELECT 'Core Java', 'OOP Concepts' UNION ALL
    SELECT 'Core Java', 'Collections Framework' UNION ALL
    SELECT 'Core Java', 'Java 8+ Features (Streams, Lambdas)'
) v
JOIN technology_stacks st ON st.stack_name = v.stack_name
WHERE NOT EXISTS (
    SELECT 1 FROM topics t WHERE t.stack_id = st.stack_id AND t.topic_name = v.topic_name
);
