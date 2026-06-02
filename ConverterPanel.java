package me.shaoxia;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 一键转换助手 ConverterPanel (已通过 Apache PDFBox 彻底修复图片损坏问题)
 */
public class ConverterPanel extends BackgroundPanel {

    private JTextArea logArea;

    // ======= 内置任务队列 =======
    private ExecutorService pool = Executors.newFixedThreadPool(2);

    public ConverterPanel(MainLauncher parent) {
        super("texture2.png");
        setLayout(new BorderLayout());

        initTopBar(parent);
        initLogArea();
        setupDropTarget();
        resetLog();
    }

    // ================= UI =================

    private void initTopBar(MainLauncher parent) {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(15, 25, 10, 25));

        JButton backBtn = new JButton(" << 返回主菜单 ");
        backBtn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        backBtn.setFocusPainted(false);

        backBtn.addActionListener(e -> parent.showMenu());

        topBar.add(backBtn, BorderLayout.WEST);
        add(topBar, BorderLayout.NORTH);
    }

    private void initLogArea() {
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setOpaque(false);
        logArea.setForeground(new Color(255, 215, 0));
        logArea.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 15));
        logArea.setLineWrap(true);
        logArea.setMargin(new Insets(30, 40, 30, 40));

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0));

        add(scroll, BorderLayout.CENTER);
    }

    // ================= 拖拽 =================

    private void setupDropTarget() {
        new DropTarget(logArea, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);

                    startBatch(files);

                } catch (Exception e) {
                    log("拖拽失败: " + e.getMessage());
                }
            }
        });
    }

    // ================= 批量入口 =================

    private void startBatch(List<File> files) {
        log("\n引擎启动：任务队列加载中...");

        for (File f : files) {
            pool.submit(() -> processFileWithRetry(f));
        }
    }

    // ================= 带重试 =================

    private void processFileWithRetry(File file) {
        int maxRetry = 2;

        for (int i = 0; i <= maxRetry; i++) {
            try {
                processSingleFile(file);
                return;
            } catch (Exception e) {
                if (i == maxRetry) {
                    log(" 最终失败: " + file.getName());
                } else {
                    log(" 重试中 (" + (i + 1) + "): " + file.getName());
                }
            }
        }
    }

    // ================= 核心转换 =================

    private void processSingleFile(File file) throws Exception {
        String name = file.getName().toLowerCase();

        boolean isPdf = name.endsWith(".pdf");
        boolean isWord = name.endsWith(".docx") || name.endsWith(".doc");
        boolean isImage = name.endsWith(".png") || name.endsWith(".jpg") ||
                name.endsWith(".jpeg") || name.endsWith(".bmp") || name.endsWith(".gif");

        if (!isPdf && !isWord && !isImage) {
            log(" 跳过不支持的格式: " + file.getName());
            return;
        }

        log(" 处理中: " + file.getName());

        // 1. 使用 PDFBox 引擎进行图片转 PDF
        if (isImage) {
            File output = buildOutput(file, ".pdf");
            convertImageToPdfWithBox(file, output);
            log("    完成 → " + output.getName());
            return;
        }

        // 2. Office / WPS 互转逻辑
        File output = buildOutput(file, isPdf ? ".docx" : ".pdf");
        File vbs = createVbsScript(file.getAbsolutePath(), output.getAbsolutePath(), isPdf);

        Process process = Runtime.getRuntime()
                .exec("wscript.exe \"" + vbs.getAbsolutePath() + "\"");

        boolean finished = process.waitFor(120, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("超时");
        }

        if (!output.exists()) {
            throw new RuntimeException("转换失败");
        }

        log("    完成 → " + output.getName());
        vbs.delete();
    }

    // ================= PDFBox 图像集成转换引擎 =================

    private void convertImageToPdfWithBox(File imgFile, File pdfFile) throws Exception {
        // 使用 PDDocument 建立标准 PDF 文件对象
        try (PDDocument document = new PDDocument()) {
            // 创建标准的 A4 页面 (595 x 842 点)
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            // 读取图片并转化为 PDFBox 兼容的图像对象
            BufferedImage bimg = ImageIO.read(imgFile);
            if (bimg == null) {
                throw new IOException("无法读取图片数据");
            }
            PDImageXObject pdfImage = LosslessFactory.createFromImage(document, bimg);

            float imgW = pdfImage.getWidth();
            float imgH = pdfImage.getHeight();

            // A4 纸张的实际长宽
            float pageW = PDRectangle.A4.getWidth();
            float pageH = PDRectangle.A4.getHeight();

            // 计算安全缩放区域（四周预留 30 磅的空白边距，防止穿边）
            float maxW = pageW - 60;
            float maxH = pageH - 60;

            float scale = Math.min(maxW / imgW, maxH / imgH);
            if (scale > 1.0f) {
                scale = 1.0f; // 如果原图比 A4 小，保持 1:1 原图，不进行强行拉伸模糊
            }

            float finalW = imgW * scale;
            float finalH = imgH * scale;

            // 完美居中坐标算法
            float x = (pageW - finalW) / 2;
            float y = (pageH - finalH) / 2;

            // 打开页面内容流，将图片安全写入
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(pdfImage, x, y, finalW, finalH);
            }

            // 保存为标准的 PDF 文件
            document.save(pdfFile);
        }
    }

    // ================= 输出文件策略 =================

    private File buildOutput(File input, String targetExt) {
        String base = input.getAbsolutePath().replaceAll("\\..+$", "");
        File out = new File(base + targetExt);

        int i = 1;
        while (out.exists()) {
            out = new File(base + "_" + i + targetExt);
            i++;
        }

        return out;
    }

    // ================= VBS 引擎 =================

    private File createVbsScript(String input, String output, boolean pdfToDocx) throws IOException {
        File vbs = File.createTempFile("engine_", ".vbs");
        int format = pdfToDocx ? 16 : 17;

        String script =
                "On Error Resume Next\n" +
                        "Set app = CreateObject(\"WPS.Application\")\n" +
                        "If Err.Number <> 0 Then\n" +
                        "  Err.Clear\n" +
                        "  Set app = CreateObject(\"Word.Application\")\n" +
                        "End If\n" +
                        "\n" +
                        "If app Is Nothing Then WScript.Quit\n" +
                        "\n" +
                        "app.Visible = False\n" +
                        "app.DisplayAlerts = 0\n" +
                        "\n" +
                        "Set doc = app.Documents.Open(\"" + escape(input) + "\", False, True)\n" +
                        "\n" +
                        "If Err.Number = 0 Then\n" +
                        "  doc.SaveAs2 \"" + escape(output) + "\", " + format + "\n" +
                        "  doc.Close False\n" +
                        "End If\n" +
                        "\n" +
                        "app.Quit\n" +
                        "Set doc = Nothing\n" +
                        "Set app = Nothing\n";

        try (OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(vbs), "GBK")) {
            writer.write(script);
        }

        return vbs;
    }

    private String escape(String path) {
        return path.replace("\\", "\\\\");
    }

    // ================= 日志 =================

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void resetLog() {
        logArea.setForeground(new Color(30, 144, 255));
        logArea.setText(
                        " 《Wheat Fields Under Thunderclouds》\n"
        );
    }
}