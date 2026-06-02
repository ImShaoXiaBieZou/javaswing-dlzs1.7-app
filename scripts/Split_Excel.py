import os
import re
import pandas as pd
import tkinter as tk
from tkinter import filedialog

def clean_filename(filename):
    """
    清洗文件名，替换Windows操作系统中不允许出现的非法字符
    """
    if pd.isna(filename) or str(filename).strip() == "":
        return "未指定"
    # 替换 Windows 下文件名禁止的字符: \ / : * ? " < > |
    safe_name = re.sub(r'[\\/:*?"<>|]', '_', str(filename))
    # 去除首尾空格和换行符
    return safe_name.strip()

def main():
    # === 核心防遮挡机制 ===
    # 初始化 Tkinter 隐藏主窗口并强制置顶，彻底解决被 Java Swing 压在下方导致“假死”的问题
    root = tk.Tk()
    root.withdraw()
    root.attributes('-topmost', True)

    print("=====================================")
    print("        总表条件拆分工具启动         ")
    print("=====================================")
    print("正在等待选择总表文件...")
    
    # 弹出文件选择框，限定只能选择 Excel 文件
    file_path = filedialog.askopenfilename(
        title="请选择需要拆分的总表 Excel 文件",
        filetypes=[("Excel 电子表格", "*.xlsx *.xls")]
    )
    
    if not file_path:
        print("已取消选择，程序安全退出。")
        return

    print(f"[成功] 已加载总表: {file_path}")
    
    # === 读取总表 ===
    try:
        print("正在解析表格数据，请稍候...")
        # read_excel 默认读取第一个 Sheet
        df = pd.read_excel(file_path)
    except Exception as e:
        print(f"\n❌ 读取文件失败: {e}")
        print("提示：请检查该 Excel 文件是否加密、损坏，或者正被其他软件独占打开。")
        return

    # === 展示列名供用户选择 ===
    print("\n-------------------------------------")
    print("当前表格包含以下可用列名（表头）：")
    print("-------------------------------------")
    # 每 4 个列名换一行展示，方便少侠在控制台对齐查看
    columns_list = list(df.columns.astype(str))
    for i in range(0, len(columns_list), 4):
        print(" | ".join(columns_list[i:i+4]))
    print("-------------------------------------")
    
    # === 用户输入交互 ===
    split_col = input("\n请输入你要根据哪一列进行拆分（请完全匹配列名，区分大小写）:\n> ").strip()
    
    if split_col not in df.columns:
        print(f"\n❌ 错误：表格中找不到列名 '{split_col}'！")
        print("提示：请检查是否有多余的空格、错别字或英文字母大小写不匹配。")
        return

    # === 创建专属输出文件夹 ===
    base_dir = os.path.dirname(file_path)
    base_name = os.path.splitext(os.path.basename(file_path))[0]
    output_dir = os.path.join(base_dir, f"{base_name}_按【{split_col}】拆分结果")
    
    try:
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)
    except Exception as e:
        print(f"\n❌ 创建输出文件夹失败: {e}")
        return

    # === 核心拆分算法 ===
    print(f"\n正在按【{split_col}】进行数据分组拆分...")
    
    # 使用 groupby 效率极高地处理几万行数据
    grouped = df.groupby(split_col, dropna=False)
    success_count = 0
    
    for name, group in grouped:
        # 调用清洗函数，防止因为名字里带特殊符号（如路径斜杠）导致保存崩溃
        safe_name = clean_filename(name)
        output_file_path = os.path.join(output_dir, f"{safe_name}.xlsx")
        
        try:
            # 过滤掉拆分时可能自动生成的索引或无用列，保持纯净输出
            group.to_excel(output_file_path, index=False)
            print(f"[生成] -> {safe_name}.xlsx (共 {len(group)} 行)")
            success_count += 1
        except Exception as e:
            print(f"[失败] 无法生成文件 {safe_name}.xlsx，原因: {e}")

    # === 结果收尾 ===
    print("\n=====================================")
    print(f"✅ 拆分任务顺利完成！")
    print(f"✅ 成功拆分出 {success_count} 个独立 Excel 文件。")
    print(f"✅ 所有文件均已存放在：\n   {output_dir}")
    print("=====================================")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\n❌ 发生严重未知异常: {e}")
    finally:
        # 强制挂起控制台，确保少侠看清所有的拆分日志
        input("\n按回车键退出本窗口...")