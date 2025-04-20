package com.aaps.oauth.utils;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * 國際化工具類，用於加載和使用多語言資源
 */
public class I18nUtil {
    private static final Logger LOGGER = Logger.getLogger(I18nUtil.class.getName());
    private static final String BUNDLE_NAME = "messages";
    private static ResourceBundle resourceBundle;
    private static Locale currentLocale = Locale.getDefault();

    static {
        // 初始化資源包
        init();
    }

    /**
     * 初始化資源包
     */
    public static void init() {
        try {
            // 嘗試載入對應當前系統語言的資源
            resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME, currentLocale);
            LOGGER.info("Loaded resource bundle for locale: " + currentLocale);
        } catch (Exception e) {
            // 如果找不到對應的資源，則使用默認資源
            resourceBundle = ResourceBundle.getBundle(BUNDLE_NAME);
            LOGGER.warning("Failed to load locale-specific bundle, using default: " + e.getMessage());
        }
    }

    /**
     * 設置使用的語言區域
     * @param locale 要使用的語言區域
     */
    public static void setLocale(Locale locale) {
        currentLocale = locale;
        init();
    }

    /**
     * 根據鍵獲取對應的文字資源
     * @param key 資源鍵
     * @return 對應的文字，如果找不到則返回鍵名
     */
    public static String getString(String key) {
        try {
            return resourceBundle.getString(key);
        } catch (Exception e) {
            LOGGER.warning("Missing resource key: " + key);
            return key; // 返回鍵名作為備用
        }
    }

    /**
     * 根據鍵獲取帶參數的文字資源
     * @param key 資源鍵
     * @param args 參數值
     * @return 格式化後的文字
     */
    public static String getString(String key, Object... args) {
        try {
            String pattern = resourceBundle.getString(key);
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            LOGGER.warning("Error formatting resource key: " + key);
            return key; // 返回鍵名作為備用
        }
    }
    
    /**
     * 獲取當前使用的語言區域
     * @return 當前語言區域
     */
    public static Locale getCurrentLocale() {
        return currentLocale;
    }
}
