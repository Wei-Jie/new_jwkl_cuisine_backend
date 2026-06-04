package com.jwkl.cuisine.repository;

import com.jwkl.cuisine.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FaqRepository extends JpaRepository<Faq, Integer> {
    
    // 依據排序權重 sort_order 升序排列查詢 (數字小優先顯示)
    List<Faq> findAllByOrderBySortOrderAsc();
}
