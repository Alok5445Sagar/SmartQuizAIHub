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

public abstract class BaseTest {

    @Autowired protected UserRepository userRepository;
    @Autowired protected TechnologyStackRepository stackRepository;
    @Autowired protected TopicRepository topicRepository;
    @Autowired protected MCQRepository mcqRepository;
    @Autowired protected UserStackMappingRepository userStackMappingRepository;

    protected User createSME(String enterpriseId, String fullName) {
        User u = new User();
        u.setEnterpriseId(enterpriseId);
        u.setPassword("password123");
        u.setFullName(fullName);
        u.setRole(User.Role.SME);
        u.setActive(true);
        return userRepository.save(u);
    }

    protected User createAdmin(String enterpriseId) {
        User u = new User();
        u.setEnterpriseId(enterpriseId);
        u.setPassword("admin123");
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
        mcq.setQuestionStem("What is " + stack.getStackName() + "?");
        mcq.setOptionA("Option A");
        mcq.setOptionB("Option B");
        mcq.setOptionC("Option C");
        mcq.setOptionD("Option D");
        mcq.setCorrectAnswer("A");
        mcq.setDifficulty(MCQ.Difficulty.Easy);
        mcq.setStack(stack);
        mcq.setTopic(topic);
        mcq.setCreator(creator);
        mcq.setStatus(status);
        return mcqRepository.save(mcq);
    }
}