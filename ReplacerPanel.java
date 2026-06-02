package me.shaoxia;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
// --- 新增 PPTX 相关引用 ---
import org.apache.poi.xslf.usermodel.*;

import javax.swing.*;
import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**文档替换助手 ReplacerPanel */

public class ReplacerPanel extends BackgroundPanel {
    private JTextField[] inputs = new JTextField[6];
    private JTextField customKeyInput;
    private final String[] placeholders = {
            "XXX项目", "NARI-XXXXXX", "2026年XX月XX日", "XXXXXXXXXXX", "****@qq.com"
    };
    private JTextArea logArea;

    public ReplacerPanel(MainLauncher parent) {
        super("texture2.png");

        // 【新增功能：提升提示框响应速度】
        // 鼠标移入只需停靠 200 毫秒立刻闪现显示，且最长维持 15 秒，保证少侠能清晰看全内容
        ToolTipManager.sharedInstance().setInitialDelay(200);
        ToolTipManager.sharedInstance().setDismissDelay(15000);

        setLayout(new BorderLayout());

        // 顶部导航
        JButton backBtn = new JButton(" << 返回主菜单 ");
        backBtn.addActionListener(e -> parent.showMenu());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        top.add(backBtn);
        add(top, BorderLayout.NORTH);

        // 主配置区
        JPanel p = new JPanel(new GridLayout(7, 1, 5, 5));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(10, 50, 10, 50));

