package me.shaoxia;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 数据校对助手 CheckerPanel (深灰纯净底色对齐版)
 */
public class CheckerPanel extends BackgroundPanel {

    private JLabel infoLabel;
    private JLabel dl;
    private TitledBorder dropBorder;
    private JPanel dropArea;

    private static final Pattern SUSPICIOUS_PATTERN = Pattern.compile(".*(888|999|666|777|000|1234|4321)(\\.0+)?$");

    // 全局统一对齐 FinderPanel 的深灰色调，并设置浅色字体
    private final Color DASHBOARD_BG = new Color(45, 48, 50);
    private final Color TEXT_COLOR = new Color(230, 230, 230);

    public CheckerPanel(MainLauncher parent) {
        super("texture2.png");
        setLayout(new BorderLayout());
        setOpaque(false);

        JButton backBtn = new JButton(" << 返回主菜单 ");
        backBtn.setFocusPainted(false);
        backBtn.addActionListener(e -> parent.showMenu());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        top.add(backBtn);
        add(top, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();

        infoLabel = new JLabel("<html><body style='text-align: center;'><h2>信息协同数据校对助手</h2><p><b></b></p></body></html>");
        infoLabel.setForeground(Color.WHITE);
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 0; gbc.gridx = 0; gbc.weightx = 1.0; gbc.weighty = 0.1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(20, 50, 10, 50);
        centerPanel.add(infoLabel, gbc);

        dropArea = new JPanel(new BorderLayout());
        dropArea.setOpaque(false);

        dropBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.WHITE, 2, true),
                " 拖入 XLSX 进行逻辑校验 ",
                TitledBorder.CENTER, TitledBorder.TOP,
                new Font("微软雅黑", Font.BOLD, 13), Color.WHITE
        );
        dropArea.setBorder(dropBorder);

        dl = new JLabel("请拖入 Excel 报价表", JLabel.CENTER);
        dl.setForeground(Color.WHITE);
        dl.setFont(new Font("微软雅黑", Font.BOLD, 18));
        dropArea.add(dl);
        setCheckDropTarget(dropArea);

