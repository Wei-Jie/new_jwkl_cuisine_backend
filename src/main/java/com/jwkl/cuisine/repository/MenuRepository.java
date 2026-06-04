package com.jwkl.cuisine.repository;

import com.jwkl.cuisine.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MenuRepository extends JpaRepository<Menu, String> {
    // 依據分類查詢已上架商品
    List<Menu> findByCategoryAndStatus(String category, String status);
    
    // 查詢所有已上架商品
    List<Menu> findByStatus(String status);

    // 悲觀寫入鎖：查詢商品並鎖定其 Row，交易異常或結束時資料庫會自動解鎖
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Menu m WHERE m.productId = :productId")
    Optional<Menu> findByProductIdForUpdate(@Param("productId") String productId);

    // 原子增量更新庫存 (累加入庫，執行緒安全)
    @Modifying
    @Query("UPDATE Menu m SET m.stock = m.stock + :qty WHERE m.productId = :productId")
    void addStock(@Param("productId") String productId, @Param("qty") Integer qty);
}
