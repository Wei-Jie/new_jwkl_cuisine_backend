package com.jwkl.cuisine.controller;

import com.jwkl.cuisine.entity.Faq;
import com.jwkl.cuisine.repository.FaqRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/faqs")
public class FaqController {

    @Autowired
    private FaqRepository faqRepository;

    /**
     * 獲取所有常見問題，並依據排序權重 sort_order 升序排列 (前台/後台共用)
     */
    @GetMapping
    public ResponseEntity<List<Faq>> getFaqs() {
        List<Faq> faqs = faqRepository.findAllByOrderBySortOrderAsc();
        return ResponseEntity.ok(faqs);
    }

    /**
     * 新增或更新常見問題項目 (後台管理，支援圖文連結)
     */
    @PostMapping
    public ResponseEntity<Faq> saveOrUpdateFaq(@RequestBody Faq faq) {
        Faq saved = faqRepository.save(faq);
        return ResponseEntity.ok(saved);
    }

    /**
     * 更新常見問題項目 (後台管理，對接前端 PUT 請求)
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateFaq(@PathVariable Integer id, @RequestBody Faq updatedFaq) {
        return faqRepository.findById(id).map(faq -> {
            faq.setQuestion(updatedFaq.getQuestion());
            faq.setAnswer(updatedFaq.getAnswer());
            faq.setSortOrder(updatedFaq.getSortOrder());
            faq.setImageUrl(updatedFaq.getImageUrl());
            Faq saved = faqRepository.save(faq);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * 刪除常見問題 (後台管理)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFaq(@PathVariable Integer id) {
        Optional<Faq> faq = faqRepository.findById(id);
        if (faq.isPresent()) {
            faqRepository.deleteById(id);
            return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"常見問題已成功刪除！\"}");
        }
        return ResponseEntity.notFound().build();
    }
}
