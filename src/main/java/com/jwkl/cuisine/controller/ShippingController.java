package com.jwkl.cuisine.controller;

import com.jwkl.cuisine.entity.ShippingBox;
import com.jwkl.cuisine.repository.ShippingBoxRepository;
import com.jwkl.cuisine.service.ShippingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/shipping")
public class ShippingController {

    @Autowired
    private ShippingBoxRepository shippingBoxRepository;

    @Autowired
    private ShippingService shippingService;

    // ===== 箱型管理 API (後台) =====

    /**
     * 查詢所有啟用中的箱型（後台管理用）
     */
    @GetMapping("/boxes")
    public ResponseEntity<List<ShippingBox>> getAllBoxes() {
        return ResponseEntity.ok(
                shippingBoxRepository.findByIsActiveTrueOrderByCarrierAscMaxPointsAsc());
    }

    /**
     * 新增箱型（後台管理）
     */
    @PostMapping("/boxes")
    public ResponseEntity<ShippingBox> createBox(@RequestBody ShippingBox box) {
        return ResponseEntity.ok(shippingBoxRepository.save(box));
    }

    /**
     * 更新箱型（後台管理）
     */
    @PutMapping("/boxes/{id}")
    public ResponseEntity<?> updateBox(@PathVariable Integer id, @RequestBody ShippingBox updated) {
        Optional<ShippingBox> opt = shippingBoxRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        ShippingBox box = opt.get();
        box.setName(updated.getName());
        box.setCarrier(updated.getCarrier());
        box.setMaxPoints(updated.getMaxPoints());
        box.setMaxWeightG(updated.getMaxWeightG());
        box.setPrice(updated.getPrice());
        box.setIsActive(updated.getIsActive());
        return ResponseEntity.ok(shippingBoxRepository.save(box));
    }

    /**
     * 停用箱型（後台管理）
     */
    @DeleteMapping("/boxes/{id}")
    public ResponseEntity<?> deactivateBox(@PathVariable Integer id) {
        Optional<ShippingBox> opt = shippingBoxRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        ShippingBox box = opt.get();
        box.setIsActive(false);
        shippingBoxRepository.save(box);
        return ResponseEntity.ok(Map.of("status", "success", "message", "箱型已停用"));
    }

    // ===== 自動裝箱試算 API =====

    /**
     * 依訂單與貨運商自動試算建議箱型與運費
     * POST /api/v1/shipping/calculate
     * Body: { "orderId": "S000055", "carrier": "black_cat" }
     */
    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculate(@RequestBody Map<String, String> body) {
        String orderId = body.get("orderId");
        String carrier = body.get("carrier");
        if (orderId == null || carrier == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "orderId 與 carrier 為必填參數"));
        }
        Map<String, Object> result = shippingService.calculateRecommendedBox(orderId, carrier);
        return ResponseEntity.ok(result);
    }
}
