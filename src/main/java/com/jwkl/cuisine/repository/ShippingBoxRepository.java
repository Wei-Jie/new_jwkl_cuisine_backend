package com.jwkl.cuisine.repository;

import com.jwkl.cuisine.entity.ShippingBox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ShippingBoxRepository extends JpaRepository<ShippingBox, Integer> {
    /** 依貨運商查詢啟用中的箱型，由小到大排序（用於自動裝箱演算） */
    List<ShippingBox> findByCarrierAndIsActiveTrueOrderByMaxPointsAsc(String carrier);

    /** 查詢所有啟用中的箱型 */
    List<ShippingBox> findByIsActiveTrueOrderByCarrierAscMaxPointsAsc();
}
