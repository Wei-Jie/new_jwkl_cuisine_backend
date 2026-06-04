package com.jwkl.cuisine.repository;

import com.jwkl.cuisine.entity.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Integer> {
    
    // 依據時間倒序排列查詢
    List<SystemLog> findAllByOrderByCreatedAtDesc();
}
