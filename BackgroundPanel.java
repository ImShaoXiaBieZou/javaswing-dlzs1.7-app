package me.shaoxia;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * 背景面板组件
 */

public class BackgroundPanel extends JPanel {
    private Image img;

    public BackgroundPanel(String path) {
        setOpaque(false);
        // 使用 getResource 查找类路径下的资源
        java.net.URL imgUrl = getClass().getClassLoader().getResource(path);

        if (imgUrl != null) {
            img = new ImageIcon(imgUrl).getImage();
        } else {
            System.err.println("找不到图片资源: " + path);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (img != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int panelW = getWidth(), panelH = getHeight(), imgW = img.getWidth(this), imgH = img.getHeight(this);
            if (imgW > 0 && imgH > 0) {
                double scale = Math.max((double) panelW / imgW, (double) panelH / imgH);
                int drawW = (int) (imgW * scale), drawH = (int) (imgH * scale);
                g2.drawImage(img, (panelW - drawW) / 2, (panelH - drawH) / 2, drawW, drawH, this);
            }
            g2.dispose();
        } else {
            g.setColor(new Color(35, 35, 35));
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        super.paintComponent(g);
    }
}