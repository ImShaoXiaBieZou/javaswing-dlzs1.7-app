package me.shaoxia;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;
import java.awt.image.RGBImageFilter;
import java.util.prefs.Preferences;

/** 主页面板块 MainLauncher */
public class MainLauncher extends JFrame {
    private BlueCat blueCat;
    private CardLayout cardLayout;
    private JPanel mainContainer;
    public static final Font MAIN_FONT = new Font("微软雅黑", Font.PLAIN, 16);
    public static final Font BOLD_FONT = new Font("微软雅黑", Font.BOLD, 18);

    public MainLauncher() {
        setTitle("代理助手 1.7 ");

        // 读取本地记忆的窗口尺寸，默认 960x720
        Preferences prefs = Preferences.userNodeForPackage(MainLauncher.class);
        int winWidth = prefs.getInt("window_width", 960);
        int winHeight = prefs.getInt("window_height", 720);
        setSize(winWidth, winHeight);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        Image appIcon = null;
        try {
            java.net.URL iconUrl = getClass().getResource("/orange.png");
            if (iconUrl != null) {
                appIcon = new ImageIcon(iconUrl).getImage();
                setIconImage(appIcon);
            } else {
                System.err.println("没找到图片路径：/orange.png");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        initSystemTray(appIcon);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false);
            }
        });

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);
        add(mainContainer);
