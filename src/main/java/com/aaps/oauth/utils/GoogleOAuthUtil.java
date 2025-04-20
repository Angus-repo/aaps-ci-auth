package com.aaps.oauth.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * 提供 Google OAuth 驗證功能的工具類
 */
public class GoogleOAuthUtil {
    private static final Logger LOGGER = Logger.getLogger(GoogleOAuthUtil.class.getName());
    
    // OAuth 相關常數
    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/auth";
    private static final String TOKEN_SERVER_URL = "https://oauth2.googleapis.com/token";
    private static final String CALLBACK_URI = "http://localhost:" + ConfigUtil.getProperty("callback.port", "") + "/callback";
    
    // 從設定檔讀取 CLIENT_ID
    private static final String CLIENT_ID = ConfigUtil.getProperty("client.id", "");
    
    // 默認域，用於數據存儲
    private static final String DEFAULT_SCOPE = "https://www.googleapis.com/auth/drive.file";
    
    private String codeVerifier;
    private String codeChallenge;
    private String state;
    
    /**
     * 初始化 OAuth 流程
     */
    public GoogleOAuthUtil() {
        // 產生 PKCE 所需的 code_verifier 和 code_challenge
        generatePkceParameters();
        // 產生 state 參數，用於防止 CSRF 攻擊
        state = generateRandomString();
    }
    
    /**
     * 生成 PKCE 所需的參數
     */
    private void generatePkceParameters() {
        // 生成一個隨機的 code_verifier (43-128 字符長度)
        codeVerifier = generateRandomString();
        
        // 根據 RFC7636，計算 code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))
        try {
            byte[] bytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
            java.security.MessageDigest messageDigest = java.security.MessageDigest.getInstance("SHA-256");
            messageDigest.update(bytes, 0, bytes.length);
            byte[] digest = messageDigest.digest();
            codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating PKCE parameters", e);
        }
    }
    
    /**
     * 生成一個用於 code_verifier 或 state 的隨機字串
     */
    private String generateRandomString() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[32]; // 32 bytes = 256 bits
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
    
    /**
     * 生成 OAuth 授權 URL
     * @return 用戶需要訪問的授權 URL
     */
    public String getAuthorizationUrl() {
        String url = AUTH_ENDPOINT +
                "?client_id=" + CLIENT_ID +
                "&redirect_uri=" + URLEncoder.encode(CALLBACK_URI, StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(DEFAULT_SCOPE, StandardCharsets.UTF_8) +
                "&code_challenge=" + URLEncoder.encode(codeChallenge, StandardCharsets.UTF_8) +
                "&code_challenge_method=S256" +
                "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8) +
                "&access_type=offline" +
                "&prompt=consent"; // 強制請求 refresh token
        
        LOGGER.info("Auth URL: " + url);
        return url;
    }
    
    /**
     * 根據授權碼交換獲取 token
     * @param authCode 從回調 URL 獲取的授權碼
     * @param receivedState 從回調 URL 獲取的 state 參數
     * @return token 響應，包含 access_token 和 refresh_token
     * @throws IOException 如果請求失敗
     * @throws IllegalStateException 如果 state 不匹配
     */
    public TokenResponse exchangeCodeForToken(String authCode, String receivedState) throws IOException, IllegalStateException {
        // 驗證 state 參數，防止 CSRF 攻擊
        if (!state.equals(receivedState)) {
            throw new IllegalStateException("Invalid state parameter. Possible CSRF attack.");
        }
        
        LOGGER.info("正在交換 token，使用授權碼: " + authCode.substring(0, Math.min(5, authCode.length())) + "...");
        LOGGER.info("使用 code_verifier: " + codeVerifier.substring(0, Math.min(5, codeVerifier.length())) + "...");
        
        // 使用 Apache HttpClient 替代 Google HTTP Client
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(TOKEN_SERVER_URL);
            
            // 構建請求參數
            URIBuilder builder = new URIBuilder(new URI(TOKEN_SERVER_URL));
            
            // 添加請求參數
            String postBody = 
                "client_id=" + URLEncoder.encode(CLIENT_ID, StandardCharsets.UTF_8) +
                "&grant_type=authorization_code" +
                "&code=" + URLEncoder.encode(authCode, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(CALLBACK_URI, StandardCharsets.UTF_8) +
                "&code_verifier=" + URLEncoder.encode(codeVerifier, StandardCharsets.UTF_8);
            
            // 設置請求內容
            StringEntity entity = new StringEntity(postBody);
            httpPost.setEntity(entity);
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");
            
            LOGGER.info("發送 token 交換請求...");

            // 執行請求
            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                // 解析響應
                String responseBody = EntityUtils.toString(response.getEntity());
                
                // 使用 Gson 解析 JSON
                Gson gson = new Gson();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                
                // 手動構建 TokenResponse
                TokenResponse tokenResponse = new TokenResponse();
                if (jsonResponse.has("access_token")) {
                    tokenResponse.setAccessToken(jsonResponse.get("access_token").getAsString());
                }
                if (jsonResponse.has("refresh_token")) {
                    tokenResponse.setRefreshToken(jsonResponse.get("refresh_token").getAsString());
                }
                if (jsonResponse.has("expires_in")) {
                    tokenResponse.setExpiresInSeconds(jsonResponse.get("expires_in").getAsLong());
                }
                if (jsonResponse.has("token_type")) {
                    tokenResponse.setTokenType(jsonResponse.get("token_type").getAsString());
                }
                
                LOGGER.info("Token 交換成功");
                return tokenResponse;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Token exchange failed", e);
            throw new IOException("Failed to exchange code for token", e);
        }
    }
    
    /**
     * 打開授權 URL (在瀏覽器中)
     */
    public void openAuthorizationUrl() {
        String url = getAuthorizationUrl();
        try {
            // 嘗試打開默認瀏覽器
            java.awt.Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not open browser. Please manually visit: " + url, e);
        }
    }
    
    /**
     * 獲取當前的 state 參數
     * @return state 參數
     */
    public String getState() {
        return state;
    }
}
