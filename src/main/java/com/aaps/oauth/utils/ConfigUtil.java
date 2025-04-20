package com.aaps.oauth.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 配置工具類，用於從屬性文件中讀取設定
 */
public class ConfigUtil {
    private static final Logger LOGGER = Logger.getLogger(ConfigUtil.class.getName());
    private static final String CONFIG_FILE_NAME = "oauth_config.properties";
    private static Properties properties = new Properties();
    private static boolean initialized = false;

    /**
     * 初始化配置
     */
    public static void init() {
        if (initialized) {
            return;
        }

        try {
            boolean configLoaded = false;
            
            // 首先嘗試從 classpath 讀取配置文件
            try (InputStream resourceStream = ConfigUtil.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
                if (resourceStream != null) {
                    properties.load(resourceStream);
                    LOGGER.info("從 classpath 加載配置文件成功");
                    configLoaded = true;
                    initialized = true;
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "無法從 classpath 加載配置文件", e);
            }
            
            // 如果從 classpath 沒有加載成功，嘗試從文件系統讀取
            if (!configLoaded) {
                // 嘗試從當前目錄讀取
                Path configPath = Paths.get(CONFIG_FILE_NAME);
                
                if (!Files.exists(configPath)) {
                    // 如果當前目錄沒有，嘗試從用戶主目錄讀取
                    String userHome = System.getProperty("user.home");
                    configPath = Paths.get(userHome, CONFIG_FILE_NAME);
                    
                    // 如果用戶主目錄也沒有，創建一個默認的配置文件
                    if (!Files.exists(configPath)) {
                        createDefaultConfig(configPath);
                    }
                }
                
                try (InputStream input = new FileInputStream(configPath.toFile())) {
                    properties.load(input);
                    LOGGER.info("配置文件加載成功: " + configPath.toAbsolutePath());
                    initialized = true;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "無法加載配置文件", e);
        }
    }
    
    /**
     * 創建默認配置文件
     */
    private static void createDefaultConfig(Path configPath) throws IOException {
        String defaultContent = 
                "# Google OAuth 配置\n" +
                "# 請從 Google Cloud Console 獲取客戶端 ID\n" +
                "# https://console.cloud.google.com/\n" +
                "client.id=YOUR_CLIENT_ID\n";
        
        Files.write(configPath, defaultContent.getBytes());
        LOGGER.info("已創建默認配置文件: " + configPath.toAbsolutePath());
    }
    
    /**
     * 獲取配置項的值
     * @param key 配置鍵
     * @return 配置值
     */
    public static String getProperty(String key) {
        if (!initialized) {
            init();
        }
        return properties.getProperty(key);
    }
    
    /**
     * 獲取配置項的值，如果不存在則使用默認值
     * @param key 配置鍵
     * @param defaultValue 默認值
     * @return 配置值
     */
    public static String getProperty(String key, String defaultValue) {
        if (!initialized) {
            init();
        }
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * 獲取配置文件路徑
     * @return 配置文件的絕對路徑
     */
    public static String getConfigFilePath() {
        // 首先檢查當前目錄
        Path currentDirPath = Paths.get(CONFIG_FILE_NAME);
        if (Files.exists(currentDirPath)) {
            return currentDirPath.toAbsolutePath().toString();
        }
        
        // 然後檢查用戶主目錄
        String userHome = System.getProperty("user.home");
        Path userHomePath = Paths.get(userHome, CONFIG_FILE_NAME);
        return userHomePath.toAbsolutePath().toString();
    }
}
