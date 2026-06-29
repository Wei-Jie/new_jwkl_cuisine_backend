package com.jwkl.cuisine.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "menus")
public class Menu {
    @Id
    @Column(name = "product_id", length = 50)
    private String productId; // 商品料號 (PK, 例如 PROD_001)

    @Column(nullable = false, length = 50)
    private String category; // 商品分類 (麵食/小菜/料理包/滷味)

    @Column(nullable = false, length = 100)
    private String name; // 菜名

    @Column(nullable = false, length = 50)
    private String price; // 單價 (如 240, 或 1.4*重量)

    @Column(name = "min_qty")
    private Integer minQty = 1; // 最小訂購數量

    private String description; // 商品描述

    private String note; // 備註 (內部管理)

    @Column(name = "image_filename", length = 100)
    private String imageFilename; // 圖片檔名對應

    @Column(name = "image_url")
    private String imageUrl; // 雲端圖片網址 (Supabase Storage)

    @Column(length = 20)
    private String status = "上架"; // 狀態 (上架/下架)

    @Column(nullable = false)
    private Integer stock = 0; // 目前庫存量 (實體總庫存 all_stock)

    @Column(name = "is_stock_managed", nullable = false)
    private Boolean isStockManaged = false; // 是否啟用庫存管理

    @Column(name = "shipping_points")
    private Integer shippingPoints; // 配送點數（體積指標，NULL=秤重商品由店主出貨時確認）

    @Column(name = "weight_g")
    private Integer weightG; // 商品重量 (g)，秤重商品可為 NULL

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
