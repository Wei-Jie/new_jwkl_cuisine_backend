package com.jwkl.cuisine.service;

import com.jwkl.cuisine.entity.Order;
import com.jwkl.cuisine.entity.OrderItem;
import com.jwkl.cuisine.entity.Menu;
import com.jwkl.cuisine.repository.MenuRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.util.*;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LineNotifyService {

    // 1. 舊版 LINE Notify (已於 2025 年 4 月停用，保留做為相容回退)
    @Value("${line.notify.token:}")
    private String lineNotifyToken;

    // 2. 新版 LINE Bot (Messaging API) 支援
    @Value("${line.bot.channel-token:}")
    private String lineBotChannelToken;

    @Value("${line.bot.admin-user-id:}")
    private String lineBotAdminUserId;

    // 3. 極速免驗證 Telegram Bot 支援 (極力推薦)
    @Value("${telegram.bot.token:}")
    private String telegramBotToken;

    @Value("${telegram.chat.id:}")
    private String telegramChatId;

    @Autowired
    private MenuRepository menuRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 發送新訂單推播通知至老闆的手機 (支援 LINE Bot, Telegram Bot 雙通道防線)
     */
    public void sendOrderNotification(Order order, List<OrderItem> items) {
        // 建立高質感的統一訊息內文
        String messageText = buildNotificationMessage(order, items);

        // 1. 執行 Telegram Bot 推送 (如果已配置)
        if (telegramBotToken != null && !telegramBotToken.trim().isEmpty() && 
            telegramChatId != null && !telegramChatId.trim().isEmpty()) {
            sendTelegramMessage(messageText);
        }

        // 2. 執行 LINE Bot (Messaging API) 推送 (如果已配置)
        if (lineBotChannelToken != null && !lineBotChannelToken.trim().isEmpty() && 
            lineBotAdminUserId != null && !lineBotAdminUserId.trim().isEmpty()) {
            sendLineBotMessage(messageText);
        }

        // 3. 執行舊版 LINE Notify 傳送 (若依然填寫)
        if (lineNotifyToken != null && !lineNotifyToken.trim().isEmpty() && 
            lineNotifyToken.length() < 100) { // 防止使用者誤填超長 Supabase Key
            sendLineNotifyMessage(messageText);
        }
    }

    /**
     * 建立統一的高質感推播內容字串
     */
    private String buildNotificationMessage(Order order, List<OrderItem> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n🥘【小灶私廚】新預約訂單成立通知！🎉\n");
        sb.append("=========================\n");
        sb.append(" 訂單號碼：").append(order.getOrderId()).append("\n");
        sb.append(" 訂購日期：").append(order.getOrderDate()).append("\n");

        sb.append("-------------------------\n");
        sb.append(" 顧客名稱：").append(order.getCustomerName()).append("\n");
        sb.append(" 手機號碼：").append(order.getPhone()).append("\n");
        
        if (order.getInstagram() != null && !order.getInstagram().trim().isEmpty()) {
            sb.append(" Instagram：").append(order.getInstagram().trim()).append("\n");
        }
        if (order.getLineId() != null && !order.getLineId().trim().isEmpty()) {
            sb.append(" Line ID：").append(order.getLineId().trim()).append("\n");
        }
        if (order.getFacebook() != null && !order.getFacebook().trim().isEmpty()) {
            sb.append(" Facebook：").append(order.getFacebook().trim()).append("\n");
        }
        
        sb.append("=========================\n");
        sb.append(" 🍽️ 訂購明細：\n");
        
        for (OrderItem item : items) {
            String productName = "未知商品";
            String priceStr = "";
            Optional<Menu> menuOpt = menuRepository.findById(item.getProductId());
            if (menuOpt.isPresent()) {
                Menu m = menuOpt.get();
                productName = m.getName();
                priceStr = m.getPrice() != null ? m.getPrice() : "";
            }
            
            boolean isWeight = priceStr.contains("*") || priceStr.contains("重量") || "P3001".equals(item.getProductId()) || "P3002".equals(item.getProductId());
            
            sb.append("  - ").append(productName).append(" x").append(item.getQty());
            if (isWeight) {
                if (item.getProductTotalAmt() == null || item.getProductTotalAmt().compareTo(java.math.BigDecimal.ZERO) == 0) {
                    sb.append(" (製作後秤重計價)\n");
                } else {
                    String weightStr = "0";
                    if (item.getProductAmt() != null) {
                        weightStr = item.getProductAmt().stripTrailingZeros().toPlainString();
                    }
                    sb.append(" (").append(weightStr).append("g: $").append(item.getProductTotalAmt()).append(")\n");
                }
            } else {
                sb.append(" ($").append(item.getProductTotalAmt()).append(")\n");
            }
        }
        
        sb.append("=========================\n");
        sb.append(" 💰 總估計金額：$").append(order.getAmount()).append(" 元\n");
        
        if (order.getNotes() != null && !order.getNotes().trim().isEmpty()) {
            sb.append(" 📝 備註事項：").append(order.getNotes().trim()).append("\n");
        }
        sb.append("=========================");
        return sb.toString();
    }

    /**
     * 管道 A: Telegram Bot 免費推播實作
     */
    private void sendTelegramMessage(String text) {
        try {
            String url = "https://api.telegram.org/bot" + telegramBotToken.trim() + "/sendMessage";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", telegramChatId.trim());
            body.put("text", text);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                System.out.println("[Telegram 推播] 訂單通知成功送達！");
            } else {
                System.err.println("[Telegram 推播] 回應異常：" + response.getBody());
            }
        } catch (Exception e) {
            System.err.println("[Telegram 推播] 送出異常：" + e.getMessage());
        }
    }

    /**
     * 管道 B: LINE Bot (Messaging API) 推播實作
     */
    private void sendLineBotMessage(String text) {
        try {
            String url = "https://api.line.me/v2/bot/message/push";
            
            // 建立 LINE Messaging API Body
            Map<String, Object> body = new HashMap<>();
            body.put("to", lineBotAdminUserId.trim());
            
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> msgMap = new HashMap<>();
            msgMap.put("type", "text");
            msgMap.put("text", text);
            messages.add(msgMap);
            body.put("messages", messages);

            String jsonBody = objectMapper.writeValueAsString(body);
            String cleanToken = lineBotChannelToken.trim().replace(" ", "+");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cleanToken)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, java.nio.charset.StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("[LINE Bot 推播] 訂單通知已透過 Messaging API 成功推送至老闆手機！");
            } else {
                System.err.println("[LINE Bot 推播] 回應異常，狀態碼：" + response.statusCode() + "，內容：" + response.body());
            }
        } catch (Exception e) {
            System.err.println("[LINE Bot 推播] 送出異常：" + e.getMessage());
        }
    }

    /**
     * 管道 C: 舊版 LINE Notify (相容回退)
     */
    private void sendLineNotifyMessage(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("Authorization", "Bearer " + lineNotifyToken.trim());

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("message", text);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://notify-api.line.me/api/notify",
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                System.out.println("[LINE Notify] 成功推送！");
            } else {
                System.err.println("[LINE Notify] 異常：" + response.getBody());
            }
        } catch (Exception e) {
            System.err.println("[LINE Notify] 傳送異常：" + e.getMessage());
        }
    }
}
