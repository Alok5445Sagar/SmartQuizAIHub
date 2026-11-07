package com.smartquiz;

import com.smartquiz.entity.*;
import com.smartquiz.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
@Transactional
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
@org.springframework.context.annotation.Import(com.smartquiz.config.TestConfig.class)

public abstract class BaseTest {

    /**
     * Uses an existing MySQL when TEST_DB_URL is set (local dev), otherwise starts a throwaway
     * Testcontainers MySQL once per JVM (CI). Each Spring context cleans and re-migrates the schema.
     */
    @org.springframework.test.context.DynamicPropertySource
    static void datasource(org.springframework.test.context.DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
            return;
        }
        org.testcontainers.containers.MySQLContainer<?> mysql = MySqlContainerHolder.get();
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    private static final class MySqlContainerHolder {
        private static org.testcontainers.containers.MySQLContainer<?> container;

        static synchronized org.testcontainers.containers.MySQLContainer<?> get() {
            if (container == null) {
                container = new org.testcontainers.containers.MySQLContainer<>("mysql:8.4")
                        .withDatabaseName("smart_quiz_hub_test");
                container.start();
            }
            return container;
        }
    }

    @Autowired protected UserRepository userRepository;
    @Autowired protected TechnologyStackRepository stackRepository;
    @Autowired protected TopicRepository topicRepository;
    @Autowired protected MCQRepository mcqRepository;
    @Autowired protected UserStackMappingRepository userStackMappingRepository;

    protected User createSME(String enterpriseId, String fullName) {
        User u = new User();
        u.setEnterpriseId(enterpriseId);
        u.setPassword("{noop}password123");
        u.setFullName(fullName);
        u.setRole(User.Role.SME);
        u.setActive(true);
        return userRepository.save(u);
    }

    protected User createAdmin(String enterpriseId) {
        User u = new User();
        u.setEnterpriseId(enterpriseId);
        u.setPassword("{noop}admin123");
        u.setFullName("Admin User");
        u.setRole(User.Role.ADMIN);
        u.setActive(true);
        return userRepository.save(u);
    }

    protected TechnologyStack createStack(String name) {
        return stackRepository.findByStackName(name)
                .orElseGet(() -> {
                    TechnologyStack s = new TechnologyStack();
                    s.setStackName(name);
                    return stackRepository.save(s);
                });
    }

    protected Topic createTopic(String name, TechnologyStack stack) {
        return topicRepository.findByStack(stack).stream()
                .filter(t -> t.getTopicName().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    Topic t = new Topic();
                    t.setTopicName(name);
                    t.setStack(stack);
                    return topicRepository.save(t);
                });
    }

    protected MCQ createMCQ(User creator, TechnologyStack stack, Topic topic, MCQ.Status status) {
        MCQ mcq = new MCQ();
        // Use unique stem per test to avoid duplicate conflicts
        mcq.setQuestionStem("What is " + stack.getStackName() + " " +
                System.nanoTime() + "?");
        mcq.setOptionA("Option A");
        mcq.setOptionB("Option B");
        mcq.setOptionC("Option C");
        mcq.setOptionD("Option D");
        mcq.setCorrectAnswer("A");
        mcq.setDifficulty(MCQ.Difficulty.Easy);
        mcq.setQuestionType(MCQ.QuestionType.MCQ);
        mcq.setStack(stack);
        mcq.setTopic(topic);
        mcq.setCreator(creator);
        mcq.setStatus(status);
        return mcqRepository.save(mcq);
    }
}