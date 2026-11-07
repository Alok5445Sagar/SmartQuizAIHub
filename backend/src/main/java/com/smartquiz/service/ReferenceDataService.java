package com.smartquiz.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.smartquiz.repository.TechnologyStackRepository;
import com.smartquiz.repository.TopicRepository;

/**
 * Technology stacks and topics are read on almost every page but only change through Flyway migrations,
 * so they are cached (see spring.cache.* in application.properties; entries expire after 10 minutes).
 * Cached values are immutable records, never JPA entities.
 */
@Service
public class ReferenceDataService {

    public record StackOption(Long stackId, String stackName) {
    }

    public record TopicOption(Long topicId, String topicName) {
    }

    private final TechnologyStackRepository stackRepository;
    private final TopicRepository topicRepository;

    public ReferenceDataService(TechnologyStackRepository stackRepository, TopicRepository topicRepository) {
        this.stackRepository = stackRepository;
        this.topicRepository = topicRepository;
    }

    @Cacheable("stacks")
    public List<StackOption> stacks() {
        return stackRepository.findAll(Sort.by("stackId")).stream()
                .map(s -> new StackOption(s.getStackId(), s.getStackName()))
                .toList();
    }

    @Cacheable("topics")
    public List<TopicOption> topics(Long stackId) {
        return topicRepository.findByStackStackId(stackId).stream()
                .map(t -> new TopicOption(t.getTopicId(), t.getTopicName()))
                .toList();
    }
}
