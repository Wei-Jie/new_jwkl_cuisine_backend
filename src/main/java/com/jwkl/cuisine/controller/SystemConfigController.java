package com.jwkl.cuisine.controller;

import com.jwkl.cuisine.entity.SystemConfig;
import com.jwkl.cuisine.repository.SystemConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/system-configs")
public class SystemConfigController {

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    /**
     * 獲取所有系統配置 (前台/後台共用)
     */
    @GetMapping
    public ResponseEntity<List<SystemConfig>> getAllConfigs() {
        List<SystemConfig> configs = systemConfigRepository.findAll();
        return ResponseEntity.ok(configs);
    }

    /**
     * 依據設定鍵 configKey 獲取單一配置
     */
    @GetMapping("/{key}")
    public ResponseEntity<SystemConfig> getConfigByKey(@PathVariable String key) {
        Optional<SystemConfig> config = systemConfigRepository.findByConfigKey(key);
        return config.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 新增或更新系統配置 (後台管理)
     */
    @PostMapping
    public ResponseEntity<SystemConfig> saveOrUpdateConfig(@RequestBody SystemConfig config) {
        Optional<SystemConfig> existing = systemConfigRepository.findByConfigKey(config.getConfigKey());
        if (existing.isPresent()) {
            SystemConfig dbConfig = existing.get();
            dbConfig.setConfigValue(config.getConfigValue());
            if (config.getDescription() != null) {
                dbConfig.setDescription(config.getDescription());
            }
            SystemConfig saved = systemConfigRepository.save(dbConfig);
            return ResponseEntity.ok(saved);
        } else {
            SystemConfig saved = systemConfigRepository.save(config);
            return ResponseEntity.ok(saved);
        }
    }
}
