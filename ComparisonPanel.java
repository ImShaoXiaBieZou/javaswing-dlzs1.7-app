package me.shaoxia;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** * 公告比对助手 ComparisonPanel
 * 完美继承 BackgroundPanel，自动支持 src 目录下图片加载
 */
public class ComparisonPanel extends BackgroundPanel {
    private MainLauncher parent;
    private JTextArea logArea;
    private File wordFile, pdfFile;
    private JLabel wordLabel, pdfLabel;

    public ComparisonPanel(MainLauncher parent) {
        // 核心修改 1：通过 super 完美复用 BackgroundPanel 的本地/src图片寻找机制
        super("texture2.png");
        this.parent = parent;

        // 整体布局：增加顶部和底部的垂直间距
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(50, 60, 40, 60));

        // 3. 顶部：拖拽区域 (圆弧实线框)
        JPanel dropPanel = new JPanel(new GridLayout(1, 2, 60, 0));
        dropPanel.setOpaque(false);
        wordLabel = createDropBox("【请拖入 DOCX 采购公告】");
        pdfLabel = createDropBox("【拖入 PDF 文档】");
        setupDND(wordLabel, true);
        setupDND(pdfLabel, false);
        dropPanel.add(wordLabel);
        dropPanel.add(pdfLabel);

