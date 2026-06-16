package com.jwkl.cuisine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwkl.cuisine.entity.SystemConfig;
import com.jwkl.cuisine.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.Security;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import java.util.*;

@Service
public class WebPushService {

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static final String PUBLIC_KEY_KEY = "WEB_PUSH_PUBLIC_KEY";
    private static final String PRIVATE_KEY_KEY = "WEB_PUSH_PRIVATE_KEY";
    private static final String SUBSCRIPTION_KEY = "ADMIN_WEB_PUSH_SUBSCRIPTION";

    /**
     * 前端與儲存安全專用之 Subscription DTO
     */
    @Data
    public static class WebPushSubscription {
        private String endpoint;
        private Keys keys;

        @Data
        public static class Keys {
            private String p256dh;
            private String auth;
        }

        public Subscription toSubscription() {
            Subscription sub = new Subscription();
            sub.endpoint = this.endpoint;
            sub.keys = new Subscription.Keys(this.keys.p256dh, this.keys.auth);
            return sub;
        }

        public static WebPushSubscription fromSubscription(Subscription sub) {
            WebPushSubscription dto = new WebPushSubscription();
            dto.setEndpoint(sub.endpoint);
            Keys k = new Keys();
            k.setP256dh(sub.keys.p256dh);
            k.setAuth(sub.keys.auth);
            dto.setKeys(k);
            return dto;
        }
    }

