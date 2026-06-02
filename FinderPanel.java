package me.shaoxia;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ItemEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/** 文件检索助手 FinderPanel - 暗黑扁平化升级版 */
public class FinderPanel extends JPanel {
    private JTextField pathDisplayField;
    private JTextArea rulesTextArea;
    private JTable fileTable;
    private DefaultTableModel tableModel;
    private List<File> allFiles = new ArrayList<>();
    private JLabel statusLabel;

    // 文件类型过滤复选框
    private JCheckBox chkWord, chkExcel, chkPdf, chkOther;

    // 统一定义暗色主题颜色
    private final Color BG_MAIN = new Color(43, 45, 48);       // 主背景
    private final Color BG_PANEL = new Color(30, 31, 34);      // 次背景 (表格、输入框)
    private final Color FG_TEXT = new Color(169, 183, 198);    // 主文本色
    private final Color ACCENT_COLOR = new Color(71, 114, 179); // 高亮色
    private final Color BORDER_COLOR = new Color(86, 90, 94);  // 边框色

    public FinderPanel(MainLauncher parent) {
        setLayout(new BorderLayout(10, 10));
        setBackground(BG_MAIN);
        setBorder(new EmptyBorder(10, 10, 10, 10)); // 全局外边距

        // --- 顶部控制栏 ---
        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setOpaque(false);

        JButton backBtn = createStyledButton(" << 返回 ");
        backBtn.addActionListener(e -> parent.showMenu());

        JPanel btnGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnGroup.setOpaque(false);
        JButton loadBtn = createStyledButton("1. 选择文件夹扫描");
        loadBtn.addActionListener(e -> selectAndLoad());
        JButton extractBtn = createStyledButton("2. 一键提取核心文件");
        extractBtn.addActionListener(e -> quickExtract());
        JButton showAllBtn = createStyledButton("3. 刷新全览");
        showAllBtn.addActionListener(e -> {
            resetFilters();
            filterFiles();
        });
        btnGroup.add(loadBtn);
        btnGroup.add(extractBtn);
        btnGroup.add(showAllBtn);

        // 多选筛选区
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        filterPanel.setOpaque(false);
        JLabel filterIcon = new JLabel("类型筛选: ");
        filterIcon.setForeground(FG_TEXT);
        filterPanel.add(filterIcon);

        chkWord = createStyledCheckBox("Word");
        chkExcel = createStyledCheckBox("Excel");
        chkPdf = createStyledCheckBox("PDF");
        chkOther = createStyledCheckBox("其他");

        // 默认全部勾选
        resetFilters();

        // 绑定状态改变事件
        chkWord.addItemListener(e -> filterFiles());
        chkExcel.addItemListener(e -> filterFiles());
        chkPdf.addItemListener(e -> filterFiles());
        chkOther.addItemListener(e -> filterFiles());

        filterPanel.add(chkWord);
        filterPanel.add(chkExcel);
        filterPanel.add(chkPdf);
        filterPanel.add(chkOther);

        topBar.add(backBtn, BorderLayout.WEST);
        topBar.add(btnGroup, BorderLayout.CENTER);
        topBar.add(filterPanel, BorderLayout.EAST);

        // 路径展示框
        pathDisplayField = new JTextField(" 尚未选择文件夹...");
        pathDisplayField.setEditable(false);
        pathDisplayField.setBackground(BG_PANEL);
        pathDisplayField.setForeground(new Color(98, 170, 107)); // 柔和的绿色
        pathDisplayField.setBorder(new LineBorder(BORDER_COLOR, 1, true));
        pathDisplayField.setPreferredSize(new Dimension(0, 35));
        pathDisplayField.setFont(new Font("微软雅黑", Font.PLAIN, 13));

        JPanel northStack = new JPanel(new BorderLayout(0, 10));
        northStack.setOpaque(false);
        northStack.add(topBar, BorderLayout.NORTH);
        northStack.add(pathDisplayField, BorderLayout.SOUTH);

        // --- 左侧提取规则区 ---
        rulesTextArea = new JTextArea("评审报告\n专家签到表\n应答文件上传情况统计\n项目编号-综合报表");
        rulesTextArea.setBackground(BG_PANEL);
        rulesTextArea.setForeground(FG_TEXT);
        rulesTextArea.setCaretColor(Color.WHITE);
        rulesTextArea.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        rulesTextArea.setBorder(new EmptyBorder(5, 5, 5, 5));

        JScrollPane rulesScroll = new JScrollPane(rulesTextArea);
        rulesScroll.setPreferredSize(new Dimension(280, 0));
        rulesScroll.setBorder(BorderFactory.createTitledBorder(
                new LineBorder(BORDER_COLOR), "关键文件名提取规则 (逐行匹配)",
                0, 0, new Font("微软雅黑", Font.BOLD, 12), FG_TEXT));
        rulesScroll.getViewport().setBackground(BG_MAIN);

        // --- 中央文件表格区 ---
        tableModel = new DefaultTableModel(new String[]{"文件名", "所在路径", "大小", "修改日期"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 禁止直接编辑表格内容
            }
        };
        fileTable = new JTable(tableModel);
        styleTable(fileTable);

        // 文件拖拽导出功能保持不变
        fileTable.setDragEnabled(true);
        fileTable.setTransferHandler(new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent c) {
                int[] rows = fileTable.getSelectedRows();
                if (rows.length == 0) return null;
                List<File> files = new ArrayList<>();
                for (int r : rows) {
                    files.add(new File((String) tableModel.getValueAt(r, 1), (String) tableModel.getValueAt(r, 0)));
                }
                return new Transferable() {
                    public DataFlavor[] getTransferDataFlavors() {
                        return new DataFlavor[]{DataFlavor.javaFileListFlavor};
                    }
                    public boolean isDataFlavorSupported(DataFlavor f) {
                        return DataFlavor.javaFileListFlavor.equals(f);
                    }
                    public Object getTransferData(DataFlavor f) {
                        return files;
                    }
                };
            }
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }
        });

        JScrollPane tableScroll = new JScrollPane(fileTable);
        tableScroll.setBorder(new LineBorder(BORDER_COLOR));
        tableScroll.getViewport().setBackground(BG_PANEL);

        // --- 底部状态栏 ---
        statusLabel = new JLabel(" 就绪");
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        // --- 组装整体布局 ---
        add(northStack, BorderLayout.NORTH);
        add(rulesScroll, BorderLayout.WEST);
        add(tableScroll, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    // --- 业务逻辑 ---

    private void selectAndLoad() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File sel = chooser.getSelectedFile();
            pathDisplayField.setText(" 当前扫描目录: " + sel.getAbsolutePath());
            scanDirectory(sel);
        }
    }

    private void scanDirectory(File dir) {
        allFiles.clear();
        statusLabel.setText(" 正在扫描，请稍候...");
        new Thread(() -> {
            recursiveWalk(dir);
            SwingUtilities.invokeLater(() -> {
                filterFiles();
                statusLabel.setText(" 扫描完成，共找到文件: " + allFiles.size() + " 个");
            });
        }).start();
    }

    private void recursiveWalk(File f) {
        File[] list = f.listFiles();
        if (list != null) {
            for (File child : list) {
                if (child.isDirectory()) recursiveWalk(child);
                else allFiles.add(child);
            }
        }
    }

    /** 基于复选框进行多选文件类型过滤 */
    private void filterFiles() {
        tableModel.setRowCount(0);

        boolean showWord = chkWord.isSelected();
        boolean showExcel = chkExcel.isSelected();
        boolean showPdf = chkPdf.isSelected();
        boolean showOther = chkOther.isSelected();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        for (File f : allFiles) {
            String name = f.getName().toLowerCase();
            boolean match = false;

            if (showWord && (name.endsWith(".doc") || name.endsWith(".docx"))) match = true;
            else if (showExcel && (name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".csv"))) match = true;
            else if (showPdf && name.endsWith(".pdf")) match = true;
            else if (showOther && !(name.endsWith(".doc") || name.endsWith(".docx") ||
                    name.endsWith(".xls") || name.endsWith(".xlsx") ||
                    name.endsWith(".csv") || name.endsWith(".pdf"))) {
                match = true;
            }

            if (match) {
                String size = f.length() / 1024 + " KB";
                String date = sdf.format(f.lastModified());
                tableModel.addRow(new Object[]{f.getName(), f.getParent(), size, date});
            }
        }
    }

    private void quickExtract() {
        String[] rules = rulesTextArea.getText().split("\n");
        tableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        int matchCount = 0;
        for (File f : allFiles) {
            String name = f.getName().toLowerCase();
            for (String r : rules) {
                if (!r.trim().isEmpty() && name.contains(r.trim().toLowerCase())) {
                    String size = f.length() / 1024 + " KB";
                    String date = sdf.format(f.lastModified());
                    tableModel.addRow(new Object[]{f.getName(), f.getParent(), size, date});
                    matchCount++;
                    break;
                }
            }
        }
        statusLabel.setText(" 核心文件提取完成，共匹配到: " + matchCount + " 个");
    }

    private void resetFilters() {
        // 移除监听器防止初始化时多次触发重绘，这里为了简单直接静默设值
        chkWord.setSelected(true);
        chkExcel.setSelected(true);
        chkPdf.setSelected(true);
        chkOther.setSelected(true);
    }

    // --- UI 样式辅助方法 ---

    private JButton createStyledButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setBackground(new Color(60, 63, 65));
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR),
                new EmptyBorder(5, 12, 5, 12)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JCheckBox createStyledCheckBox(String text) {
        JCheckBox chk = new JCheckBox(text);
        chk.setOpaque(false);
        chk.setForeground(FG_TEXT);
        chk.setFocusPainted(false);
        chk.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        chk.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return chk;
    }

    private void styleTable(JTable table) {
        table.setBackground(BG_PANEL);
        table.setForeground(FG_TEXT);
        table.setSelectionBackground(ACCENT_COLOR);
        table.setSelectionForeground(Color.WHITE);
        table.setGridColor(BG_MAIN);
        table.setRowHeight(28);
        table.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        table.setShowVerticalLines(false); // 隐藏垂直网格线，更显现代感

        // 表头样式
        JTableHeader header = table.getTableHeader();
        header.setBackground(BG_MAIN);
        header.setForeground(Color.WHITE);
        header.setFont(new Font("微软雅黑", Font.BOLD, 13));
        header.setBorder(new LineBorder(BORDER_COLOR));
        header.setPreferredSize(new Dimension(0, 30));

        // 调整列宽分配
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(350);
        table.getColumnModel().getColumn(1).setPreferredWidth(350);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(140);

        // 设置部分列居中显示
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        table.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
    }
}