//板块
        mainContainer.add(createMainMenu(), "MENU");
        mainContainer.add(new RenamerPanel(this), "RENAMER");
        mainContainer.add(new CheckerPanel(this), "CHECKER");
        mainContainer.add(new FinderPanel(this), "FINDER");
        mainContainer.add(new ScoringPanel(this), "SCORING");
        mainContainer.add(new ReplacerPanel(this), "REPLACER");
        mainContainer.add(new ConverterPanel(this), "CONVERTER");
        mainContainer.add(new ComparisonPanel(this), "COMPARISON");
        mainContainer.add(new PyHelperPanel(this), "PY_HELPER");

        cardLayout.show(mainContainer, "MENU");

        // === 【召唤蓝猫】 ===
        blueCat = new BlueCat(this);
        setGlassPane(blueCat);
        blueCat.setCatVisible(true);

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                blueCat.updateBounds();
            }
        });
    }

    private void initSystemTray(Image iconImage) {
        if (!SystemTray.isSupported()) {
            System.err.println("当前系统不支持系统托盘功能，回退为常规关闭模式。");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            return;
        }

        if (iconImage == null) {
            iconImage = new ImageIcon(new byte[0]).getImage();
        }

        SystemTray tray = SystemTray.getSystemTray();
        PopupMenu popupMenu = new PopupMenu();

        MenuItem showItem = new MenuItem("OPEN APPLICATION");
        showItem.addActionListener(e -> {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
            toFront();
        });

        MenuItem exitItem = new MenuItem("EXIT");
        exitItem.addActionListener(e -> {
            System.exit(0);
        });

        popupMenu.add(showItem);
        popupMenu.addSeparator();
        popupMenu.add(exitItem);

        TrayIcon trayIcon = new TrayIcon(iconImage, "代理助手 1.7 - 后台运行中", popupMenu);
        trayIcon.setImageAutoSize(true);

        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    setVisible(true);
                    setExtendedState(JFrame.NORMAL);
                    toFront();
                }
            }
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("系统托盘图标添加失败！");
        }
    }

    public void switchPanel(String cardName) {
        cardLayout.show(mainContainer, cardName);

        if (blueCat != null) {
            if ("MENU".equals(cardName)) {
                blueCat.setCatVisible(true);
            } else {
                blueCat.setCatVisible(false);
            }
        }
    }

    /**
     * 纯代码绘制的抗锯齿齿轮按钮
     */
    class GearButton extends JButton {
        public GearButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setToolTipText("系统设置");
            setPreferredSize(new Dimension(45, 45));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int rOut = 11;
            int rIn = 4;
            int teeth = 8;
            int toothL = 4;
            int toothW = 6;

            if (getModel().isRollover()) {
                g2.setColor(Color.WHITE);
            } else {
                g2.setColor(new Color(200, 200, 200, 180));
            }

            Area gear = new Area(new Ellipse2D.Double(cx - rOut, cy - rOut, rOut * 2, rOut * 2));
            for (int i = 0; i < teeth; i++) {
                double angle = 2 * Math.PI * i / teeth;
                Rectangle2D.Double tooth = new Rectangle2D.Double(cx - toothW / 2.0, cy - rOut - toothL, toothW, toothL + 2);
                AffineTransform tx = AffineTransform.getRotateInstance(angle, cx, cy);
                gear.add(new Area(tx.createTransformedShape(tooth)));
            }
            gear.subtract(new Area(new Ellipse2D.Double(cx - rIn, cy - rIn, rIn * 2, rIn * 2)));

            g2.fill(gear);
            g2.dispose();
        }
    }

    private JPanel createMainMenu() {
        BackgroundPanel menuPanel = new BackgroundPanel("texture2.png");
        menuPanel.setLayout(new BorderLayout());

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        topBar.setOpaque(false);

        GearButton settingsBtn = new GearButton();
        // ✨ 这里直接调用我们分离出去的新类方法！
        settingsBtn.addActionListener(e -> SettingsDialog.showDialog(MainLauncher.this));
        topBar.add(settingsBtn);

        JPanel topArea = new JPanel(new GridBagLayout());
        topArea.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();

        JLabel title = new JLabel("代理助手 1.7");
        title.setFont(new Font("微软雅黑", Font.BOLD, 52));
        title.setForeground(Color.WHITE);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(30, 0, 60, 0);
        gbc.weighty = 0;
        topArea.add(title, gbc);

        JButton btn1 = createModuleButton("协同改名助手", "RENAMER", new Color(41, 128, 185));
        JButton btn2 = createModuleButton("数据校对助手", "CHECKER", new Color(39, 174, 96));
        JButton btn3 = createModuleButton("文件检索助手", "FINDER", new Color(142, 68, 173));
        JButton btn4 = createModuleButton("评分生成助手", "SCORING", new Color(211, 84, 0));
        JButton btn5 = createModuleButton("文档替换助手", "REPLACER", new Color(255, 215, 0));
        JButton btn6 = createModuleButton("一键转换助手", "CONVERTER", new Color(192, 57, 43));
        JButton btn7 = createModuleButton("公告比对助手", "COMPARISON", new Color(44, 62, 80));
        JButton btn8 = createModuleButton("PY脚本助手", "PY_HELPER", new Color(155, 89, 182));
        JButton btn9 = createModuleButton("待开发...", "DEV", new Color(127, 140, 141));

        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.insets = new Insets(10, 10, 10, 10);

        gbc.gridy = 1;
        gbc.gridx = 0; topArea.add(btn1, gbc);
        gbc.gridx = 1; topArea.add(btn2, gbc);
        gbc.gridx = 2; topArea.add(btn3, gbc);

        gbc.gridy = 2;
        gbc.gridx = 0; topArea.add(btn4, gbc);
        gbc.gridx = 1; topArea.add(btn5, gbc);
        gbc.gridx = 2; topArea.add(btn6, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0; topArea.add(btn7, gbc);
        gbc.gridx = 1; topArea.add(btn8, gbc);
        gbc.gridx = 2; topArea.add(btn9, gbc);

        gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        topArea.add(Box.createVerticalGlue(), gbc);

        DinoGamePanel gamePanel = new DinoGamePanel();
        gamePanel.setVisible(false);
        gamePanel.setPreferredSize(new Dimension(800, 260));

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE && !gamePanel.isVisible()) {
                    gamePanel.setVisible(true);
                    menuPanel.revalidate();
                    gamePanel.requestFocusInWindow();
                    return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && gamePanel.isVisible()) {
                    gamePanel.setVisible(false);
                    menuPanel.revalidate();
                    return true;
                }
            }
            return false;
        });

        menuPanel.add(topBar, BorderLayout.NORTH);
        menuPanel.add(topArea, BorderLayout.CENTER);
        menuPanel.add(gamePanel, BorderLayout.SOUTH);
        return menuPanel;
    }

    private JButton createModuleButton(String text, String cardName, Color themeColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean isHover = getModel().isRollover();
                int alpha = isHover ? 200 : 150;
                g2.setColor(new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(), alpha));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                g2.setStroke(new BasicStroke(1.8f));
                g2.setColor(new Color(255, 255, 255, isHover ? 120 : 70));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 25, 25);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(new Color(0, 0, 0, 60));
                g2.drawString(getText(), x + 1, y + 1);
                g2.setColor(Color.WHITE);
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(270, 110));
        btn.setFont(new Font("微软雅黑", Font.BOLD, 20));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { btn.setFont(new Font("微软雅黑", Font.BOLD, 22)); }
            @Override
            public void mouseExited(MouseEvent e) { btn.setFont(new Font("微软雅黑", Font.BOLD, 20)); }
            @Override
            public void mousePressed(MouseEvent e) {
                switchPanel(cardName);
            }
        });
        return btn;
    }

    public void showMenu() {
        switchPanel("MENU");
    }

    public static void main(String[] args) {
        // 极致精简后的透明闪图窗口逻辑
        JWindow splashScreen = new JWindow();
        splashScreen.setBackground(new Color(0, 0, 0, 0));

        java.net.URL gifUrl = MainLauncher.class.getClassLoader().getResource("loading.gif");

        if (gifUrl != null) {
            Image image = Toolkit.getDefaultToolkit().createImage(gifUrl);
            ImageFilter filter = new RGBImageFilter() {
                public final int filterRGB(int x, int y, int rgb) {
                    if ((rgb | 0xFF000000) == 0xFFFFFFFF) {
                        return 0x00FFFFFF & rgb;
                    }
                    return rgb;
                }
            };
            Image transparentGif = Toolkit.getDefaultToolkit().createImage(new FilteredImageSource(image.getSource(), filter));
            JLabel gifLabel = new JLabel(new ImageIcon(transparentGif));
            splashScreen.getContentPane().add(gifLabel);
        }

        splashScreen.pack();
        splashScreen.setLocationRelativeTo(null);
        splashScreen.setVisible(true);

        // 启动主程序线程
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (Exception e) {}

            SwingUtilities.invokeLater(() -> {
                FlatDarkLaf.setup();
                UIManager.put("defaultFont", new Font("微软雅黑", Font.PLAIN, 14));

                MainLauncher launcher = new MainLauncher();
                splashScreen.dispose(); // 关闭闪图
                launcher.setVisible(true); // 显示主界面
            });
        }).start();
    }
}