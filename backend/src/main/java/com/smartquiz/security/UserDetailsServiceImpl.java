package com.smartquiz.security;

import com.smartquiz.entity.User;
import com.smartquiz.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsPasswordService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads users for authentication. Also acts as the {@link UserDetailsPasswordService}, so Spring Security
 * re-hashes legacy ({noop}) passwords with the current default encoder (BCrypt) after a successful login.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService, UserDetailsPasswordService {

    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String enterpriseId) throws UsernameNotFoundException {
        User user = userRepository.findByEnterpriseId(enterpriseId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + enterpriseId));
        return toUserDetails(user);
    }

    @Override
    @Transactional
    public UserDetails updatePassword(UserDetails userDetails, String newEncodedPassword) {
        User user = userRepository.findByEnterpriseId(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userDetails.getUsername()));
        user.setPassword(newEncodedPassword);
        userRepository.save(user);
        log.info("Upgraded password hash for user {}", user.getEnterpriseId());
        return toUserDetails(user);
    }

    private UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.withUsername(user.getEnterpriseId())
                .password(user.getPassword())
                .disabled(!user.isActive())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }
}
