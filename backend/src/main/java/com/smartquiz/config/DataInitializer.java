package com.smartquiz.config;

import com.smartquiz.entity.*;
import com.smartquiz.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private UserRepository userRepository;
    @Autowired private TechnologyStackRepository stackRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private UserStackMappingRepository userStackMappingRepository;

    @Override
    public void run(String... args) {
        if (stackRepository.count() > 0) {
            seedParticipantUsers();
            return; // Already initialized
        }

        // Create Technology Stacks
        Map<String, TechnologyStack> stacks = new LinkedHashMap<>();
        String[] stackNames = {"Spring Cloud", "Spring Boot", "Spring Core", "Spring MVC & REST", "Spring ORM & Data JPA", "Core Java"};
        for (String name : stackNames) {
            TechnologyStack stack = new TechnologyStack();
            stack.setStackName(name);
            stacks.put(name, stackRepository.save(stack));
        }

        // Create Topics for Spring Cloud
        TechnologyStack springCloud = stacks.get("Spring Cloud");
        createTopic("Introduction to Spring Cloud", springCloud);
        createTopic("Service Discovery design pattern - Eureka Server & Discovery Client", springCloud);
        createTopic("Eureka Heartbeats & Self Preservation", springCloud);
        createTopic("Spring Cloud Loadbalancer", springCloud);
        createTopic("Spring Cloud OpenFeign", springCloud);
        createTopic("Resilience4J- Circuit Breaker", springCloud);
        createTopic("Spring Boot Actuator", springCloud);

        // Topics for Spring Boot
        TechnologyStack springBoot = stacks.get("Spring Boot");
        createTopic("Spring Boot Auto-Configuration", springBoot);
        createTopic("Spring Boot Starters", springBoot);
        createTopic("Embedded Server Configuration", springBoot);
        createTopic("Application Properties", springBoot);
        createTopic("Spring Boot Testing", springBoot);

        // Topics for other stacks
        for (String stackName : new String[]{"Spring Core", "Spring MVC & REST", "Spring ORM & Data JPA", "Core Java"}) {
            TechnologyStack s = stacks.get(stackName);
            createTopic("Introduction to " + stackName, s);
            createTopic("Core Concepts of " + stackName, s);
            createTopic("Advanced Topics in " + stackName, s);
        }

        // Create Admin user
        if (!userRepository.existsById("admin")) {
            User admin = new User();
            admin.setEnterpriseId("admin");
            admin.setPassword("admin123");
            admin.setFullName("System Admin");
            admin.setRole(User.Role.ADMIN);
            userRepository.save(admin);
        }

        // Create SME users from the master list
        Map<String, String[]> smeData = new LinkedHashMap<>();
        smeData.put("gaurav.a.bhola",        new String[]{"Gaurav Bhola",          "Spring Cloud", "Spring Boot", "Core Java"});
        smeData.put("birendra.kumar.singh",   new String[]{"Birendra Kumar Singh",  "Spring Boot",  "Spring Core", "Spring Cloud"});
        smeData.put("divya.madhanasekar",     new String[]{"Divya Madhanasekar",    "Spring Core",  "Spring MVC & REST"});
        smeData.put("swati.avinash.nikam",    new String[]{"Swati Nikam",           "Spring MVC & REST", "Spring ORM & Data JPA"});
        smeData.put("indugu.hari.prasad",     new String[]{"Indugu Hari Prasad",    "Spring ORM & Data JPA", "Core Java"});

        for (Map.Entry<String, String[]> entry : smeData.entrySet()) {
            String eid = entry.getKey();
            if (!userRepository.existsById(eid)) {
                User sme = new User();
                sme.setEnterpriseId(eid);
                sme.setPassword("password123");
                sme.setFullName(entry.getValue()[0]);
                sme.setRole(User.Role.SME);
                userRepository.save(sme);

                for (int i = 1; i < entry.getValue().length; i++) {
                    String stackName = entry.getValue()[i];
                    TechnologyStack stack = stacks.get(stackName);
                    if (stack != null) {
                        UserStackMapping mapping = new UserStackMapping();
                        mapping.setUser(sme);
                        mapping.setStack(stack);
                        userStackMappingRepository.save(mapping);
                    }
                }
            }
        }

        seedParticipantUsers();

        System.out.println("✅ Master data initialized successfully!");
        System.out.println("Admin login: admin / admin123");
        System.out.println("SME login examples: gaurav.a.bhola / password123");
    }

    private void seedParticipantUsers() {
        Map<String, String> participantUsers = new LinkedHashMap<>();
        participantUsers.put("participant.aarav", "Aarav Mehta");
        participantUsers.put("participant.anaya", "Anaya Kapoor");
        participantUsers.put("participant.vihaan", "Vihaan Sharma");
        participantUsers.put("participant.kiara", "Kiara Iyer");
        participantUsers.put("participant.advik", "Advik Nair");
        participantUsers.put("participant.saanvi", "Saanvi Reddy");
        participantUsers.put("participant.arjun", "Arjun Patel");
        participantUsers.put("participant.mira", "Mira Desai");
        participantUsers.put("participant.ishaan", "Ishaan Verma");
        participantUsers.put("participant.tara", "Tara Joshi");

        for (Map.Entry<String, String> entry : participantUsers.entrySet()) {
            String enterpriseId = entry.getKey();
            String fullName = entry.getValue();

            User user = userRepository.findByEnterpriseId(enterpriseId).orElseGet(User::new);
            user.setEnterpriseId(enterpriseId);
            user.setPassword("password123");
            user.setFullName(fullName);
            user.setRole(User.Role.PARTICIPANT);
            user.setActive(true);
            userRepository.save(user);
        }
    }

    private void createTopic(String name, TechnologyStack stack) {
        Topic topic = new Topic();
        topic.setTopicName(name);
        topic.setStack(stack);
        topicRepository.save(topic);
    }
}
