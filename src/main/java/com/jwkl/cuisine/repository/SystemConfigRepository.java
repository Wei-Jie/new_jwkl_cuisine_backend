package com.jwkl.cuisine.repository;

import com.jwkl.cuisine.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Integer> {
    
    // 依據 Key 尋找設定
    Optional<SystemConfig> findByConfigKey(String configKey);
}
