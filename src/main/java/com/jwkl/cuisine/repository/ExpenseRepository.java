package com.jwkl.cuisine.repository;

import com.jwkl.cuisine.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Integer> {
    
    // 依據支出日期排序查詢
    List<Expense> findAllByOrderByDateDesc();
    
    // 依據日期區間查詢
    List<Expense> findByDateBetween(String startDate, String endDate);
}
