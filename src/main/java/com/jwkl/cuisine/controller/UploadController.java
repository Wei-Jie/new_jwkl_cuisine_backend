package com.jwkl.cuisine.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 處理實體圖片上傳 (由 ApiKeyFilter 強制保護，防止未授權操作)
     */
    @PostMapping
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"status\":\"error\",\"message\":\"上傳的檔案不可為空！\"}");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                originalFilename = "upload_image.jpg";
            }

            // 1. 檔名加上時間戳記防重名覆蓋 (例如 P1001.jpg -> P1001_1717800000.jpg)
            String name = originalFilename;
            String ext = "";
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex > 0) {
                name = originalFilename.substring(0, dotIndex);
                ext = originalFilename.substring(dotIndex);
            }
            long timestamp = Instant.now().getEpochSecond();
            
            // 2. 清理檔名中可能的不安全字元，僅保留英數字與底線 (Supabase Storage 限制檔案 Key 僅能使用 ASCII 字元)
            String cleanedName = name.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_");
            String uniqueFileName = cleanedName + "_" + timestamp + ext;

            log.info("開始上傳檔案到 Supabase Storage: {}, 大小: {} bytes", uniqueFileName, file.getSize());

            // 2. 使用 Java 11 內建 HttpClient 發送二進位內容至 Supabase Storage REST API
            String uploadUrl = supabaseUrl + "/storage/v1/object/cuisine-assets/" + uniqueFileName;
            String mimeType = file.getContentType();
            if (mimeType == null) {
                mimeType = "image/jpeg";
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("apikey", supabaseKey)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("Content-Type", mimeType)
                    .header("x-upsert", "true") // 支援覆蓋以策安全
                    .POST(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // 3. 上傳成功，拼裝公開存取的 CDN URL
                String publicUrl = supabaseUrl + "/storage/v1/object/public/cuisine-assets/" + uniqueFileName;
                log.info("檔案上傳 Supabase Storage 成功！URL: {}", publicUrl);

                Map<String, Object> result = new HashMap<>();
                result.put("status", "success");
                result.put("url", publicUrl);
                return ResponseEntity.ok(result);
            } else {
                log.error("Supabase Storage 伺服器回傳錯誤: Status {}, Body: {}", response.statusCode(), response.body());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"status\":\"error\",\"message\":\"上傳至 Supabase 儲存空間時出錯！\"}");
            }

        } catch (Exception e) {
            log.error("上傳圖片過程發生系統異常: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"status\":\"error\",\"message\":\"伺服器上傳處理失敗，請稍後再試！\"}");
        }
    }
}
