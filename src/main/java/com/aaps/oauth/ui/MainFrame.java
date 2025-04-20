package com.aaps.oauth.ui;

import com.aaps.oauth.utils.CallbackServer;
import com.aaps.oauth.utils.ConfigUtil;
import com.aaps.oauth.utils.GoogleOAuthUtil;
import com.aaps.oauth.utils.I18nUtil;
import com.google.api.client.auth.oauth2.TokenResponse;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 主應用程序視窗，提供登錄和顯示 refresh token 的界面
 */
public class MainFrame extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(MainFrame.class.getName());
    private int callbackPort = 58080;

    private JPanel mainPanel;
    private JButton loginButton;
    private JTextField tokenField;
    private JButton copyButton;
    private JTextArea logArea;
    private JScrollPane logScrollPane;

    private GoogleOAuthUtil oauthUtil;
    private CallbackServer callbackServer;

    /**
     * 創建並初始化主窗口
     */
    public MainFrame() {
        super(I18nUtil.getString("app.title"));

        try{
            callbackPort = Integer.parseInt(ConfigUtil.getProperty("callback.port", "8080"));
        } catch (NumberFormatException e) {
            log(I18nUtil.getString("error.port.invalid"));
        }

        initComponents();
        layoutComponents();
        initListeners();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(null);
        setVisible(true);

        // 檢查設定檔是否配置正確
        if (!checkAndInitConfig()) {
            return;
        }

        // 添加初始日誌信息
        log(I18nUtil.getString("app.started"));
        log(I18nUtil.getString("app.clickLogin"));
    }

    /**
     * 檢查並初始化配置
     * @return 配置是否有效
     */
    private boolean checkAndInitConfig() {
        // 初始化配置
        ConfigUtil.init();

        // 獲取 CLIENT_ID
        String clientId = ConfigUtil.getProperty("client.id", "");

        // 檢查 CLIENT_ID 是否有效
        if (clientId.isEmpty() || "YOUR_CLIENT_ID".equals(clientId)) {
            log(I18nUtil.getString("config.error.clientId"));
            log(I18nUtil.getString("config.error.edit", ConfigUtil.getConfigFilePath()));
            log(I18nUtil.getString("config.error.setClientId"));

            // 禁用登入按鈕
            loginButton.setEnabled(false);

            // 顯示錯誤對話框
            JOptionPane.showMessageDialog(this,
                    I18nUtil.getString("config.dialog.message", ConfigUtil.getConfigFilePath()),
                    I18nUtil.getString("config.dialog.title"),
                    JOptionPane.ERROR_MESSAGE);

            return false;
        }

        return true;
    }

    /**
     * 隱藏客戶端 ID 的一部分，用於日誌顯示
     */
    private String maskClientId(String clientId) {
        if (clientId.length() <= 8) {
            return "***";
        }
        return clientId.substring(0, 4) + "..." + clientId.substring(clientId.length() - 4);
    }

    /**
     * 初始化 UI 組件
     */
    private void initComponents() {
        mainPanel = new JPanel();

        // 上半部分 - 登入區域
        loginButton = new JButton(I18nUtil.getString("button.login"));
        tokenField = new JTextField(30);
        tokenField.setEditable(false);
        copyButton = new JButton(I18nUtil.getString("button.copy"));
        copyButton.setEnabled(false);

        // 下半部分 - 日誌區域
        logArea = new JTextArea(15, 60);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        // 設置自動滾動到底部
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    }

    /**
     * 設置組件佈局
     */
    private void layoutComponents() {
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 上半部分面板
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));

        // 登入按鈕面板
        JPanel loginPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        loginPanel.add(loginButton);

        // Token 顯示面板
        JPanel tokenPanel = new JPanel(new BorderLayout(5, 5));
        JLabel tokenLabel = new JLabel(I18nUtil.getString("label.refreshToken"));
        tokenPanel.add(tokenLabel, BorderLayout.WEST);
        tokenPanel.add(tokenField, BorderLayout.CENTER);
        tokenPanel.add(copyButton, BorderLayout.EAST);

        topPanel.add(loginPanel, BorderLayout.NORTH);
        topPanel.add(tokenPanel, BorderLayout.CENTER);

        // 添加上下部分到主面板
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // 日誌區域標題
        JPanel logTitlePanel = new JPanel(new BorderLayout());
        logTitlePanel.add(new JLabel(I18nUtil.getString("label.logs")), BorderLayout.WEST);

        JPanel logPanel = new JPanel(new BorderLayout(5, 5));
        logPanel.add(logTitlePanel, BorderLayout.NORTH);
        logPanel.add(logScrollPane, BorderLayout.CENTER);

        mainPanel.add(logPanel, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    /**
     * 初始化事件監聽器
     */
    private void initListeners() {
        // 登入按鈕事件
        loginButton.addActionListener(this::handleLogin);

        // 複製按鈕事件
        copyButton.addActionListener(this::handleCopy);
    }

    /**
     * 處理登入按鈕點擊事件
     */
    private void handleLogin(ActionEvent e) {
        // 禁用登入按鈕，避免重複點擊
        loginButton.setEnabled(false);

        log(I18nUtil.getString("auth.started"));

        // 在新線程中啟動認證流程，避免凍結 UI
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                try {
                    // 初始化 OAuth 工具並啟動回調伺服器
                    oauthUtil = new GoogleOAuthUtil();
                    callbackServer = new CallbackServer(callbackPort, MainFrame.this::log);
                    callbackServer.start();

                    // 打開授權 URL
                    oauthUtil.openAuthorizationUrl();
                    log(I18nUtil.getString("auth.browser.opened"));

                    // 等待回調
                    Map<String, String> params = callbackServer.waitForAuthorization();

                    String code = params.get("code");
                    String state = params.get("state");

                    if (code == null) {
                        log(I18nUtil.getString("error.no.code"));
                        return null;
                    }

                    log(I18nUtil.getString("auth.code.received"));

                    // 使用授權碼交換 token
                    TokenResponse response = oauthUtil.exchangeCodeForToken(code, state);

                    // 返回 refresh token
                    return response.getRefreshToken();

                } catch (IOException ex) {
                    log(I18nUtil.getString("error.general", ex.getMessage()));
                    LOGGER.log(Level.SEVERE, "授權過程中發生錯誤", ex);
                    return null;
                } finally {
                    // 無論成功與否，都停止回調伺服器
                    if (callbackServer != null) {
                        callbackServer.stop();
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    String refreshToken = get();
                    if (refreshToken != null) {
                        // 在 UI 上顯示 refresh token
                        tokenField.setText(refreshToken);
                        copyButton.setEnabled(true);
                        log(I18nUtil.getString("auth.token.success"));

                        // 將程式視窗移至最前景
                        setAlwaysOnTop(true);
                        toFront();
                        requestFocus();
                        setAlwaysOnTop(false);
                    } else {
                        log(I18nUtil.getString("auth.token.failure"));
                        loginButton.setEnabled(true);
                    }
                } catch (Exception ex) {
                    log(I18nUtil.getString("error.general", ex.getMessage()));
                    LOGGER.log(Level.SEVERE, "處理 Token 過程中發生錯誤", ex);
                    loginButton.setEnabled(true);
                }
            }
        }.execute();
    }

    /**
     * 處理複製按鈕點擊事件
     */
    private void handleCopy(ActionEvent e) {
        String token = tokenField.getText();
        if (token != null && !token.isEmpty()) {
            StringSelection selection = new StringSelection(token);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
            log(I18nUtil.getString("token.copied"));
        }
    }

    /**
     * 添加日誌到日誌區域
     */
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            // 確保滾動到最新的日誌
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        LOGGER.info(message);
    }
}
