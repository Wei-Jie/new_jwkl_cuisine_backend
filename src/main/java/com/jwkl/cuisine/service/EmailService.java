package com.jwkl.cuisine.service;

import com.jwkl.cuisine.entity.Order;
import com.jwkl.cuisine.entity.OrderItem;
import com.jwkl.cuisine.entity.Menu;
import com.jwkl.cuisine.repository.MenuRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final MenuRepository menuRepository;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * 非同步發送訂單通知信件
     *
     * @param order 訂單對象
     * @param items 訂單明細列表
     * @param type  郵件類型: "CREATED" (訂單預約成功), "COMPLETED" (訂單已出貨/完成)
     */
    @Async
    public void sendOrderNotificationEmail(Order order, List<OrderItem> items, String type) {
        if (order.getEmail() == null || order.getEmail().trim().isEmpty()) {
            log.info("訂單 {} 未填寫 Email，略過發送 Email 通知", order.getOrderId());
            return;
        }

        try {
            log.info("開始非同步發送訂單 {} 通知信 ({}) 至 {}", order.getOrderId(), type, order.getEmail());
            
            // 查詢所有選單項目以便獲取中文名稱
            List<Menu> menus = menuRepository.findAll();
            Map<String, String> productNames = menus.stream()
                    .collect(Collectors.toMap(Menu::getProductId, Menu::getName, (a, b) -> a));

            String subject = "";
            String title = "";
            String desc = "";

            if ("CREATED".equalsIgnoreCase(type)) {
                subject = "【小灶私廚】預約訂購確認通知 - 單號: " + order.getOrderId();
                title = "感謝您的預約訂購！";
                desc = "我們已收到您的訂購需求，以下是您的預約明細。小灶私廚會盡快與您聯繫訂單進行確認，謝謝。";
            } else if ("COMPLETED".equalsIgnoreCase(type)) {
                subject = "【小灶私廚】訂單已完成/出貨通知 - 單號: " + order.getOrderId();
                title = "您的美味餐點已出貨/製作完成！";
                desc = "您的訂單已為您準備就緒。若是秤重商品，以下為最終的秤重與計費明細：";
            } else {
                subject = "【小灶私廚】訂單進度通知 - 單號: " + order.getOrderId();
                title = "您的訂單進度更新";
                desc = "以下是您的訂單最新明細資訊：";
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(order.getEmail());
            helper.setSubject(subject);

            // 產生 HTML 明細表格
            StringBuilder itemRows = new StringBuilder();
            for (OrderItem item : items) {
                String pId = item.getProductId();
                String pName = productNames.getOrDefault(pId, pId);
                
                // 折扣商品特殊呈現
                if ("PROD_DISCOUNT".equals(pId)) {
                    pName = "🎁 折扣折抵";
                }

                String priceDisplay = "";
                // 判斷是否為秤重商品
                boolean isWeight = false;
                Menu menu = menus.stream().filter(m -> m.getProductId().equals(pId)).findFirst().orElse(null);
                if (menu != null && (String.valueOf(menu.getPrice()).contains("*") || String.valueOf(menu.getPrice()).contains("重量") || "P3001".equals(pId) || "P3002".equals(pId))) {
                    isWeight = true;
                }

                if (isWeight) {
                    priceDisplay = item.getProductAmt() + " g";
                } else if ("PROD_DISCOUNT".equals(pId)) {
                    priceDisplay = "-";
                } else {
                    priceDisplay = "$" + item.getProductAmt();
                }

                itemRows.append("<tr style='border-bottom: 1px solid #eeeeee;'>")
                        .append("<td style='padding: 10px; color: #333333;'>").append(pName).append("</td>")
                        .append("<td style='padding: 10px; text-align: right; color: #666666;'>").append(priceDisplay).append("</td>")
                        .append("<td style='padding: 10px; text-align: center; color: #666666;'>").append(item.getQty()).append("</td>")
                        .append("<td style='padding: 10px; text-align: right; color: #b45309; font-weight: bold;'>$").append(item.getProductTotalAmt().intValue()).append("</td>")
                        .append("</tr>");
            }

            String htmlContent = "<div style='font-family: \"Helvetica Neue\", Helvetica, Arial, \"Microsoft JhengHei\", sans-serif; background-color: #faf8f5; padding: 20px;'>"
                    + "  <div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 10px rgba(0,0,0,0.05); border: 1px solid #ece6dc; overflow: hidden;'>"
                    + "    <div style='background: linear-gradient(135deg, #b45309, #d97706); padding: 30px 20px; text-align: center; color: #ffffff;'>"
                    + "      <h1 style='margin: 0; font-size: 24px; font-weight: bold; letter-spacing: 1px;'>🥘 小灶私廚</h1>"
                    + "      <p style='margin: 10px 0 0 0; font-size: 15px; opacity: 0.9;'>" + title + "</p>"
                    + "    </div>"
                    + "    <div style='padding: 30px 20px; line-height: 1.6; color: #4b5563;'>"
                    + "      <p style='font-size: 15px; margin-top: 0;'>親愛的 <strong>" + order.getCustomerName() + "</strong> 您好：</p>"
                    + "      <p style='font-size: 14px;'>" + desc + "</p>"
                    + "      "
                    + "      <h3 style='color: #b45309; border-bottom: 2px solid #ece6dc; padding-bottom: 8px; margin-top: 25px; font-size: 16px;'>📋 訂單基本資訊</h3>"
                    + "      <table style='width: 100%; border-collapse: collapse; font-size: 14px; margin-bottom: 20px;'>"
                    + "        <tr><td style='padding: 6px 0; color: #8c857b; width: 120px;'>訂單編號：</td><td style='padding: 6px 0; font-weight: bold; color: #1f2937;'>" + order.getOrderId() + "</td></tr>"
                    + "        <tr><td style='padding: 6px 0; color: #8c857b;'>下單日期：</td><td style='padding: 6px 0; color: #1f2937;'>" + order.getOrderDate() + "</td></tr>"
                    + "        <tr><td style='padding: 6px 0; color: #8c857b;'>聯絡電話：</td><td style='padding: 6px 0; color: #1f2937;'>" + order.getPhone() + "</td></tr>"
                    + "        <tr><td style='padding: 6px 0; color: #8c857b;'>付款狀態：</td><td style='padding: 6px 0; color: #1f2937;'><span style='background-color: " + ("已付款".equals(order.getPaymentStatus()) ? "#dcfce7; color: #15803d;" : "#fef3c7; color: #b45309;") + " padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: bold;'>" + order.getPaymentStatus() + "</span></td></tr>"
                    + "      </table>"
                    + "      "
                    + "      <h3 style='color: #b45309; border-bottom: 2px solid #ece6dc; padding-bottom: 8px; font-size: 16px;'>🍳 訂購商品明細</h3>"
                    + "      <table style='width: 100%; border-collapse: collapse; font-size: 14px;'>"
                    + "        <thead>"
                    + "          <tr style='background-color: #fdfaf6; border-bottom: 2px solid #ece6dc; color: #6b7280;'>"
                    + "            <th style='padding: 10px; text-align: left;'>品項</th>"
                    + "            <th style='padding: 10px; text-align: right; width: 100px;'>單價/克數</th>"
                    + "            <th style='padding: 10px; text-align: center; width: 60px;'>數量</th>"
                    + "            <th style='padding: 10px; text-align: right; width: 90px;'>小計</th>"
                    + "          </tr>"
                    + "        </thead>"
                    + "        <tbody>"
                    + itemRows.toString()
                    + "        </tbody>"
                    + "      </table>"
                    + "      "
                    + "      <div style='text-align: right; padding: 20px 10px; font-size: 16px; font-weight: bold; color: #1f2937; border-top: 2px solid #ece6dc; margin-top: 10px;'>"
                    + "        訂單總金額：<span style='color: #b45309; font-size: 20px;'>$" + order.getAmount().intValue() + "</span> 元"
                    + "      </div>"
                    + "      "
                    + (order.getNotes() != null && !order.getNotes().trim().isEmpty() ? 
                      ("      <div style='background-color: #fafaf9; border-left: 4px solid #d97706; padding: 12px; margin-top: 20px; font-size: 13px; border-radius: 0 6px 6px 0;'>"
                      + "        <strong style='color: #78350f;'>顧客備註：</strong>" + order.getNotes()
                      + "      </div>") : "")
                    + "    </div>"
                    + "    <div style='background-color: #faf8f5; border-top: 1px solid #ece6dc; padding: 20px; text-align: center; font-size: 12px; color: #9ca3af;'>"
                    + "      此信件為系統自動發送，請勿直接回信。<br/>若有任何訂單問題，歡迎透過社群平台或電話與小灶私廚聯絡。"
                    + "    </div>"
                    + "  </div>"
                    + "</div>";

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("訂單 {} 通知信寄送成功！", order.getOrderId());

        } catch (Exception e) {
            log.error("寄送訂單 {} 通知信時發生異常: ", order.getOrderId(), e);
        }
    }
}
