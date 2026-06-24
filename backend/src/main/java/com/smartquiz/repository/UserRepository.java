package com.smartquiz.repository;

import com.smartquiz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEnterpriseId(String enterpriseId);
    List<User> findByRoleInOrderByFullNameAsc(Collection<User.Role> roles);
}
