package com.jwkl.cuisine.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter implements Filter {

    // 併發安全地儲存每個 IP 對應的限流桶
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    // 建立新桶：每個 IP 每分鐘最多允許 300 次 API 呼叫 (放寬開發限制，防止 HMR / 快速重新整理踩雷)
    private Bucket createNewBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(300, Refill.intervally(300, Duration.ofMinutes(1))))
                .build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        // 僅對後端 API 路由進行限流
        if (path.startsWith("/api/")) {
            String ip = getClientIp(httpRequest);
            Bucket bucket = cache.computeIfAbsent(ip, k -> createNewBucket());

            // 嘗試消耗 1 個權限令牌
            if (!bucket.tryConsume(1)) {
                // 超過限流限制，回傳 429 Too Many Requests，阻斷後續昂貴的 DB 與 CPU 運算
                httpResponse.setStatus(429); // 429 Too Many Requests
                httpResponse.setContentType("application/json;charset=utf-8");
                httpResponse.getWriter().write("{\"status\":\"error\",\"error\":\"【請求過於頻繁】已觸發系統限流防禦，請於一分鐘後再試！\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 獲取真實客戶端 IP (考量 CDN 或 Cloud Run 反向代理)
     */
    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
