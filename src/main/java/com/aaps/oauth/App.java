package com.aaps.oauth;

import com.aaps.oauth.ui.MainFrame;
import com.aaps.oauth.utils.I18nUtil;

import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 應用程式的主入口點
 */
public class App {
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());
    
    public static void main(String[] args) {
        // 設置 Swing 的外觀感
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, I18nUtil.getString("app.error.gui"), e);
        }
        
        // 確保從 Swing 的事件分發線程啟動應用
        SwingUtilities.invokeLater(() -> {
            try {
                new MainFrame();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, I18nUtil.getString("app.error.startup"), e);
                JOptionPane.showMessageDialog(null, 
                        I18nUtil.getString("app.error.dialog.message", e.getMessage()), 
                        I18nUtil.getString("app.error.dialog.title"), 
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
