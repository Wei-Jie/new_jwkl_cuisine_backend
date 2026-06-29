package com.jwkl.cuisine.service;

import com.jwkl.cuisine.entity.OrderItem;
import com.jwkl.cuisine.entity.ShippingBox;
import com.jwkl.cuisine.entity.Menu;
import com.jwkl.cuisine.repository.MenuRepository;
import com.jwkl.cuisine.repository.OrderItemRepository;
import com.jwkl.cuisine.repository.ShippingBoxRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ShippingService {

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private ShippingBoxRepository shippingBoxRepository;

    /**
     * 自動裝箱演算（點數制 + 重量檢查混合方案）
     *
     * @param orderId 訂單編號
     * @param carrier 貨運商 ('black_cat' | 'seven_eleven')
     * @return 試算結果 Map
     */
    public Map<String, Object> calculateRecommendedBox(String orderId, String carrier) {
        Map<String, Object> result = new HashMap<>();
        List<String> warnings = new ArrayList<>();

        // Step 1: 取得訂單所有明細
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        if (items.isEmpty()) {
            result.put("error", "找不到訂單明細：" + orderId);
            return result;
        }

        int totalPoints = 0;
        int totalWeightG = 0;
        boolean hasNullPoints = false;

        // Step 2 & 3: 計算總點數與總重量
        for (OrderItem item : items) {
            // 跳過折扣料號
            if ("PROD_DISCOUNT".equals(item.getProductId())) continue;

            int qty = item.getQty() != null ? item.getQty() : 1;
            Optional<Menu> menuOpt = menuRepository.findById(item.getProductId());

            if (menuOpt.isPresent()) {
                Menu menu = menuOpt.get();

                // 點數計算
                if (menu.getShippingPoints() != null) {
                    totalPoints += menu.getShippingPoints() * qty;
                } else {
                    // 無點數（秤重商品或未設定）
                    hasNullPoints = true;
                }

                // 重量計算
                if (menu.getWeightG() != null) {
                    totalWeightG += menu.getWeightG() * qty;
                } else {
                    // 秤重商品：以訂單實際 product_amt 作為重量（單位視為公克）
                    if (item.getProductAmt() != null) {
                        totalWeightG += item.getProductAmt().intValue() * qty;
                    }
                }
            }
        }

        // 含無點數商品時提示
        if (hasNullPoints) {
            warnings.add("此訂單含有未設定配送點數的商品（如秤重商品），請出貨時人工確認箱型是否足夠。");
        }

        // Step 4: 依貨運商取得箱型清單（由小到大）
        List<ShippingBox> boxes = shippingBoxRepository
                .findByCarrierAndIsActiveTrueOrderByMaxPointsAsc(carrier);

        if (boxes.isEmpty()) {
            result.put("error", "找不到貨運商 [" + carrier + "] 的有效箱型設定");
            return result;
        }

        // Step 5: 找出第一個符合點數與重量的箱型
        ShippingBox recommended = null;
        for (ShippingBox box : boxes) {
            boolean pointsOk = totalPoints <= box.getMaxPoints();
            boolean weightOk = totalWeightG <= box.getMaxWeightG();

            if (pointsOk && weightOk) {
                recommended = box;
                break;
            }

            // 點數沒超但重量超限 → 繼續找下一個
            if (pointsOk && !weightOk) {
                warnings.add("重量 " + (totalWeightG / 1000.0) + " kg 超過此箱型上限，已自動升級。");
            }
        }

        // Step 6: 特殊情況處理
        if (recommended == null) {
            // 所有箱型都裝不下
            ShippingBox largest = boxes.get(boxes.size() - 1);
            warnings.add("訂單總量超過最大箱型上限（" + largest.getMaxPoints() + " 點 / " +
                    (largest.getMaxWeightG() / 1000.0) + " kg），建議分多箱寄送。");
            recommended = largest; // 回傳最大箱型作為參考
        }

        result.put("recommendedBoxId", recommended.getId());
        result.put("recommendedBoxName", recommended.getName());
        result.put("suggestedFee", recommended.getPrice());
        result.put("totalPoints", totalPoints);
        result.put("totalWeightG", totalWeightG);
        result.put("hasNullPoints", hasNullPoints);
        result.put("warnings", warnings);
        result.put("carrier", carrier);

        return result;
    }
}