        // 4. 中部：日志区域 (增加与顶部的距离)
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("微软雅黑", Font.BOLD, 16));
        logArea.setForeground(Color.WHITE);
        logArea.setOpaque(false);
        logArea.setLineWrap(true);
        logArea.setMargin(new Insets(15, 20, 15, 20));
        logArea.setText(">>> halo！等待您的启动ty！\n");

        JScrollPane scrollPane = new JScrollPane(logArea) {
            @Override
            protected void paintComponent(Graphics g) {
                // 日志框圆弧背景
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 80)); // 半透明黑
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                g2.setColor(new Color(255, 255, 255, 100)); // 白色实线边框
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 25, 25);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(new EmptyBorder(5, 5, 5, 5));

        // 用一个带边距的面板包裹日志区，产生物理距离
        JPanel logContainer = new JPanel(new BorderLayout());
        logContainer.setOpaque(false);
        logContainer.setBorder(new EmptyBorder(40, 0, 40, 0)); // 这是与上下的距离
        logContainer.add(scrollPane, BorderLayout.CENTER);

        // 5. 底部：圆弧实体按钮
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 60, 0));
        btnPanel.setOpaque(false);

        JButton btnRun = createRoundBtn("开始校对", new Color(231, 76, 60));
        JButton btnBack = createRoundBtn("返回主菜单", new Color(80, 80, 80));

        btnRun.addActionListener(e -> startMatch());
        btnBack.addActionListener(e -> parent.showMenu());

        btnPanel.add(btnRun);
        btnPanel.add(btnBack);

        add(dropPanel, BorderLayout.NORTH);
        add(logContainer, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    // 核心修改 2：彻底删除了原先的 paintComponent 重写，完美交由父类绘制，不再扭曲

    // 按钮圆弧重绘逻辑
    private JButton createRoundBtn(String text, Color baseColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) g2.setColor(baseColor.darker());
                else if (getModel().isRollover()) g2.setColor(baseColor.brighter());
                else g2.setColor(baseColor);

                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30); // 彻底圆弧
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
                g2.dispose();
            }
        };
        btn.setFont(new Font("微软雅黑", Font.BOLD, 15));
        btn.setPreferredSize(new Dimension(180, 50));
        btn.setContentAreaFilled(false); // 去掉默认方块底色
        btn.setBorderPainted(false);     // 去掉默认边框
        btn.setFocusPainted(false);
        return btn;
    }

    // 拖拽框圆弧重绘
    private JLabel createDropBox(String t) {
        JLabel l = new JLabel(t, JLabel.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2)); // 实线
                g2.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 30, 30);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        l.setPreferredSize(new Dimension(300, 150));
        l.setForeground(Color.WHITE);
        l.setFont(new Font("微软雅黑", Font.BOLD, 16));
        return l;
    }

    private void startMatch() {
        if (wordFile == null || pdfFile == null) {
            log("▲ 请先拖入文件！");
            return;
        }
        log(">>> 正在努力打工...");
        new Thread(() -> {
            try {
                String w = readWord(wordFile);
                String p = readPdf(pdfFile);
                SwingUtilities.invokeLater(() -> executeCompare(w, p));
            } catch (Exception ex) {
                log("▲ 出错: 可能是你的文件放反了！" + ex.getMessage());
            }
        }).start();
    }

    private void executeCompare(String w, String p) {
        log("--- 终于完成啦！ ---");
        boolean isAllPassed = true;

        String wNo = find(w, "采购编号[:：]?\\s*([^\\s]+)");
        String pNo = find(p, "(?:采购项目编号|招标编号|采购编号)[:：]?\\s*([^\\s\\n]+)");

        if (wNo != null) {
            wNo = wNo.replaceAll("[)）]+$", "").trim();
        }
        if (pNo != null) {
            pNo = pNo.replaceAll("[)）]+$", "").trim();
        }

        if (!checkResult(wNo, pNo, "【项目编号】")) {
            isAllPassed = false;
        }

        String wc = w.replaceAll("\\s", "");
        String pc = p.replaceAll("\\s", "");

        String wT1 = find(wc, "至(\\d{4}年\\d{1,2}月\\d{1,2}日(上午|下午)?\\d{1,2}时(?:\\d{1,2}分)?)");
        String pT1 = find(pc, "(?:采购文件获取截止时间|获取时间.*?到)(\\d{4}[年-]\\d{1,2}[月-]\\d{1,2}日?\\s*(?:上午|下午)?\\d{1,2}时?\\s*(?::|分)?\\d{1,2}分?)");

        if (!checkTimeResult(wT1, pT1, "【获取截止时间】")) {
            isAllPassed = false;
        }

        String wT2 = find(wc, "截止时间.*?(\\d{4}年\\d{1,2}月\\d{1,2}日(上午|下午)?\\d{1,2}时\\d{1,2}分)");
        String pT2 = find(pc, "(?:开启应答文件时间|递交截止时间|首次应答文件提交的截止时间).*?(\\d{4}[年-]\\d{1,2}[月-]\\d{1,2}日?\\s*(?:上午|下午)?\\d{1,2}时?\\s*(?::|分)?\\d{1,2}分?)");

        if (!checkTimeResult(wT2, pT2, "【应答截止时间】")) {
            isAllPassed = false;
        }

        final boolean finalStatus = isAllPassed;
        SwingUtilities.invokeLater(() -> {
            if (finalStatus) {
                JOptionPane.showMessageDialog(this, "★ 成功！所有元素完全一致。", "校对结果", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "▲ 失败！存在不一致的信息，请查看详情日志。", "校对结果", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private boolean checkResult(String w, String p, String lab) {
        if (w == null || p == null) {
            log("⚠️ " + lab + " 提取失败");
            return false;
        }
        boolean match = w.trim().equalsIgnoreCase(p.trim());
        log(lab + " -> Word:[" + w.trim() + "] vs PDF:[" + p.trim() + "] -> " + (match ? "★一致" : "▲冲突"));
        return match;
    }

    private boolean checkTimeResult(String wt, String pt, String lab) {
        if (wt == null || pt == null) {
            log("⚠️ " + lab + " 提取数据缺失");
            return false;
        }
        String nW = normalize(wt);
        String nP = pt.replaceAll("[^0-9]", "");
        boolean match = nP.startsWith(nW);
        log(lab + " -> Word:[" + nW + "] vs PDF:[" + nP + "] -> " + (match ? "★一致" : "▲冲突"));
        return match;
    }

    private String normalize(String raw) {
        if (raw == null) return "";

        Matcher m = Pattern.compile("(\\d{4})[年-]?(\\d{1,2})[月-]?(\\d{1,2})日?\\s*(上午|下午)?\\s*(\\d{1,2})[时:]?(\\d{1,2})?分?").matcher(raw);
        if (m.find()) {
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int day = Integer.parseInt(m.group(3));
            int hour = Integer.parseInt(m.group(5));

            if (raw.contains("下午") && hour < 12) hour += 12;
            if (raw.contains("上午") && hour == 12) hour = 0;

            int min = (m.group(6) != null) ? Integer.parseInt(m.group(6)) : 0;

            return String.format("%04d%02d%02d%02d%02d", year, month, day, hour, min);
        }
        return raw.replaceAll("[^0-9]", "");
    }

    private String readWord(File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f); XWPFDocument doc = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) sb.append(p.getText()).append("\n");
            return sb.toString();
        }
    }

    private String readPdf(File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f)) {
            PdfReader reader = new PdfReader(fis);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) sb.append(PdfTextExtractor.getTextFromPage(reader, i)).append("\n");
            reader.close();
            return sb.toString();
        }
    }

    private String find(String t, String r) {
        Matcher m = Pattern.compile(r).matcher(t);
        return m.find() ? m.group(1) : null;
    }

    private void setupDND(JLabel l, boolean isWord) {
        new DropTarget(l, new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    File f = files.get(0);
                    if (isWord) wordFile = f; else pdfFile = f;
                    l.setText("<html><center>★ 已装载<br/>" + f.getName() + "</center></html>");
                    l.setForeground(Color.WHITE);
                } catch (Exception e) {}
            }
        });
    }

    private void log(String m) {
        SwingUtilities.invokeLater(() -> logArea.append(m + "\n"));
    }
}