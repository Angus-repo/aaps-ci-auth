package com.aaps.oauth.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * 實現一個簡單的 HTTP 伺服器來處理 OAuth 回調
 */
public class CallbackServer {
    private static final Logger LOGGER = Logger.getLogger(CallbackServer.class.getName());
    
    private final int port;
    private HttpServer server;
    private final CountDownLatch authorizationLatch = new CountDownLatch(1);
    private Map<String, String> authorizationParams;
    private Consumer<String> logConsumer;
    
    /**
     * 創建一個新的回調伺服器
     * @param port 伺服器將監聽的端口
     * @param logConsumer 用於日誌輸出的消費者函數
     */
    public CallbackServer(int port, Consumer<String> logConsumer) {
        this.port = port;
        this.logConsumer = logConsumer;
    }
    
    /**
     * 啟動伺服器
     * @throws IOException 如果伺服器無法啟動
     */
    public void start() throws IOException {
        log(I18nUtil.getString("callback.server.starting", port));
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/callback", new CallbackHandler());
        server.setExecutor(null); // 使用默認執行器
        server.start();
        log(I18nUtil.getString("callback.server.started"));
    }
    
    /**
     * 停止伺服器
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            log(I18nUtil.getString("callback.server.stopped"));
        }
    }
    
    /**
     * 等待授權完成
     * @return 包含授權碼和狀態的參數映射
     * @throws InterruptedException 如果等待被中斷
     */
    public Map<String, String> waitForAuthorization() throws InterruptedException {
        authorizationLatch.await();
        return authorizationParams;
    }
    
    /**
     * 處理回調請求的處理程序
     */
    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI requestURI = exchange.getRequestURI();
            String query = requestURI.getQuery();
            
            log(I18nUtil.getString("callback.request.received"));
            
            // 解析查詢參數
            authorizationParams = parseQueryParameters(query);
            
            // 返回一個成功頁面給使用者
            String title = I18nUtil.getString("callback.success.title");
            String message = I18nUtil.getString("callback.success.message");
            String response = "<html><body style='font-family: Arial, sans-serif; text-align: center; margin-top: 50px;'>" +
                    "<h1>" + title + "</h1>" +
                    "<p>" + message + "</p>" +
                    "</body></html>";
            
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
            
            // 釋放等待的主線程
            authorizationLatch.countDown();
        }
    }
    
    /**
     * 解析查詢字符串為參數映射
     * @param query 查詢字符串
     * @return 參數映射
     */
    private Map<String, String> parseQueryParameters(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = pair.substring(0, idx);
                    String value = pair.substring(idx + 1);
                    params.put(key, value);
                }
            }
        }
        return params;
    }
    
    /**
     * 記錄消息到控制台和提供的日誌消費者
     * @param message 要記錄的消息
     */
    private void log(String message) {
        LOGGER.info(message);
        if (logConsumer != null) {
            logConsumer.accept(message);
        }
    }
}
