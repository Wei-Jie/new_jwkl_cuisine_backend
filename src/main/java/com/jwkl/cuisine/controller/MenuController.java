package com.jwkl.cuisine.controller;

import com.jwkl.cuisine.entity.Menu;
import com.jwkl.cuisine.repository.MenuRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

import com.jwkl.cuisine.service.ImageStorageService;

@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private ImageStorageService imageStorageService;

    /**
     * 查詢所有上架商品 (前台使用)
     */
    @GetMapping
    public ResponseEntity<List<Menu>> getActiveMenus() {
        List<Menu> activeMenus = menuRepository.findByStatus("上架");
        // 過濾掉折扣料號，不顯示在點餐菜單中
        List<Menu> filtered = activeMenus.stream()
                .filter(m -> !"PROD_DISCOUNT".equals(m.getProductId()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(filtered);
    }

    /**
     * 查詢所有商品 (後台管理使用，含下架品項)
     */
    @GetMapping("/all")
    public ResponseEntity<List<Menu>> getAllMenus() {
        return ResponseEntity.ok(menuRepository.findAll());
    }

    /**
     * 新增或更新菜單品項 (後台管理)
     */
    @PostMapping
    public ResponseEntity<?> saveOrUpdateMenu(@RequestBody Menu menu) {
        if (menu.getProductId() == null || menu.getProductId().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"商品料號 (product_id) 不可為空！\"}");
        }
        
        // 確保為英數檔名對齊
        if (menu.getImageFilename() == null || menu.getImageFilename().trim().isEmpty()) {
            menu.setImageFilename(menu.getProductId() + ".jpg"); // 預設以 product_id.jpg 當作檔名對應
        }

        Menu saved = menuRepository.save(menu);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<?> deleteMenu(@PathVariable String productId) {
        Optional<Menu> menuOpt = menuRepository.findById(productId);
        if (menuOpt.isPresent()) {
            Menu menu = menuOpt.get();
            if (menu.getImageUrl() != null && !menu.getImageUrl().trim().isEmpty()) {
                imageStorageService.deleteImageByUrl(menu.getImageUrl());
            }
            menuRepository.deleteById(productId);
            return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"品項已成功刪除！\"}");
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 直接更新/盤點商品庫存與啟用開關 (後台管理)
     */
    @PutMapping("/{productId}/stock")
    public ResponseEntity<?> updateStock(
            @PathVariable String productId, 
            @RequestParam Integer stock,
            @RequestParam(required = false) Boolean isStockManaged) {
        
        Optional<Menu> menuOpt = menuRepository.findById(productId);
        if (menuOpt.isPresent()) {
            Menu menu = menuOpt.get();
            menu.setStock(stock);
            if (isStockManaged != null) {
                menu.setIsStockManaged(isStockManaged);
            }
            Menu saved = menuRepository.save(menu);
            return ResponseEntity.ok(saved);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 累加/入庫商品庫存 (後台管理)
     */
    @PostMapping("/{productId}/stock/add")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> addStock(
            @PathVariable String productId, 
            @RequestParam Integer qty) {
        
        Optional<Menu> menuOpt = menuRepository.findById(productId);
        if (menuOpt.isPresent()) {
            menuRepository.addStock(productId, qty);
            Menu updated = menuRepository.findById(productId).get();
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 批次更新商品庫存與啟用開關 (後台管理)
     */
    @PutMapping("/stock/batch")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> batchUpdateStock(@RequestBody List<java.util.Map<String, Object>> updates) {
        for (java.util.Map<String, Object> update : updates) {
            String productId = (String) update.get("productId");
            if (productId == null) continue;
            
            Object stockObj = update.get("stock");
            Object isManagedObj = update.get("isStockManaged");
            
            Optional<Menu> menuOpt = menuRepository.findById(productId);
            if (menuOpt.isPresent()) {
                Menu menu = menuOpt.get();
                if (stockObj != null) {
                    menu.setStock(Integer.parseInt(stockObj.toString()));
                }
                if (isManagedObj != null) {
                    menu.setIsStockManaged(Boolean.parseBoolean(isManagedObj.toString()));
                }
                menuRepository.save(menu);
            }
        }
        return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"批次庫存更新成功！\"}");
    }
}
