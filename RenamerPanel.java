package me.shaoxia;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.Vector;

/**协同改名助手 RenamerPanel*/

class RenamerPanel extends BackgroundPanel
{
    private JComboBox<String> biaoCombo, baoCombo, typeCombo, companyCombo;
    private JTextField newCompanyInput;
    private JLabel previewLabel;
    private DefaultComboBoxModel<String> companyModel;

    public RenamerPanel(MainLauncher parent) {
        super("texture2.png");
        setLayout(new GridLayout(1, 2));
        setOpaque(false);

        // 左侧控制区
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBackground(new Color(45, 48, 50, 200));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 5, 10, 5);
        gbc.weightx = 1.0;

        JButton backBtn = new JButton(" << 返回主菜单 ");
        backBtn.setFont(MainLauncher.BOLD_FONT);
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        leftPanel.add(backBtn, gbc);
        backBtn.addActionListener(e -> parent.showMenu());

        biaoCombo = new JComboBox<>(generateList("标", 20));
        addControlRow(leftPanel, gbc, 1, "1. 选择标段:", biaoCombo);

        baoCombo = new JComboBox<>(generateList("包", 20));
        addControlRow(leftPanel, gbc, 2, "2. 选择包号:", baoCombo);

        String[] types = {"价格文件", "商务文件", "技术文件", "二轮报价", "三轮报价", "四轮报价", "五轮报价", "六轮报价", "首轮明细报价" ,"二轮明细报价", "三轮明细报价", "四轮明细报价", "五轮明细报价", "六轮明细报价"};
        typeCombo = new JComboBox<>(types);
        addControlRow(leftPanel, gbc, 3, "3. 文件类型:", typeCombo);

        companyModel = new DefaultComboBoxModel<>(new String[]{"请先添加供应商"});
        companyCombo = new JComboBox<>(companyModel);
        addControlRow(leftPanel, gbc, 4, "4. 选择公司:", companyCombo);

        newCompanyInput = new JTextField();
        addControlRow(leftPanel, gbc, 5, "5. 管理供应商:", newCompanyInput);

        JPanel btnP = new JPanel(new GridLayout(1, 2, 8, 0));
        btnP.setOpaque(false);
        JButton save = new JButton("保存公司"), delete = new JButton("删除公司"),reset = new JButton("重置名单");
        btnP.add(save);
        btnP.add(delete);
        btnP.add(reset);
        gbc.gridy = 6;
        gbc.gridx = 1;
        gbc.gridwidth = 1;
        leftPanel.add(btnP, gbc);

        // 事件处理
        save.addActionListener(e -> {
            String n = newCompanyInput.getText().trim();
            if (!n.isEmpty()) {
                if (companyModel.getSize() > 0 && companyModel.getElementAt(0).contains("请先"))
                    companyModel.removeElementAt(0);
                companyModel.addElement(n);
                companyCombo.setSelectedItem(n);
                newCompanyInput.setText("");
                updatePreview();
            }
        });
        delete.addActionListener(e -> {
            String selected = (String) companyCombo.getSelectedItem();

            // 如果选中的不是提示语，且不为空
            if (selected != null && !selected.contains("请先添加")) {
                companyModel.removeElement(selected); // 从模型里删掉它

                // 如果删完名单空了，把提示语请回来
                if (companyModel.getSize() == 0) {
                    companyModel.addElement("请先添加供应商");
                }
                updatePreview(); // 更新右侧预览
            } else {
                JOptionPane.showMessageDialog(this, "请先选择一个具体的供应商！");
            }
        });
        /*保存为enter键位*/
        newCompanyInput.addActionListener(e -> save.doClick());
        //
        reset.addActionListener(e -> {
            companyModel.removeAllElements();
            companyModel.addElement("请先添加供应商");
            updatePreview();
        });

        // 右侧预览与拖入
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        GridBagConstraints rGbc = new GridBagConstraints();
        rGbc.insets = new Insets(20, 20, 20, 20);
        rGbc.fill = GridBagConstraints.BOTH;

        previewLabel = new JLabel("预览：等待输入...", JLabel.CENTER);
        previewLabel.setFont(MainLauncher.BOLD_FONT);
        previewLabel.setForeground(Color.WHITE);
        rGbc.gridy = 0;
        rGbc.weighty = 0.2;
        rGbc.weightx = 1.0;
        rightPanel.add(previewLabel, rGbc);

        JPanel dropArea = new JPanel(new BorderLayout());
        dropArea.setOpaque(false);
        dropArea.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.WHITE, 2, true), " 拖入文件重命名 ", TitledBorder.CENTER, TitledBorder.TOP, MainLauncher.BOLD_FONT, Color.WHITE));
        JLabel dl = new JLabel("<html><center>准备就绪<br>拖入文件自动处理</center></html>", JLabel.CENTER);
        dl.setForeground(Color.WHITE);
        dropArea.add(dl);
        setDropTarget(dropArea);
        rGbc.gridy = 1;
        rGbc.weighty = 0.8;
        rightPanel.add(dropArea, rGbc);

        ActionListener listener = e -> updatePreview();
        biaoCombo.addActionListener(listener);
        baoCombo.addActionListener(listener);
        typeCombo.addActionListener(listener);
        companyCombo.addActionListener(listener);

        add(leftPanel);
        add(rightPanel);
    }

    private void addControlRow(JPanel p, GridBagConstraints g, int r, String t, Component c) {
        g.gridy = r;
        g.gridx = 0;
        g.weightx = 0.3;
        g.gridwidth = 1;
        JLabel l = new JLabel(t);
        l.setForeground(Color.WHITE);
        p.add(l, g);
        g.gridx = 1;
        g.weightx = 0.7;
        p.add(c, g);
    }

    private void updatePreview() {
        Object selected = companyCombo.getSelectedItem();
        String c = (selected != null) ? selected.toString() : "未选公司";
        if (c.contains("请先")) c = "未选公司";
        previewLabel.setText("预览：" + biaoCombo.getSelectedItem() + baoCombo.getSelectedItem() + "_" + c + "_" + typeCombo.getSelectedItem());
    }

    private void setDropTarget(JPanel p) {
        new DropTarget(p, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                try {
                    String comp = companyCombo.getSelectedItem().toString();
                    if (comp.contains("请先")) {
                        dtde.rejectDrop();
                        JOptionPane.showMessageDialog(null, "请先输入并保存供应商名称！", "操作拦截", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        String ext = file.getName().contains(".") ? file.getName().substring(file.getName().lastIndexOf(".")) : "";
                        String baseName = biaoCombo.getSelectedItem() + (String) baoCombo.getSelectedItem() + "_" + comp + "_" + typeCombo.getSelectedItem();
                        File newFile = new File(file.getParent(), baseName + ext);
                        if (newFile.exists()) {
                            int choice = JOptionPane.showConfirmDialog(null, "文件夹内已存在文件：\n" + newFile.getName() + "\n是否自动更名保存？", "检测到重名", JOptionPane.YES_NO_OPTION);
                            if (choice == JOptionPane.YES_OPTION) {
                                int count = 1;
                                while (newFile.exists()) {
                                    newFile = new File(file.getParent(), baseName + "_复件" + count + ext);
                                    count++;
                                }
                            } else continue;
                        }
                        file.renameTo(newFile);
                    }
                    JOptionPane.showMessageDialog(null, "处理成功！");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private Vector<String> generateList(String p, int c) {
        Vector<String> v = new Vector<>();
        for (int i = 1; i <= c; i++) v.add(p + i);
        return v;
    }
}
