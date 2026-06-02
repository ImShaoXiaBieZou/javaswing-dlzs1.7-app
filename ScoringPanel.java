package me.shaoxia;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**评分生成助手 ScoringPanel*/

public class ScoringPanel extends JPanel
{
    private JTabbedPane tabbedPane;
    private JButton exportBtn;
    private List<TableDataHolder> extractedTables = new ArrayList<>();

    public ScoringPanel(MainLauncher parent) {
        setLayout(new BorderLayout(10, 10));
        setBackground(new Color(45, 48, 50));

        // 1. 顶部返回按钮区
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBar.setOpaque(false);
        JButton backBtn = new JButton(" << 返回主菜单 ");
        backBtn.addActionListener(e -> parent.showMenu());
        topBar.add(backBtn);

        // 2. 固定的拖拽区域（锁定高度，生成表格后位置雷打不动）
        JLabel dropLabel = new JLabel("<html><center>" + "<font face='monospaced' size='6' color='white'>" +
                "WE ARE ALL GREAT \uD83D\uDC12APES\uD83D\uDC12<br>" +
                "<font size='4' color='#A0A0A0'>请将 Word 采购文件拖拽至此区域</font>" +
                "</font></center></html>", JLabel.CENTER);

        dropLabel.setFont(new Font("微软雅黑", Font.BOLD, 22));
        dropLabel.setPreferredSize(new Dimension(0, 140)); // 固定高度区域
        dropLabel.setOpaque(true);
        dropLabel.setBackground(new Color(30, 30, 30));
        dropLabel.setForeground(new Color(212, 175, 55));
        dropLabel.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 3, 5, 2, true));

        // 用一个容器把返回按钮和固定拖拽区打包放在北边
        JPanel northPanel = new JPanel(new BorderLayout(5, 5));
        northPanel.setOpaque(false);
        northPanel.add(topBar, BorderLayout.NORTH);
        northPanel.add(dropLabel, BorderLayout.CENTER);
        add(northPanel, BorderLayout.NORTH);

        // 3. 中间选项卡面板初始化
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("微软雅黑", Font.PLAIN, 13));

        // 确保整体选项卡面板具备自适应滚动能力
        JScrollPane contentScroll = new JScrollPane(tabbedPane);
        contentScroll.setBorder(BorderFactory.createEmptyBorder());
        contentScroll.setOpaque(false);
        contentScroll.getViewport().setOpaque(false);
        contentScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        contentScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        add(contentScroll, BorderLayout.CENTER); // 放入主界面中央

        // 4. 底部导出按钮区
        exportBtn = new JButton(" 一键导出 Excel");
        exportBtn.setFont(new Font("微软雅黑", Font.BOLD, 20));
        exportBtn.setPreferredSize(new Dimension(0, 60));
        exportBtn.setEnabled(false);
        exportBtn.addActionListener(e -> exportToExcel());
        add(exportBtn, BorderLayout.SOUTH);

        // 原封不动的拖拽监听事件
        new DropTarget(dropLabel, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) parseDocx(files.get(0));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    // 您的核心解析代码
    private void parseDocx(File file) {
        tabbedPane.removeAll();
        extractedTables.clear();
        try (FileInputStream fis = new FileInputStream(file); XWPFDocument doc = new XWPFDocument(fis)) {
            List<IBodyElement> elements = doc.getBodyElements();

            String currentTitle = "";
            boolean isCapturing = false;

            for (int i = 0; i < elements.size(); i++) {
                IBodyElement el = elements.get(i);

                if (el instanceof XWPFParagraph) {
                    String text = ((XWPFParagraph) el).getText().trim();
                    if (text.isEmpty()) continue;

                    boolean isTargetHeader = (text.contains("技术评分标准") || text.contains("商务评分标准")
                            || text.contains("技术评审细则") || text.contains("商务评审细则"))
                            && (text.contains("%") || text.contains("权重"));

                    if (isTargetHeader) {
                        currentTitle = text;
                        isCapturing = true;
                        continue;
                    }

                    if (isCapturing && (text.startsWith("第") && text.contains("章") || text.contains("应答文件格式"))) {
                        if (!isTargetHeader) {
                            isCapturing = false;
                        }
                    }
                }

                if (isCapturing && el instanceof XWPFTable) {
                    XWPFTable table = (XWPFTable) el;
                    if (table.getRows().size() > 1) {
                        processTable(table, currentTitle);
                    }
                }
            }

            // 状态判断与弹窗提示
            if (!extractedTables.isEmpty()) {
                exportBtn.setEnabled(true);
                JOptionPane.showMessageDialog(this,
                        "检索成功，共找到 " + extractedTables.size() + " 个评分标准表格！",
                        "提示",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                exportBtn.setEnabled(false);
                JOptionPane.showMessageDialog(this,
                        "未检索到符合条件的评分标准表格，请检查文档内容！",
                        "警告",
                        JOptionPane.WARNING_MESSAGE);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "解析过程出错，请检查文档格式或是否被占用");
        }
    }

    private void processTable(XWPFTable table, String title) {
        List<XWPFTableRow> rows = table.getRows();
        XWPFTableRow firstRow = rows.get(0);
        List<Integer> validCols = new ArrayList<>();

        for (int i = 0; i < firstRow.getTableCells().size(); i++) {
            String head = firstRow.getCell(i).getText().trim();
            if (!head.contains("分") && !head.contains("值") && !head.contains("得") && !head.isEmpty()) {
                validCols.add(i);
            }
        }

        if (validCols.isEmpty()) return;

        List<String[]> data = new ArrayList<>();
        String[] lastRowMemory = new String[validCols.size()];
        Arrays.fill(lastRowMemory, "");

        for (int r = 1; r < rows.size(); r++) {
            XWPFTableRow row = rows.get(r);
            List<XWPFTableCell> cells = row.getTableCells();
            String[] rowData = new String[validCols.size()];
            boolean hasContent = false;

            for (int j = 0; j < validCols.size(); j++) {
                int targetIdx = validCols.get(j);
                String currentVal = "";
                if (targetIdx < cells.size()) {
                    currentVal = cells.get(targetIdx).getText().trim().replaceAll("[\\n\\r\\t]+", " ");
                }
                if (currentVal.isEmpty()) {
                    currentVal = lastRowMemory[j];
                } else {
                    lastRowMemory[j] = currentVal;
                }
                rowData[j] = currentVal;
                if (!currentVal.isEmpty()) hasContent = true;
            }
            if (hasContent) data.add(rowData);
        }

        if (!data.isEmpty()) {
            TableDataHolder holder = new TableDataHolder(title, data);
            extractedTables.add(holder);
            displayTable(holder);
        }
    }

    private void displayTable(TableDataHolder holder) {
        DefaultTableModel model = new DefaultTableModel(holder.data.toArray(new Object[0][0]), new String[holder.data.get(0).length]);
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        // 【核心修改点：占满宽度与横拉条兼得】
        // 1. 让表格列在总宽度富余时自动拉伸填满整个横向区域
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        // 2. 为每一列设置合理的最小和推荐宽度，防止列数多的时候被硬生生挤扁
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            column.setPreferredWidth(180); // 期望基准宽度
            column.setMinWidth(100);       // 保证不被压缩得看不见
        }

        // 每一个标签页内的表格单独拥有全方位滚动支持
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tableScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        tabbedPane.addTab(holder.title, tableScroll);
    }

    private void exportToExcel() {
        try {
            File desktop = new File(System.getProperty("user.home"), "Desktop");
            for (TableDataHolder holder : extractedTables) {
                String fileNameBase = holder.title.contains("技术") ? "技术评分标准" : "商务评分标准";
                File targetFile = getUniqueFile(desktop, fileNameBase, "xlsx");
                XSSFWorkbook wb = new XSSFWorkbook();
                XSSFCellStyle style = createBaseStyle(wb);
                XSSFSheet sheet = wb.createSheet("Sheet1");
                List<String[]> data = holder.data;
                for (int r = 0; r < data.size(); r++) {
                    XSSFRow row = sheet.createRow(r);
                    for (int c = 0; c < data.get(r).length; c++) {
                        XSSFCell cell = row.createCell(c);
                        cell.setCellValue(data.get(r)[c]);
                        cell.setCellStyle(style);
                    }
                }
                for (int c = 0; c < data.get(0).length; c++) physicalMerge(sheet, data, c);
                for (int i = 0; i < data.get(0).length; i++) sheet.setColumnWidth(i, 256 * 40);
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    wb.write(fos);
                }
            }
            JOptionPane.showMessageDialog(this, "Excel 导出成功");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private File getUniqueFile(File dir, String baseName, String ext) {
        File file = new File(dir, baseName + "." + ext);
        if (!file.exists()) return file;
        int count = 1;
        while (true) {
            file = new File(dir, baseName + "_副本" + count + "." + ext);
            if (!file.exists()) return file;
            count++;
        }
    }

    private void physicalMerge(XSSFSheet sheet, List<String[]> data, int colIdx) {
        int startRow = 0;
        for (int r = 1; r <= data.size(); r++) {
            String current = (r < data.size()) ? data.get(r)[colIdx] : "EOF";
            String previous = data.get(startRow)[colIdx];
            if (!current.equals(previous)) {
                if (r - 1 > startRow && !previous.isEmpty()) {
                    sheet.addMergedRegion(new CellRangeAddress(startRow, r - 1, colIdx, colIdx));
                }
                startRow = r;
            }
        }
    }

    private XSSFCellStyle createBaseStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    static class TableDataHolder {
        String title;
        List<String[]> data;

        TableDataHolder(String t, List<String[]> d) {
            this.title = t;
            this.data = d;
        }
    }
}