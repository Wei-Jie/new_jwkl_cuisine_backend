package com.jwkl.cuisine.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class ApiKeyFilter implements Filter {

    @Value("${security.api-key:JWKL_CUISINE_DEFAULT_SECRET_API_KEY}")
    private String apiKey;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 💡 關鍵修復：如果是 OPTIONS 預檢請求，直接放行，讓 Spring MVC CORS 配置處理，防堵 401 被攔截
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // 判斷是否為前台公開的 API 白名單路由與對應 Method
        boolean isPublicRoute = false;
        if ("GET".equalsIgnoreCase(method)) {
            if (path.equals("/api/v1/faqs") || 
                path.equals("/api/v1/menus") || 
                path.equals("/api/v1/system-configs") || 
                path.equals("/api/v1/orders/track") ||
                path.startsWith("/api/v1/orders/receipt/") ||
                path.equals("/api/v1/posts") ||
                path.startsWith("/api/v1/posts/") ||
                path.startsWith("/api/v1/comments/post/")) {
                isPublicRoute = true;
            }
        } else if ("POST".equalsIgnoreCase(method)) {
            if (path.equals("/api/v1/orders") ||
                path.startsWith("/api/v1/comments/post/") ||
                (path.startsWith("/api/v1/posts/") && path.endsWith("/view"))) {
                isPublicRoute = true;
            }
        }

        // 僅校驗 /api/ 開頭的後端 API 路由，排除靜態網頁、健康檢查或前台公開免 Key 路由
        if (path.startsWith("/api/") && !isPublicRoute) {
            String clientKey = httpRequest.getHeader("X-API-KEY");

            if (clientKey == null || !clientKey.equals(apiKey)) {
                // 金鑰不吻合，立即在毫秒級速度下拋出 401 Unauthorized，拒絕存取資料庫與 Cloud Run
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json;charset=utf-8");
                httpResponse.getWriter().write("{\"status\":\"error\",\"error\":\"【拒絕存取】API-KEY 校驗失敗，未授權的操作！\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