    @PostConstruct
    public void initKeys() {
        try {
            Optional<SystemConfig> pubOpt = systemConfigRepository.findByConfigKey(PUBLIC_KEY_KEY);
            Optional<SystemConfig> privOpt = systemConfigRepository.findByConfigKey(PRIVATE_KEY_KEY);

            if (pubOpt.isEmpty() || privOpt.isEmpty()) {
                // 自動生成金鑰對 (使用 BouncyCastle 橢圓曲線生成)
                java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("EC", "BC");
                java.security.spec.ECGenParameterSpec gps = new java.security.spec.ECGenParameterSpec("secp256r1");
                kpg.initialize(gps);
                KeyPair keyPair = kpg.generateKeyPair();

                ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
                ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivate();

                byte[] publicKeyBytes = publicKey.getQ().getEncoded(false);
                byte[] privateKeyBytes = org.bouncycastle.util.BigIntegers.asUnsignedByteArray(32, privateKey.getD());

                String pubBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKeyBytes);
                String privBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(privateKeyBytes);

                saveConfig(PUBLIC_KEY_KEY, pubBase64, "Web Push VAPID Public Key (Auto Generated)");
                saveConfig(PRIVATE_KEY_KEY, privBase64, "Web Push VAPID Private Key (Auto Generated)");
                System.out.println("[Web Push] 已自動生成並儲存 VAPID 公私鑰對");
            }
        } catch (Exception e) {
            System.err.println("[Web Push] 初始化金鑰對失敗: " + e.getMessage());
        }
    }

    private void saveConfig(String key, String value, String description) {
        SystemConfig config = systemConfigRepository.findByConfigKey(key).orElse(new SystemConfig());
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);
        systemConfigRepository.save(config);
    }

    public String getPublicKey() {
        return systemConfigRepository.findByConfigKey(PUBLIC_KEY_KEY)
                .map(SystemConfig::getConfigValue)
                .orElse("");
    }

    private String getPrivateKey() {
        return systemConfigRepository.findByConfigKey(PRIVATE_KEY_KEY)
                .map(SystemConfig::getConfigValue)
                .orElse("");
    }

    /**
     * 儲存或更新訂閱資訊 (支援多個瀏覽器同時儲存於一個 JSON 陣列中)
     */
    public synchronized void subscribe(WebPushSubscription subscription) {
        try {
            List<WebPushSubscription> subscriptions = getSubscriptions();
            // 檢查是否已存在相同的 endpoint
            boolean exists = subscriptions.stream()
                    .anyMatch(s -> s.getEndpoint().equals(subscription.getEndpoint()));

            if (!exists) {
                subscriptions.add(subscription);
                String json = objectMapper.writeValueAsString(subscriptions);
                saveConfig(SUBSCRIPTION_KEY, json, "Admin Web Push Subscriptions JSON Array");
                System.out.println("[Web Push] 新增訂閱成功！目前活躍訂閱數: " + subscriptions.size());
            }
        } catch (Exception e) {
            System.err.println("[Web Push] 儲存訂閱失敗: " + e.getMessage());
        }
    }

    /**
     * 獲取所有訂閱
     */
    public List<WebPushSubscription> getSubscriptions() {
        try {
            Optional<SystemConfig> configOpt = systemConfigRepository.findByConfigKey(SUBSCRIPTION_KEY);
            if (configOpt.isPresent() && !configOpt.get().getConfigValue().trim().isEmpty()) {
                String val = configOpt.get().getConfigValue();
                if (val.trim().startsWith("[")) {
                    return objectMapper.readValue(val, new TypeReference<List<WebPushSubscription>>() {});
                } else {
                    // 相容舊版單一物件格式
                    WebPushSubscription sub = objectMapper.readValue(val, WebPushSubscription.class);
                    List<WebPushSubscription> list = new ArrayList<>();
                    list.add(sub);
                    return list;
                }
            }
        } catch (Exception e) {
            System.err.println("[Web Push] 讀取訂閱列表失敗，可能格式損壞，將重置: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * 發送 Web Push 推送給所有訂閱者
     */
    public void sendPushNotification(String title, String message, String clickAction) {
        new Thread(() -> {
            try {
                String pubKeyStr = getPublicKey();
                String privKeyStr = getPrivateKey();

                if (pubKeyStr.isEmpty() || privKeyStr.isEmpty()) {
                    System.err.println("[Web Push] VAPID 金鑰未就緒，無法發送通知");
                    return;
                }

                // 直接傳送 Base64 URL Safe 字串金鑰給 PushService
                PushService pushService = new PushService(pubKeyStr, privKeyStr, "mailto:admin@example.com");

                List<WebPushSubscription> subscriptions = getSubscriptions();
                if (subscriptions.isEmpty()) {
                    System.out.println("[Web Push] 無任何活躍訂閱，跳過推送");
                    return;
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("title", title);
                payload.put("body", message);
                if (clickAction != null) {
                    payload.put("click_action", clickAction);
                }
                String payloadJson = objectMapper.writeValueAsString(payload);

                List<WebPushSubscription> failedSubscriptions = new ArrayList<>();

                for (WebPushSubscription subDto : subscriptions) {
                    try {
                        Subscription sub = subDto.toSubscription();
                        Notification notification = new Notification(sub, payloadJson);
                        var response = pushService.send(notification);
                        int status = response.getStatusLine().getStatusCode();
                        if (status == 201) {
                            System.out.println("[Web Push] 推送成功送達: " + sub.endpoint);
                        } else if (status == 410 || status == 404) {
                            System.out.println("[Web Push] 訂閱已失效，將自動清除: " + sub.endpoint);
                            failedSubscriptions.add(subDto);
                        } else {
                            System.err.println("[Web Push] 推送回應異常，狀態碼: " + status);
                        }
                    } catch (Exception e) {
                        System.err.println("[Web Push] 單筆推送發送失敗: " + e.getMessage());
                        if (e.getMessage() != null && (e.getMessage().contains("410") || e.getMessage().contains("404"))) {
                            failedSubscriptions.add(subDto);
                        }
                    }
                }

                // 清理失效的訂閱
                if (!failedSubscriptions.isEmpty()) {
                    subscriptions.removeAll(failedSubscriptions);
                    String json = objectMapper.writeValueAsString(subscriptions);
                    saveConfig(SUBSCRIPTION_KEY, json, "Admin Web Push Subscriptions JSON Array");
                }

            } catch (Exception e) {
                System.err.println("[Web Push] 批次發送推送失敗: " + e.getMessage());
            }
        }).start();
    }
}
