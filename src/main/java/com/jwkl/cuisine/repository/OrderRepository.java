package com.jwkl.cuisine.repository;

import com.jwkl.cuisine.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    
    // 依據訂單編號 (S000001) 查詢
    Optional<Order> findByOrderId(String orderId);
    
    // 依據手機和訂單編號查詢 (訂單追蹤功能)
    Optional<Order> findByPhoneAndOrderId(String phone, String orderId);
    
    // 依據手機查詢所有預約單
    List<Order> findByPhoneOrderByCreatedAtDesc(String phone);
    
    // 依據狀態查詢 (待確認/已接單)
    List<Order> findByStatusOrderByCreatedAtDesc(String status);

    // 【核心高階方案】從 Supabase PostgreSQL 中極速、安全、原子性地獲取下一個遞增 Sequence 值
    @Query(value = "SELECT nextval('order_id_seq')", nativeQuery = true)
    Long getNextOrderIdSeqValue();
}
