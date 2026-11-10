package com.jwkl.cuisine.controller;

import com.jwkl.cuisine.entity.Menu;
import com.jwkl.cuisine.entity.SystemLog;
import com.jwkl.cuisine.repository.MenuRepository;
import com.jwkl.cuisine.repository.SystemLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.jwkl.cuisine.service.ImageStorageService;

@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private SystemLogRepository systemLogRepository;

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
        
        String productId = menu.getProductId().trim();
        menu.setProductId(productId);

        // 確保為英數檔名對齊
        if (menu.getImageFilename() == null || menu.getImageFilename().trim().isEmpty()) {
            menu.setImageFilename(productId + ".jpg"); // 預設以 product_id.jpg 當作檔名對應
        }

        // 防呆保護：若該商品已存在於資料庫中，編輯菜單資訊時強制保留原有的庫存量 (stock) 與庫存防守開關 (isStockManaged)
        // 避免編輯菜單時被預設值 (0 與 false) 覆蓋而導致庫存歸零或庫存防守失效
        Optional<Menu> existingOpt = menuRepository.findById(productId);
        if (existingOpt.isPresent()) {
            Menu existing = existingOpt.get();
            menu.setStock(existing.getStock());
            menu.setIsStockManaged(existing.getIsStockManaged());
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
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> updateStock(
            @PathVariable String productId, 
            @RequestParam Integer stock,
            @RequestParam(required = false) Boolean isStockManaged) {
        
        Optional<Menu> menuOpt = menuRepository.findByProductIdForUpdate(productId);
        if (menuOpt.isPresent()) {
            Menu menu = menuOpt.get();
            Integer oldStock = menu.getStock();
            Boolean oldManaged = menu.getIsStockManaged();

            menu.setStock(stock);
            if (isStockManaged != null) {
                menu.setIsStockManaged(isStockManaged);
            }
            Menu saved = menuRepository.save(menu);

            // 寫入庫存盤點操作日誌
            saveStockLog("STOCK_UPDATE", "INFO", String.format(
                    "單品庫存盤點：品項 [%s %s] 實體庫存 %d -> %d，庫存防守 %s -> %s",
                    saved.getProductId(), saved.getName(),
                    oldStock, saved.getStock(),
                    oldManaged, saved.getIsStockManaged()
            ));

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
        
        Optional<Menu> menuOpt = menuRepository.findByProductIdForUpdate(productId);
        if (menuOpt.isPresent()) {
            Menu menu = menuOpt.get();
            int oldStock = menu.getStock() != null ? menu.getStock() : 0;
            int newStock = oldStock + qty;
            menu.setStock(newStock);
            Menu updated = menuRepository.save(menu);

            // 寫入入庫登記操作日誌
            saveStockLog("STOCK_ADD", "INFO", String.format(
                    "商品入庫登記：品項 [%s %s] 入庫 +%d，實體庫存 %d -> %d (庫存防守: %s)",
                    updated.getProductId(), updated.getName(),
                    qty, oldStock, updated.getStock(), updated.getIsStockManaged()
            ));

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
        List<String> changeDetails = new ArrayList<>();
        for (java.util.Map<String, Object> update : updates) {
            String productId = (String) update.get("productId");
            if (productId == null) continue;
            
            Object stockObj = update.get("stock");
            Object isManagedObj = update.get("isStockManaged");
            
            Optional<Menu> menuOpt = menuRepository.findByProductIdForUpdate(productId);
            if (menuOpt.isPresent()) {
                Menu menu = menuOpt.get();
                Integer oldStock = menu.getStock();
                Boolean oldManaged = menu.getIsStockManaged();

                Integer newStock = stockObj != null ? Integer.parseInt(stockObj.toString()) : oldStock;
                Boolean newManaged = isManagedObj != null ? Boolean.parseBoolean(isManagedObj.toString()) : oldManaged;

                boolean changed = !Objects.equals(oldStock, newStock) || !Objects.equals(oldManaged, newManaged);
                if (changed) {
                    menu.setStock(newStock);
                    menu.setIsStockManaged(newManaged);
                    menuRepository.save(menu);
                    changeDetails.add(String.format("[%s %s: 庫存 %d->%d, 防守 %s->%s]",
                            menu.getProductId(), menu.getName(), oldStock, newStock, oldManaged, newManaged));
                }
            }
        }

        if (!changeDetails.isEmpty()) {
            saveStockLog("STOCK_BATCH_UPDATE", "INFO", "批次庫存設定變更：" + String.join("；", changeDetails));
        }

        return ResponseEntity.ok("{\"status\":\"success\",\"message\":\"批次庫存更新成功！\"}");
    }

    private void saveStockLog(String action, String level, String message) {
        try {
            SystemLog log = new SystemLog();
            log.setAction(action);
            log.setLogLevel(level);
            log.setMessage(message);
            systemLogRepository.save(log);
            System.out.println("[" + action + "] " + message);
        } catch (Exception e) {
            System.err.println("[SystemLog] 寫入庫存日誌失敗: " + e.getMessage());
        }
    }
}
