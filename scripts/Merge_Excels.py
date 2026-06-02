import os
import pandas as pd
import tkinter as tk
from tkinter import filedialog

def main():
    # === 核心防遮挡机制 ===
    # 初始化 Tkinter 隐藏主窗口，并强制置顶，彻底解决被 Java Swing 界面压在下方导致“假死”的问题
    root = tk.Tk()
    root.withdraw()
    root.attributes('-topmost', True)

    print("=====================================")
    print("        多表一键合并工具启动         ")
    print("=====================================")
    print("正在等待选择文件夹...")
    
    # 弹出选择文件夹对话框
    folder_path = filedialog.askdirectory(title="请选择存放待合并Excel的文件夹")
    
    if not folder_path:
        print("已取消选择，程序安全退出。")
        return

    print(f"已选择文件夹: {folder_path}\n")
    
    all_data = []
    file_count = 0

    # === 遍历与读取机制 ===
    for file in os.listdir(folder_path):
        # 严格过滤：只处理 .xlsx 或 .xls，且跳过 Office 自动生成的临时隐藏文件(以 ~$ 开头)
        if (file.endswith('.xlsx') or file.endswith('.xls')) and not file.startswith('~$'):
            # 防无限套娃：跳过工具自己生成的汇总表
            if file == "汇总表_合并结果.xlsx":
                continue
                
            file_path = os.path.join(folder_path, file)
            try:
                # 读取数据
                df = pd.read_excel(file_path)
                
                # 核心细节：在表格的第 1 列（索引0）强行插入一列“数据来源”，方便合并后溯源某行数据是谁家的
                df.insert(0, '数据来源_源文件名', file) 
                
                all_data.append(df)
                file_count += 1
                print(f"[成功] 读取: {file} (共 {len(df)} 行)")
            except Exception as e:
                # 容错处理：比如遇到加了密码的 Excel，直接跳过并报错，不中断后续操作
                print(f"[跳过] 读取 {file} 时出错: {e}")

    # === 数据合并与输出机制 ===
    if file_count > 0:
        print("\n正在努力合并数据，请稍候...")
        # 纵向拼接所有表，ignore_index=True 重新洗牌行号
        merged_df = pd.concat(all_data, ignore_index=True)
        output_path = os.path.join(folder_path, "汇总表_合并结果.xlsx")
        
        try:
            merged_df.to_excel(output_path, index=False)
            print(f"\n✅ 合并圆满完成！")
            print(f"✅ 共合并了 {file_count} 个文件，总计 {len(merged_df)} 行数据。")
            print(f"✅ 汇总表已安全保存至: {output_path}")
        except Exception as e:
            print(f"\n❌ 保存文件失败: {e}")
            print("提示：请检查目标文件夹是否有写入权限，或者 '汇总表_合并结果.xlsx' 是否正被 Excel 软件打开占用。")
    else:
        print("\n❌ 任务结束：该文件夹下没有找到合法的 Excel 文件。")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\n❌ 发生严重未捕获异常: {e}")
    finally:
        # 强制暂停，确保黑框不会一闪而过，少侠能看清所有执行日志
        input("\n按回车键退出本窗口...")