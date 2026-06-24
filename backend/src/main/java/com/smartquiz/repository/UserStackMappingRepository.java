package com.smartquiz.repository;

import com.smartquiz.entity.UserStackMapping;
import com.smartquiz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserStackMappingRepository extends JpaRepository<UserStackMapping, Long> {
    List<UserStackMapping> findByUserEnterpriseId(String enterpriseId);
    
    @Query("SELECT DISTINCT usm.user FROM UserStackMapping usm WHERE usm.stack.stackId = :stackId AND usm.user.enterpriseId != :excludeEnterpriseId")
    List<User> findUsersByStackIdExcluding(@Param("stackId") Long stackId, @Param("excludeEnterpriseId") String excludeEnterpriseId);
    
    @Query("SELECT DISTINCT usm.user FROM UserStackMapping usm WHERE usm.stack.stackId = :stackId")
    List<User> findUsersByStackId(@Param("stackId") Long stackId);
}
