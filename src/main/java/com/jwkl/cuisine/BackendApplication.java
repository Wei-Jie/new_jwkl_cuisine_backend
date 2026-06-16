package com.jwkl.cuisine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BackendApplication {
    @jakarta.annotation.PostConstruct
    public void init() {
        // 設定 JVM 預設時區為台北時間 (GMT+8)
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Taipei"));
        System.out.println("[時區初始化] 已將 JVM 預設時區設定為 Asia/Taipei");
    }

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        SpringApplication.run(BackendApplication.class, args);
    }
}
