package com.jwkl.cuisine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class ImageStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 從完整的 Supabase 公開 URL 中，刪除 Supabase Storage 中的實體檔案
     * @param imageUrl 完整的公開 URL (例如 https://.../storage/v1/object/public/cuisine-assets/file_123.jpg)
     */
    public void deleteImageByUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }

        // 檢查是否屬於該 Supabase 的圖片連結
        String targetPart = "/storage/v1/object/public/cuisine-assets/";
        if (!imageUrl.contains(targetPart)) {
            log.info("圖片 URL 不包含 Supabase 儲存庫路徑，跳過刪除: {}", imageUrl);
            return;
        }

        try {
            // 解析出唯一的檔案名稱
            int index = imageUrl.indexOf(targetPart);
            String uniqueFileName = imageUrl.substring(index + targetPart.length());
            
            // 去除可能的時間戳記 query 參數（例如 ?t=1717800000）
            if (uniqueFileName.contains("?")) {
                uniqueFileName = uniqueFileName.substring(0, uniqueFileName.indexOf('?'));
            }

            log.info("開始從 Supabase Storage 刪除檔案: {}", uniqueFileName);

            String deleteUrl = supabaseUrl + "/storage/v1/object/cuisine-assets/" + uniqueFileName;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(deleteUrl))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 204) {
                log.info("成功從 Supabase Storage 刪除檔案: {}", uniqueFileName);
            } else {
                log.warn("刪除 Supabase 檔案失敗: HTTP {}, Response: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("從 Supabase Storage 刪除檔案時發生異常: ", e);
        }
    }
}
