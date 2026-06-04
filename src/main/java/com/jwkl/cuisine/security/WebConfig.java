package com.jwkl.cuisine.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private ApiKeyFilter apiKeyFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * 註冊限流 Filter (最外層防線，第一時間封鎖惡意刷頻)
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> registerRateLimitFilter() {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(rateLimitFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1); // 最優先執行
        return registration;
    }

    /**
     * 註冊 API-KEY 安全過濾 Filter (第二道防線，驗證前後端安全令牌)
     */
    @Bean
    public FilterRegistrationBean<ApiKeyFilter> registerApiKeyFilter() {
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(apiKeyFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2); // 限流通過後才進行金鑰驗證
        return registration;
    }

    /**
     * 全域 CORS 跨來源共用配置
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-API-KEY", "Authorization")
                .allowCredentials(false) // 設為 false 時 allowedOrigins 可為 *
                .maxAge(3600); // 預檢請求暫存 1 小時
    }
}
