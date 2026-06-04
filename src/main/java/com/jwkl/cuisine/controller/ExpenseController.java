package com.jwkl.cuisine.controller;

import com.jwkl.cuisine.entity.Expense;
import com.jwkl.cuisine.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

    @Autowired
    private ExpenseRepository expenseRepository;

    /**
     * 獲取所有支出紀錄，依據日期排序 (後台管理)
     */
    @GetMapping
    public ResponseEntity<List<Expense>> getExpenses() {
        List<Expense> expenses = expenseRepository.findAllByOrderByDateDesc();
        return ResponseEntity.ok(expenses);
    }

    /**
     * 依據日期區間查詢支出 (收支分析用)
     */
    @GetMapping("/range")
    public ResponseEntity<List<Expense>> getExpensesByRange(
            @RequestParam String startDate, 
            @RequestParam String endDate) {
        List<Expense> expenses = expenseRepository.findByDateBetween(startDate, endDate);
        return ResponseEntity.ok(expenses);
    }

    /**
     * 新增或更新支出紀錄 (後台管理)
     */
    @PostMapping
    public ResponseEntity<Expense> saveOrUpdateExpense(@RequestBody Expense expense) {
        Expense saved = expenseRepository.save(expense);
        return ResponseEntity.ok(saved);
    }

    /**
     * 刪除支出紀錄 (後台管理)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteExpense(@PathVariable Integer id) {
        Optional<Expense> expenseOpt = expenseRepository.findById(id);
        if (expenseOpt.isPresent()) {
            expenseRepository.deleteById(id);
            return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"支出紀錄已成功刪除！\"}");
        }
        return ResponseEntity.notFound().build();
    }
}