        for (int i = 0; i < 5; i++) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            JLabel label = new JLabel((i + 1) + ". 修改 [" + placeholders[i] + "] 为: ");
            label.setPreferredSize(new Dimension(220, 30));
            label.setForeground(Color.WHITE);
            row.add(label, BorderLayout.WEST);
            inputs[i] = createCustomTextField();
            row.add(inputs[i], BorderLayout.CENTER);
            p.add(row);
        }

        JPanel row6 = new JPanel(new BorderLayout(10, 0));
        row6.setOpaque(false);
        JPanel leftPanel = new JPanel(new BorderLayout(5, 0));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(220, 30));
        JLabel label6 = new JLabel("6. 修改 ");
        label6.setForeground(Color.WHITE);
        leftPanel.add(label6, BorderLayout.WEST);
        customKeyInput = createCustomTextField();
        customKeyInput.setToolTipText("输入需要被替换的自定义内容");
        leftPanel.add(customKeyInput, BorderLayout.CENTER);
        JLabel label6End = new JLabel(" 为: ");
        label6End.setForeground(Color.WHITE);
        leftPanel.add(label6End, BorderLayout.EAST);
        row6.add(leftPanel, BorderLayout.WEST);
        inputs[5] = createCustomTextField();
        row6.add(inputs[5], BorderLayout.CENTER);
        p.add(row6);

        logArea = new JTextArea(
                "  _________________________  \n" +
                        " | [  ]                            [X] | \n" +
                        " |-------------------------| \n" +
                        " | >_ BUT SOME OF US  | \n" +
                        " | >_ ARE CODERS.         | \n" +
                        " |_________________________| \n" +
                        "                             \n");
        logArea.setEditable(false);
        logArea.setBackground(new Color(0, 0, 0, 60));
        logArea.setForeground(Color.WHITE);
        logArea.setFont(new Font("微软雅黑", Font.BOLD, 12));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        new DropTarget(logArea, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    Map<String, String> map = new HashMap<>();
                    for (int i = 0; i < 5; i++) {
                        String v = inputs[i].getText().trim();
                        if (!v.isEmpty()) map.put(placeholders[i], v);
                    }
                    String customKey = customKeyInput.getText().trim();
                    String customVal = inputs[5].getText().trim();
                    if (!customKey.isEmpty() && !customVal.isEmpty()) {
                        map.put(customKey, customVal);
                    }
                    if (map.isEmpty()) {
                        log("请至少输入一个替换内容！");
                        return;
                    }
                    new Thread(() -> {
                        for (File f : files) recursiveScan(f, map);
                        log(" 任务已完成！");
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "处理完成！"));
                    }).start();
                } catch (Exception e) {
                    log("异常: " + e.getMessage());
                }
            }
        });

        JPanel centerContainer = new JPanel(new BorderLayout(10, 10));
        centerContainer.setOpaque(false);
        centerContainer.add(p, BorderLayout.NORTH);
        centerContainer.add(scroll, BorderLayout.CENTER);
        add(centerContainer, BorderLayout.CENTER);
    }

    private JTextField createCustomTextField() {
        JTextField textField = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.dispose();
                super.paintComponent(g);
            }
            @Override
            protected void paintBorder(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 1));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                g2.dispose();
            }

            // 【核心新增方法】：在这里重写默认的 ToolTip 获取逻辑，实现完美动态内容展示
            @Override
            public String getToolTipText() {
                String text = getText().trim();
                if (!text.isEmpty()) {
                    // 采用 HTML 格式进行优雅美化，支持长文本智能排版
                    return "<html><body style='font-family:微软雅黑; font-size:13px; padding:2px; color:#333333;'>"
                            + "<b>当前输入内容：</b><br>" + text + "</body></html>";
                }
                return super.getToolTipText(); // 如果框内是空的，则展示原本默认设置的提示（例如自定义规则处的原版默认提示词）
            }
        };
        textField.setOpaque(false);
        textField.setForeground(Color.WHITE);
        textField.setCaretColor(Color.WHITE);
        textField.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        textField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        return textField;
    }

    private void recursiveScan(File file, Map<String, String> map) {
        if (file.isDirectory()) {
            File[] list = file.listFiles();
            if (list != null) for (File c : list) recursiveScan(c, map);
        } else {
            processFile(file, map);
        }
    }

    private void processFile(File src, Map<String, String> map) {
        String name = src.getName().toLowerCase();
        File temp = new File(src.getAbsolutePath() + ".tmp");
        boolean success = false;

        try (InputStream is = FileMagic.prepareToCheckMagic(new FileInputStream(src))) {
            FileMagic fm = FileMagic.valueOf(is);

            // 1. 处理 .docx
            if (fm == FileMagic.OOXML && name.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(is)) {
                    for (XWPFParagraph p : doc.getParagraphs()) replaceInDocx(p, map);
                    for (XWPFTable t : doc.getTables()) {
                        for (XWPFTableRow r : t.getRows()) {
                            for (XWPFTableCell c : r.getTableCells()) {
                                for (XWPFParagraph p : c.getParagraphs()) replaceInDocx(p, map);
                            }
                        }
                    }
                    try (FileOutputStream fos = new FileOutputStream(temp)) { doc.write(fos); }
                    success = true;
                }
            }
            // 2. 处理 .pptx (新增支持)
            else if (fm == FileMagic.OOXML && name.endsWith(".pptx")) {
                try (XMLSlideShow ppt = new XMLSlideShow(is)) {
                    for (XSLFSlide slide : ppt.getSlides()) {
                        for (XSLFShape shape : slide.getShapes()) {
                            // 处理普通文本框/形状
                            if (shape instanceof XSLFTextShape) {
                                replaceInPptx((XSLFTextShape) shape, map);
                            }
                            // 处理表格
                            else if (shape instanceof XSLFTable) {
                                XSLFTable table = (XSLFTable) shape;
                                for (XSLFTableRow row : table.getRows()) {
                                    for (XSLFTableCell cell : row.getCells()) {
                                        replaceInPptx(cell, map);
                                    }
                                }
                            }
                        }
                    }
                    try (FileOutputStream fos = new FileOutputStream(temp)) { ppt.write(fos); }
                    success = true;
                }
            }
            // 3. 处理 .doc
            else if (fm == FileMagic.OLE2 && name.endsWith(".doc")) {
                try (HWPFDocument doc = new HWPFDocument(is)) {
                    org.apache.poi.hwpf.usermodel.Range r = doc.getRange();
                    for (Map.Entry<String, String> e : map.entrySet()) {
                        r.replaceText(e.getKey(), e.getValue());
                    }
                    try (FileOutputStream fos = new FileOutputStream(temp)) { doc.write(fos); }
                    success = true;
                }
            }
            // 4. 处理 Excel
            else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                try (Workbook wb = WorkbookFactory.create(is)) {
                    for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                        for (Row r : wb.getSheetAt(i)) {
                            for (Cell c : r) {
                                if (c.getCellType() == CellType.STRING) {
                                    String v = c.getStringCellValue();
                                    for (Map.Entry<String, String> e : map.entrySet()) {
                                        v = v.replace(e.getKey(), e.getValue());
                                    }
                                    c.setCellValue(v);
                                }
                            }
                        }
                    }
                    try (FileOutputStream fos = new FileOutputStream(temp)) { wb.write(fos); }
                    success = true;
                }
            }

            if (success && temp.exists()) {
                Files.copy(temp.toPath(), src.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log("[处理成功] " + src.getName());
            }
        } catch (Exception e) {
            log("[报错跳过]" + src.getName() + " -> " + e.getMessage());
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    // --- 新增：PPTX 替换逻辑辅助方法 ---
    private void replaceInPptx(XSLFTextShape shape, Map<String, String> map) {
        for (XSLFTextParagraph p : shape.getTextParagraphs()) {
            List<XSLFTextRun> runs = p.getTextRuns();
            if (runs.isEmpty()) continue;

            // 获取整段文本进行匹配（防止关键字被拆分在不同 Run 中）
            String pText = "";
            for (XSLFTextRun run : runs) pText += run.getRawText();

            boolean hit = false;
            for (String key : map.keySet()) {
                if (pText.contains(key)) { hit = true; break; }
            }

            if (hit) {
                // 替换文本
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    pText = pText.replace(entry.getKey(), entry.getValue());
                }

                // 保留第一个 Run 的样式，清除旧内容并写入新文本
                XSLFTextRun firstRun = runs.get(0);
                String text = pText;

                // 简单的替换策略：清空所有 Run，重新在第一个 Run 写入替换后的全量文本
                // 这样可以最大程度保持该段落的初始字体颜色和大小
                for (int i = runs.size() - 1; i >= 1; i--) {
                    runs.get(i).setText("");
                }
                firstRun.setText(text);
            }
        }
    }

    private void replaceInDocx(XWPFParagraph p, Map<String, String> map) {
        String pText = p.getText();
        if (pText == null || pText.isEmpty()) return;
        boolean hit = false;
        for (String key : map.keySet()) {
            if (pText.contains(key)) { hit = true; break; }
        }
        if (hit) {
            String newText = pText;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                newText = newText.replace(entry.getKey(), entry.getValue());
            }
            List<XWPFRun> runs = p.getRuns();
            if (!runs.isEmpty()) {
                XWPFRun origin = runs.get(0);
                String fontFamily = origin.getFontFamily();
                int fontSize = origin.getFontSize();
                String color = origin.getColor();
                boolean isBold = origin.isBold();
                boolean isItalic = origin.isItalic();
                UnderlinePatterns underline = origin.getUnderline();
                for (int i = runs.size() - 1; i >= 0; i--) p.removeRun(i);
                XWPFRun newRun = p.createRun();
                newRun.setText(newText);
                if (fontFamily != null) newRun.setFontFamily(fontFamily);
                if (fontSize != -1) newRun.setFontSize(fontSize);
                if (color != null) newRun.setColor(color);
                newRun.setBold(isBold);
                newRun.setItalic(isItalic);
                newRun.setUnderline(underline);
                newRun.getCTR().addNewRPr().addNewRFonts().setEastAsia(fontFamily);
            }
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}