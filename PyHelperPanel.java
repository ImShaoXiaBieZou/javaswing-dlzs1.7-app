package me.shaoxia;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class PyHelperPanel extends JPanel {
    private MainLauncher launcher;
    private JTable scriptTable;
    private DefaultTableModel tableModel;
    private JTextArea detailTextArea;

    // 脚本存放路径
    private final String SCRIPT_RESOURCE_PATH = "/scripts/";
    // Python 安装包内置路径
    private final String PYTHON_ENV_PATH = "/env/python-installer.exe";

    public PyHelperPanel(MainLauncher launcher) {
        this.launcher = launcher;
        setLayout(new BorderLayout());
        setOpaque(false);

        initUI();
    }

    private void initUI() {
        // === 1. 顶部导航栏 ===
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        topBar.setOpaque(false);
        JButton backBtn = new JButton("<< 返回主菜单");
        backBtn.setFont(MainLauncher.MAIN_FONT);
        backBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backBtn.addActionListener(e -> launcher.showMenu());
        topBar.add(backBtn);

        JLabel titleLabel = new JLabel(" PY 脚本助手");
        titleLabel.setFont(MainLauncher.BOLD_FONT);
        titleLabel.setForeground(Color.WHITE);
        topBar.add(titleLabel);

        add(topBar, BorderLayout.NORTH);

        // === 2. 中心区域 (包含表格 和 详细说明框) ===
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 20, 5, 20));

        // -- 表格部分 --
        String[] columnNames = {"编号", "脚本名称", "简述", "文件名", "详细说明"};
        Object[][] rowData = {
                // 【一、 Excel 数据与报表处理篇】
                {"001", "多表一键合并", "一键合并多个同格式的Excel表", "Merge_Excels.py",
                        "【功能详细说明】\n场景：收到了几十个不同单位发来的同格式Excel，一键将它们合并成一张总表。\n核心库：pandas"},

                {"002", "总表条件拆分", "按指定条件拆分总表为多个独立文件", "Split_Excel.py",
                        "【功能详细说明】\n场景：将包含几千行数据的项目总表，按照“项目经理”或“地区”自动拆分为几十个独立的Excel文件并命名。\n核心库：pandas / openpyxl"},

                {"003", "跨文件特定单元格提取", "批量提取多表特定单元格汇总", "Extract_Cells.py",
                        "【功能详细说明】\n场景：遍历一个文件夹下所有的Excel，只提取每个表里的“总金额”和“联系人”所在单元格，汇总成一个新表。\n核心库：openpyxl"},
                /*

                {"004", "两表差异高亮比对", "自动高亮核对新旧版本数据差异", "Compare_Excels.py",
                        "【功能详细说明】\n场景：核对最新版清单和历史版清单，用红色背景色自动高亮出被修改过、删除或新增的单元格。\n核心库：pandas / openpyxl"},
                {"005", "批量清洗不可见字符", "一键清理首尾空格及隐藏换行符", "Clean_Excel_Data.py",
                        "【功能详细说明】\n场景：从网页或老系统导出的Excel经常带有隐藏换行符和首尾空格导致无法计算，该脚本一键全局清洗。\n核心库：pandas"},

                // 【二、 Word 文档与合同流转篇】
                {"006", "跨文档批量全文替换", "批量替换多个文档中的特定文本", "Batch_Replace_Word.py",
                        "【功能详细说明】\n场景：拿旧模板改新材料时，批量把几百个文档里的旧公司名、旧年份、旧项目编号替换为新的。\n核心库：python-docx"},
                {"007", "提取Word表格输出为Excel", "将Word内嵌表格转为可计算Excel", "WordTables_to_Excel.py",
                        "【功能详细说明】\n场景：对方发来一份长达百页的Word文件，里面嵌了十几个参数表格，脚本一键把所有表格扒出来转成可计算的Excel。\n核心库：python-docx / pandas"},
                {"008", "Excel数据批量生成Word报告", "读取表格数据批量生成排版文档", "Excel_to_Word.py",
                        "【功能详细说明】\n场景：提前做好一个Word模板（如通知书、评审报告），读取Excel里的每一行数据，批量生成几百份排版精美的Word。\n核心库：docxtpl"},
                {"009", "多个Word文档按序合并", "无损格式拼接多个独立Word文档", "Merge_Words.py",
                        "【功能详细说明】\n场景：将封面、正文、附件等多个独立的Word文件，不破坏原格式地拼接成一份完整的长文档。\n核心库：docxcompose"},
                {"010", "批量给文档添加水印", "批量为文档每一页添加半透明水印", "Add_Watermark.py",
                        "【功能详细说明】\n场景：文件外发前，批量在Word或生成的PDF每一页对角线上打上“内部绝密”或公司名称的半透明水印。\n核心库：PyPDF2"},

                // 【三、 PDF 文件与公告处理篇】
                {"011", "PDF提取矢量表格", "精准提取PDF表格并还原为Excel", "PDF_Table_Extractor.py",
                        "【功能详细说明】\n场景：遇到网上发布的PDF格式的公示公告或清单，直接提取里面的表格还原成Excel。\n核心库：pdfplumber"},
                {"012", "扫描件OCR文字识别", "批量识别盖章或图片PDF并输出纯文本", "OCR_PDF_to_Text.py",
                        "【功能详细说明】\n场景：对方发来的是盖了章的图片转成的PDF，文字无法复制。脚本调用OCR引擎批量识别并输出纯文本。\n核心库：PaddleOCR / pytesseract"},
                {"013", "批量拆分与提取指定页", "精准剥离PDF指定页或拆解为单页", "Extract_PDF_Pages.py",
                        "【功能详细说明】\n场景：从几百页的标书或图纸中，精准剥离出第5-10页，或者把单页拆解成独立文件。\n核心库：PyPDF2 / PyMuPDF"},
                {"014", "Word/Excel批量转PDF", "调用本地组件一键批量转换为PDF格式", "Office_to_PDF.py",
                        "【功能详细说明】\n场景：定稿后的几十份文件，不需要手动一个个另存为，一键批量转为PDF格式进行归档。\n核心库：win32com"},
                {"015", "批量提取PDF电子书签与目录", "导出树状书签目录为文本或Excel", "Extract_PDF_Bookmarks.py",
                        "【功能详细说明】\n场景：审核大型文档时，把PDF的树状书签目录一键导成Txt或Excel，方便宏观把控文件结构。\n核心库：PyMuPDF (fitz)"},

                // 【四、 综合任务与自动化协同篇】
                {"016", "一键生成项目目录结构", "按配置清单瞬间生成海量嵌套文件夹", "Create_Folders.py",
                        "【功能详细说明】\n场景：接手新业务时，读取Excel里的目录层级设置，一秒钟在本地创建成百上千个带有特定编号的文件夹。\n核心库：os / shutil"},
                {"017", "自动分类归档", "按规则将杂乱文件自动移动至对应归档", "Auto_Sort_Files.py",
                        "【功能详细说明】\n场景：设定规则，一键把杂乱无章的目录按后缀名（或文件名包含的特定项目关键字）自动移动到对应的归档文件夹中。\n核心库：os / shutil"},
                {"018", "批量发送带附件邮件", "读取Excel自动群发带专属附件的邮件", "Batch_Send_Emails.py",
                        "【功能详细说明】\n场景：结果公示后，读取Excel里的邮箱地址列，自动带上针对个人的附件（如通知函），一键群发数百封邮件。\n核心库：smtplib / email"},
                {"019", "一键提取PPT所有高清图片", "无需另存，批量扒出PPT中多媒体素材", "Extract_PPT_Images.py",
                        "【功能详细说明】\n场景：拿到一份绝美的PPT汇报材料，不需要右键一张张另存为，一键把所有多媒体素材（图片、音频）全扒进一个文件夹。\n核心库：python-pptx"},
                {"020", "剪贴板监听器转Excel", "后台监听复制操作并自动追加记录至表格", "Clipboard_to_Excel.py",
                        "【功能详细说明】\n场景：在不同的内网系统里查资料时，只要按 Ctrl+C 复制文本，脚本就在后台自动把文本按行追加记录到Excel里，极其适合高强度收集信息。\n核心库：pyperclip / pandas"}*/

        };

        tableModel = new DefaultTableModel(rowData, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        scriptTable = new JTable(tableModel);

        scriptTable = new JTable(tableModel);
        scriptTable.setFont(MainLauncher.MAIN_FONT);
        scriptTable.setRowHeight(35);
        scriptTable.getTableHeader().setFont(MainLauncher.BOLD_FONT);
        scriptTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // 从视图中隐藏 第4列(文件名) 和 第5列(详细说明)
        scriptTable.getColumnModel().removeColumn(scriptTable.getColumnModel().getColumn(4));
        scriptTable.getColumnModel().removeColumn(scriptTable.getColumnModel().getColumn(3));

        scriptTable.getColumnModel().getColumn(0).setPreferredWidth(80);
        scriptTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        scriptTable.getColumnModel().getColumn(2).setPreferredWidth(350);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        scriptTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);

        JScrollPane tableScrollPane = new JScrollPane(scriptTable);
        centerPanel.add(tableScrollPane, BorderLayout.CENTER);

        // -- 大说明框部分 --
        detailTextArea = new JTextArea("请在上方表格中选择一个脚本以查看详细说明...");
        detailTextArea.setFont(MainLauncher.MAIN_FONT);
        detailTextArea.setEditable(false);
        detailTextArea.setLineWrap(true);
        detailTextArea.setWrapStyleWord(true);
        detailTextArea.setBackground(new Color(43, 45, 48));
        detailTextArea.setForeground(new Color(200, 200, 200));
        detailTextArea.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane detailScrollPane = new JScrollPane(detailTextArea);
        detailScrollPane.setPreferredSize(new Dimension(0, 180));
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(100, 100, 100)),
                " 详细脚本说明 ", TitledBorder.LEFT, TitledBorder.TOP,
                MainLauncher.BOLD_FONT, Color.LIGHT_GRAY);
        detailScrollPane.setBorder(border);

        centerPanel.add(detailScrollPane, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        // === 绑定表格点击事件 ===
        scriptTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = scriptTable.getSelectedRow();
                if (viewRow != -1) {
                    int modelRow = scriptTable.convertRowIndexToModel(viewRow);
                    String details = (String) tableModel.getValueAt(modelRow, 4);
                    detailTextArea.setText(details);
                    detailTextArea.setCaretPosition(0);
                }
            }
        });

        // === 3. 底部操作栏 ===
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        bottomBar.setOpaque(false);

        JButton installPyBtn = new JButton("安装 Python 环境");
        installPyBtn.setFont(MainLauncher.MAIN_FONT);
        installPyBtn.setBackground(new Color(41, 128, 185)); // 蓝色
        installPyBtn.setForeground(Color.WHITE);
        installPyBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        installPyBtn.addActionListener(e -> installBundledPythonEnv());

        // 新增的“一键安装环境库”按钮
        JButton installLibsBtn = new JButton("一键安装环境库");
        installLibsBtn.setFont(MainLauncher.MAIN_FONT);
        installLibsBtn.setBackground(new Color(142, 68, 173)); // 紫色，用于视觉区分
        installLibsBtn.setForeground(Color.WHITE);
        installLibsBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        installLibsBtn.addActionListener(e -> installPythonDependencies());

        JButton exportBtn = new JButton("导出选中脚本到桌面");
        exportBtn.setFont(MainLauncher.MAIN_FONT);
        exportBtn.setBackground(new Color(39, 174, 96)); // 绿色
        exportBtn.setForeground(Color.WHITE);
        exportBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        exportBtn.addActionListener(e -> exportSelectedScript());

        // 依次将三个按钮加入底部操作栏
        bottomBar.add(installPyBtn);
        bottomBar.add(installLibsBtn);
        bottomBar.add(exportBtn);

        add(bottomBar, BorderLayout.SOUTH);
    }

    private void exportSelectedScript() {
        int selectedViewRow = scriptTable.getSelectedRow();
        if (selectedViewRow == -1) {
            JOptionPane.showMessageDialog(this, "请先在表格中选择一个要导出的脚本！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = scriptTable.convertRowIndexToModel(selectedViewRow);
        String scriptName = (String) tableModel.getValueAt(modelRow, 1);
        String fileName = (String) tableModel.getValueAt(modelRow, 3);

        File desktopDir = FileSystemView.getFileSystemView().getHomeDirectory();
        File destFile = new File(desktopDir, fileName);

        try (InputStream is = getClass().getResourceAsStream(SCRIPT_RESOURCE_PATH + fileName)) {
            if (is == null) {
                JOptionPane.showMessageDialog(this,
                        "未找到文件！请检查项目源码的 resources" + SCRIPT_RESOURCE_PATH + " 目录下是否存在：" + fileName,
                        "导出失败", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Files.copy(is, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(this, "脚本【" + scriptName + "】已成功导出到桌面！\n" + destFile.getAbsolutePath(), "成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "导出发生错误：\n" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 一键安装所有必备的 Python 第三方库（使用清华源加速）
     */
    private void installPythonDependencies() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "这将在后台为系统安装所需的 Python 依赖库 (pandas, openpyxl, python-docx, pdfplumber 等)。\n需要电脑保持联网，大概需要 1-2 分钟，是否继续？",
                "配置环境库", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "正在后台静默安装环境库...\n这需要一点时间，安装完成前请勿关闭程序，稍后会弹窗提示结果。", "配置中", JOptionPane.INFORMATION_MESSAGE)
                );

                // 核心命令：调用 pip 并指定清华镜像源
                String[] cmd = {
                        "cmd.exe", "/c",
                        "pip install pandas openpyxl python-docx PyPDF2 Pillow pdfplumber -i https://pypi.tuna.tsinghua.edu.cn/simple --trusted-host pypi.tuna.tsinghua.edu.cn"
                };

                Process process = Runtime.getRuntime().exec(cmd);
                int exitCode = process.waitFor(); // 等待命令执行完成

                if (exitCode == 0) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "太棒了！所有核心依赖库已成功安装！\n现在你可以畅快运行导出的各种自动化脚本了。", "配置成功", JOptionPane.INFORMATION_MESSAGE)
                    );
                } else {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "安装遇到阻力，可能是公司防火墙拦截了 pip 请求，或者你尚未安装/配置好 Python 环境。\n退出码：" + exitCode, "配置失败", JOptionPane.ERROR_MESSAGE)
                    );
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "执行命令异常：" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE)
                );
            }
        }).start();
    }

    /**
     * 从 resources/env/ 提取内置的 Python 安装包并自动运行
     */
    private void installBundledPythonEnv() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "这将会把系统内置的 Python 环境安装包提取到桌面并准备运行。\n是否继续？",
                "提取确认", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        File desktopDir = FileSystemView.getFileSystemView().getHomeDirectory();
        File destFile = new File(desktopDir, "python-installer.exe");

        // 依然放在新线程里，提取大文件时不会让UI卡顿
        new Thread(() -> {
            try (InputStream is = getClass().getResourceAsStream(PYTHON_ENV_PATH)) {
                if (is == null) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(PyHelperPanel.this,
                                    "找不到内置安装包！\n请确保打包前已将 python-installer.exe 放入 resources/env/ 目录下。",
                                    "资源缺失", JOptionPane.ERROR_MESSAGE)
                    );
                    return;
                }

                // 覆盖拷贝流到桌面
                Files.copy(is, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                SwingUtilities.invokeLater(() -> {
                    int runConfirm = JOptionPane.showConfirmDialog(PyHelperPanel.this,
                            "Python 安装包已成功提取到桌面！\n\n【重要提示】：安装时请务必勾选界面底部的 \n'Add python.exe to PATH'\n'Use admin privileges when instaling py.exe'\n\n是否立即启动安装程序？",
                            "提取完成", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

                    if (runConfirm == JOptionPane.YES_OPTION) {
                        try {
                            // 自动帮用户打开桌面上的安装包
                            Desktop.getDesktop().open(destFile);
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(PyHelperPanel.this, "请手动在桌面双击 python-installer.exe 进行安装。", "提示", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(PyHelperPanel.this, "提取发生错误：\n" + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE)
                );
            }
        }).start();
    }
}