package com.smartquiz.security;

import com.smartquiz.entity.User;
import com.smartquiz.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String enterpriseId) throws UsernameNotFoundException {
        User user = userRepository.findByEnterpriseId(enterpriseId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + enterpriseId));

        return new org.springframework.security.core.userdetails.User(
                user.getEnterpriseId(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