        gbc.gridy = 1; gbc.weightx = 1.0; gbc.weighty = 0.8;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(10, 50, 40, 50);
        centerPanel.add(dropArea, gbc);
        add(centerPanel, BorderLayout.CENTER);

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) { adjustFontSize(); }
        });
    }

    private void adjustFontSize() {
        int width = getWidth();
        if (width <= 0) return;
        double scale = Math.max(0.7, Math.min(width / 800.0, 1.4));
        int infoFontSize = (int) (12 * scale);
        int titleFontSize = (int) (16 * scale);

        infoLabel.setText("<html><body style='text-align: center;'>"
                + "<h2 style='font-size:" + titleFontSize + "px; margin: 0; padding: 0;'>信息协同数据校对助手</h2>"
                + "<p style='font-size:" + infoFontSize + "px; margin-top: 8px;'><b></b></p>"
                + "</body></html>");

        dl.setFont(new Font("微软雅黑", Font.BOLD, (int) (18 * scale)));
        dropBorder.setTitleFont(new Font("微软雅黑", Font.BOLD, (int) (13 * scale)));
        dropArea.repaint();
        this.validate();
    }

    private void setCheckDropTarget(JPanel p) {
        new DropTarget(p, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) processExcel(files.get(0));
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });
    }

    static class QuoteDropNode {
        String phaseName;
        Double dropPercent;

        QuoteDropNode(String phaseName, Double dropPercent) {
            this.phaseName = phaseName;
            this.dropPercent = dropPercent;
        }
    }

    private void processExcel(File file) {
        List<String> errors = new ArrayList<>();
        Map<String, List<QuoteDropNode>> dropDataMap = new LinkedHashMap<>();
        Set<String> flaggedCompanies = new HashSet<>();

        try (FileInputStream fis = new FileInputStream(file); Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            int supIdx = -1;
            String[] rounds = {"首轮报价", "二轮报价", "三轮报价", "四轮报价", "五轮报价", "六轮报价", "终轮报价"};
            int[] colIdxMap = new int[rounds.length];
            Arrays.fill(colIdxMap, -1);
            boolean foundHeader = false;

            Map<Integer, Map<String, List<String>>> percentDuplicateMap = new HashMap<>();
            Map<Integer, Map<String, List<String>>> valueDuplicateMap = new HashMap<>();

            for (Row row : sheet) {
                if (!foundHeader) {
                    for (Cell cell : row) {
                        String val = getCellValue(cell);
                        if (val.contains("供应商名称")) supIdx = cell.getColumnIndex();
                        for (int i = 0; i < rounds.length; i++) {
                            if (val.equals(rounds[i])) colIdxMap[i] = cell.getColumnIndex();
                        }
                    }
                    if (supIdx != -1) foundHeader = true;
                    continue;
                }

                String company = getCellValue(row.getCell(supIdx));
                if (company.isEmpty() || company.contains("※")) continue;

                Double prevPrice = null;
                String prevRoundName = null;
                List<QuoteDropNode> companyDrops = new ArrayList<>();

                for (int i = 0; i < colIdxMap.length; i++) {
                    int cIdx = colIdxMap[i];
                    if (cIdx == -1) continue;
                    Double currPrice = getNumericValue(row.getCell(cIdx));

                    if (currPrice != null) {
                        String priceStr = String.format("%.2f", currPrice);
                        boolean isSuspicious = SUSPICIOUS_PATTERN.matcher(priceStr).matches();

                        if (isSuspicious) {
                            errors.add("【特征预警】" + company + " 在 [" + rounds[i] + "] 出现异常特征号: " + priceStr);
                            flaggedCompanies.add(company);
                        }

                        if (prevPrice != null && prevRoundName != null) {
                            if (currPrice > prevPrice) {
                                errors.add("【涨价异常】" + company + "\n    " + rounds[i] + "(" + currPrice + ") 高于上一轮(" + prevPrice + ")");
                                flaggedCompanies.add(company);
                            }

                            double dropPercent = 0.0;
                            if (prevPrice > 0) {
                                dropPercent = (prevPrice - currPrice) / prevPrice * 100.0;
                                String percentStr = String.format("%.4f", dropPercent);

                                percentDuplicateMap.putIfAbsent(i, new HashMap<>());
                                percentDuplicateMap.get(i).putIfAbsent(percentStr, new ArrayList<>());
                                percentDuplicateMap.get(i).get(percentStr).add(company);

                                double dropValue = prevPrice - currPrice;
                                if (dropValue > 0.0001) {
                                    String valueStr = String.format("%.2f", dropValue);
                                    valueDuplicateMap.putIfAbsent(i, new HashMap<>());
                                    valueDuplicateMap.get(i).putIfAbsent(valueStr, new ArrayList<>());
                                    valueDuplicateMap.get(i).get(valueStr).add(company);
                                }
                            }

                            String phaseName = prevRoundName.replace("报价", "") + "->" + rounds[i].replace("报价", "");
                            companyDrops.add(new QuoteDropNode(phaseName, dropPercent));
                        }
                        prevPrice = currPrice;
                        prevRoundName = rounds[i];
                    }
                }
                if (!companyDrops.isEmpty()) dropDataMap.put(company, companyDrops);
            }

            for (Map.Entry<Integer, Map<String, List<String>>> roundEntry : percentDuplicateMap.entrySet()) {
                String roundName = rounds[roundEntry.getKey()];
                for (Map.Entry<String, List<String>> pEntry : roundEntry.getValue().entrySet()) {
                    List<String> companies = pEntry.getValue();
                    if (companies.size() > 1 && Double.parseDouble(pEntry.getKey()) > 0.0001) {
                        errors.add("【一致比例降幅】" + roundName + " 降幅: " + pEntry.getKey() + "%\n    涉及: " + String.join(", ", companies));
                        flaggedCompanies.addAll(companies);
                    }
                }
            }

            for (Map.Entry<Integer, Map<String, List<String>>> roundEntry : valueDuplicateMap.entrySet()) {
                String roundName = rounds[roundEntry.getKey()];
                for (Map.Entry<String, List<String>> vEntry : roundEntry.getValue().entrySet()) {
                    List<String> companies = vEntry.getValue();
                    if (companies.size() > 1) {
                        errors.add("【一致定额降幅】" + roundName + " 降幅: " + vEntry.getKey() + " 万元\n    涉及: " + String.join(", ", companies));
                        flaggedCompanies.addAll(companies);
                    }
                }
            }

            if (errors.isEmpty() && flaggedCompanies.isEmpty()) {
                JOptionPane.showMessageDialog(null, "校对完成，数据逻辑正常。");
            } else {
                Map<String, List<QuoteDropNode>> flaggedHistory = new HashMap<>();
                for (String c : flaggedCompanies) {
                    if (dropDataMap.containsKey(c)) flaggedHistory.put(c, dropDataMap.get(c));
                }
                showResultsDialog(errors, flaggedHistory);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "解析失败，请检查文件格式。");
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        try {
            if (cell.getCellType() == CellType.NUMERIC) return String.valueOf(cell.getNumericCellValue());
            return cell.getStringCellValue().trim();
        } catch (Exception e) { return ""; }
    }

    private Double getNumericValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA) return cell.getNumericCellValue();
            return Double.parseDouble(cell.getStringCellValue().trim());
        } catch (Exception e) { return null; }
    }

    // ================== 三分布局 UI 组装 (深灰透明穿透底色) ==================

    private void showResultsDialog(List<String> errors, Map<String, List<QuoteDropNode>> dropData) {
        JDialog dialog = new JDialog((Frame) null, "校对结果综合看板", true);
        dialog.setSize(1250, 700);
        dialog.setLocationRelativeTo(null);
        dialog.setLayout(new BorderLayout());

        // 统一对话框的绝对背景色
        dialog.getContentPane().setBackground(DASHBOARD_BG);

        // 1. 左侧：预警明细
        JTextArea logArea = new JTextArea(String.join("\n\n", errors));
        logArea.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setMargin(new Insets(10, 10, 10, 10));
        logArea.setBackground(DASHBOARD_BG);
        logArea.setForeground(TEXT_COLOR); // 设置亮色文字

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setOpaque(false); // 关键修复：剥夺自带底色
        logScroll.getViewport().setOpaque(false);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "三项校验预警明细",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("微软雅黑", Font.BOLD, 13), TEXT_COLOR));

        // 2. 中间：走势图面板
        DropCorrelationChartPanel chartPanel = new DropCorrelationChartPanel(dropData);
        chartPanel.setOpaque(false); // 关键修复：允许底层灰色透出
        chartPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "异常供应商降幅走势",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("微软雅黑", Font.BOLD, 13), TEXT_COLOR));

        // 3. 右侧：各家百分比列表
        JTextArea detailArea = new JTextArea();
        detailArea.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        detailArea.setEditable(false);
        detailArea.setBackground(DASHBOARD_BG);
        detailArea.setForeground(TEXT_COLOR); // 设置亮色文字
        detailArea.setMargin(new Insets(10, 10, 10, 10));

        StringBuilder detailSb = new StringBuilder();
        for (Map.Entry<String, List<QuoteDropNode>> entry : dropData.entrySet()) {
            detailSb.append("【").append(entry.getKey()).append("】\n");
            for (QuoteDropNode node : entry.getValue()) {
                detailSb.append("  ").append(node.phaseName).append(" : ")
                        .append(String.format("%.2f%%", node.dropPercent)).append("\n");
            }
            detailSb.append("\n");
        }
        detailArea.setText(detailSb.toString());
        detailArea.setCaretPosition(0);

        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setOpaque(false); // 关键修复：剥夺自带底色
        detailScroll.getViewport().setOpaque(false);
        detailScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), "各轮降幅百分比明细",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("微软雅黑", Font.BOLD, 13), TEXT_COLOR));
        detailScroll.setPreferredSize(new Dimension(280, 0));

        // 核心修复：将所有的 SplitPane 强制设置为透明，防止系统自带的分割线颜色遮挡
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chartPanel, detailScroll);
        rightSplit.setOpaque(false);
        rightSplit.setResizeWeight(1.0);
        rightSplit.setDividerSize(4);
        rightSplit.setBorder(BorderFactory.createEmptyBorder());

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, logScroll, rightSplit);
        mainSplit.setOpaque(false);
        mainSplit.setDividerLocation(420);
        mainSplit.setDividerSize(4);
        mainSplit.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        dialog.add(mainSplit, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    // ================== 图表渲染与原生悬浮引擎 ==================

    class DropCorrelationChartPanel extends JPanel {
        private Map<String, List<QuoteDropNode>> data;
        private Color[] lineColors = {
                new Color(231, 76, 60), new Color(52, 152, 219), new Color(46, 204, 113),
                new Color(155, 89, 182), new Color(241, 196, 15), new Color(230, 126, 34)
        };

        private double scale = 1.0;
        private double translateX = 0.0;
        private double translateY = 0.0;
        private Point dragStart = null;

        private Map<Rectangle, String> tooltipMap = new HashMap<>();

        public DropCorrelationChartPanel(Map<String, List<QuoteDropNode>> data) {
            this.data = data;

            ToolTipManager.sharedInstance().registerComponent(this);
            setToolTipText("");

            addMouseWheelListener(e -> {
                double oldScale = scale;
                if (e.getWheelRotation() < 0) scale *= 1.15;
                else scale /= 1.15;
                scale = Math.max(0.3, Math.min(scale, 10.0));

                double f = scale / oldScale;
                translateX = e.getX() - f * (e.getX() - translateX);
                translateY = e.getY() - f * (e.getY() - translateY);
                repaint();
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { dragStart = e.getPoint(); }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart != null) {
                        translateX += e.getX() - dragStart.getX();
                        translateY += e.getY() - dragStart.getY();
                        dragStart = e.getPoint();
                        repaint();
                    }
                }
            });
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            Point p = event.getPoint();
            for (Map.Entry<Rectangle, String> entry : tooltipMap.entrySet()) {
                if (entry.getKey().contains(p)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            tooltipMap.clear();

            if (data == null || data.isEmpty()) return;

            AffineTransform saveAT = g2d.getTransform();
            g2d.translate(translateX, translateY);
            g2d.scale(scale, scale);
            AffineTransform logicalAT = g2d.getTransform();

            int padding = 60;
            int width = Math.max(1, getWidth() - padding * 2);
            int height = Math.max(1, getHeight() - padding * 2);

            List<String> allPhases = new ArrayList<>();
            double maxDrop = 0.0, minDrop = 0.0;
            for (List<QuoteDropNode> drops : data.values()) {
                for (QuoteDropNode node : drops) {
                    if (!allPhases.contains(node.phaseName)) allPhases.add(node.phaseName);
                    if (node.dropPercent > maxDrop) maxDrop = node.dropPercent;
                    if (node.dropPercent < minDrop) minDrop = node.dropPercent;
                }
            }
            if (maxDrop == minDrop) { maxDrop += 5; minDrop -= 5; }
            maxDrop += 2;

            // 修复深色模式下的网格线颜色
            g2d.setColor(new Color(80, 80, 80));
            g2d.drawLine(padding, padding, padding, padding + height);
            int zeroY = padding + height - (int) ((0 - minDrop) / (maxDrop - minDrop) * height);
            g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{5.0f}, 0.0f));
            g2d.drawLine(padding, zeroY, padding + width, zeroY);

            // X 轴文字颜色
            g2d.setColor(TEXT_COLOR);
            g2d.setFont(new Font("微软雅黑", Font.BOLD, 12));
            int phaseCount = Math.max(1, allPhases.size());
            int xStep = width / phaseCount;
            for (int i = 0; i < allPhases.size(); i++) {
                int x = padding + i * xStep + (xStep / 2);
                g2d.drawString(allPhases.get(i), x - 20, padding + height + 25);
            }

            int colorIdx = 0;
            g2d.setStroke(new BasicStroke(2.0f));

            for (Map.Entry<String, List<QuoteDropNode>> entry : data.entrySet()) {
                Color c = lineColors[colorIdx % lineColors.length];
                List<QuoteDropNode> drops = entry.getValue();
                String companyName = entry.getKey();

                Integer prevX = null, prevY = null;
                for (QuoteDropNode node : drops) {
                    int phaseIdx = allPhases.indexOf(node.phaseName);
                    if (phaseIdx == -1) continue;

                    int x = padding + phaseIdx * xStep + (xStep / 2);
                    int y = padding + height - (int) ((node.dropPercent - minDrop) / (maxDrop - minDrop) * height);

                    g2d.setColor(c);
                    if (prevX != null && prevY != null) g2d.drawLine(prevX, prevY, x, y);
                    g2d.fillOval(x - 5, y - 5, 10, 10);

                    Point2D.Double src = new Point2D.Double(x, y);
                    Point2D.Double dst = new Point2D.Double();
                    logicalAT.transform(src, dst);
                    Rectangle pointRect = new Rectangle((int)dst.x - 7, (int)dst.y - 7, 14, 14);
                    tooltipMap.put(pointRect, companyName + " (" + node.phaseName + ")");

                    prevX = x; prevY = y;
                }
                colorIdx++;
            }

            g2d.setTransform(saveAT);

            int legendY = 20;
            colorIdx = 0;
            for (Map.Entry<String, List<QuoteDropNode>> entry : data.entrySet()) {
                Color c = lineColors[colorIdx % lineColors.length];
                g2d.setColor(c);
                g2d.fillRect(getWidth() - 130, legendY, 15, 15);
                g2d.setColor(TEXT_COLOR); // 修复图例文字颜色
                g2d.setFont(new Font("微软雅黑", Font.PLAIN, 12));

                String fullCompanyName = entry.getKey();
                String shortName = fullCompanyName.length() > 6 ? fullCompanyName.substring(0,6) + ".." : fullCompanyName;
                g2d.drawString(shortName, getWidth() - 105, legendY + 12);

                Rectangle legRect = new Rectangle(getWidth() - 130, legendY, 120, 15);
                tooltipMap.put(legRect, fullCompanyName);

                legendY += 25;
                colorIdx++;
            }

            g2d.setColor(Color.GRAY);
            g2d.drawString("", 15, 25);//下方提示
        }
    }
}