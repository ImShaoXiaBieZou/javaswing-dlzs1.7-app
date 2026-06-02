import os
import openpyxl
import pandas as pd
import tkinter as tk
from tkinter import filedialog

def main():
    # === 核心防遮挡机制 ===
    root = tk.Tk()
    root.withdraw()
    root.attributes('-topmost', True)

    print("=====================================")
    print("      跨文件特定单元格提取工具启动   ")
    print("=====================================")
    print("正在等待选择文件夹...")
    
    # 弹出选择文件夹对话框
    folder_path = filedialog.askdirectory(title="请选择存放待提取Excel的文件夹")
    
    if not folder_path:
        print("已取消选择，程序安全退出。")
        return

    print(f"[成功] 已选择文件夹: {folder_path}\n")
    
    # === 交互：获取目标单元格 ===
    print("-------------------------------------")
    print("请输入你需要提取的单元格坐标。")
    print("示例: B2, D5, E10 (多个坐标用逗号隔开)")
    print("-------------------------------------")
    cells_input = input("> ").strip()
    
    if not cells_input:
        print("❌ 未输入任何单元格坐标，程序退出。")
        return

    # 清洗用户输入：转大写、去空格、按逗号拆分
    target_cells = [cell.strip().upper() for cell in cells_input.split(',') if cell.strip()]
    print(f"\n准备提取的单元格列表: {target_cells}\n")

    result_data = []
    success_count = 0

    # === 遍历与读取机制 ===
    for file in os.listdir(folder_path):
        # 严格过滤：只处理 .xlsx，跳过临时文件(~$开头)和之前生成的汇总表
        if file.endswith('.xlsx') and not file.startswith('~$'):
            if "专项提取汇总台账" in file:
                continue
                
            file_path = os.path.join(folder_path, file)
            print(f"正在扫描: {file} ...", end=" ")
            
            try:
                # 核心细节：data_only=True 确保读取的是公式计算后的真实结果，而不是公式代码
                # read_only=True 极大提升读取几百个文件的速度
                wb = openpyxl.load_workbook(file_path, data_only=True, read_only=True)
                ws = wb.active # 默认读取第一个 Sheet
                
                # 初始化这一行的数据，第一列固定为文件名，方便溯源
                row_data = {"数据来源_源文件名": file}
                
                # 遍历目标单元格提取数据
                for cell in target_cells:
                    try:
                        val = ws[cell].value
                        row_data[cell] = val
                    except Exception:
                        # 容错：如果用户输入的坐标非法（比如乱码），填入特定提示
                        row_data[cell] = "[坐标无效]"
                
                result_data.append(row_data)
                wb.close()
                success_count += 1
                print("成功!")
            except Exception as e:
                print(f"失败 (可能文件被加密或损坏: {e})")

    # === 数据汇总与输出机制 ===
    if success_count > 0:
        print("\n正在生成汇总台账...")
        # 转换为 DataFrame 并强制指定列的顺序（文件名在最前，后面跟单元格顺序）
        df = pd.DataFrame(result_data)
        cols = ['数据来源_源文件名'] + target_cells
        df = df[cols]
        
        output_path = os.path.join(folder_path, "专项提取汇总台账.xlsx")
        
        try:
            df.to_excel(output_path, index=False)
            print(f"\n=====================================")
            print(f"✅ 提取圆满完成！")
            print(f"✅ 共从 {success_count} 个文件中成功提取数据。")
            print(f"✅ 汇总台账已保存至:\n   {output_path}")
            print(f"=====================================")
        except Exception as e:
            print(f"\n❌ 保存文件失败: {e}")
            print("提示：请检查目标文件夹是否有写入权限，或者汇总表是否正被 Excel 软件占用。")
    else:
        print("\n❌ 任务结束：没有提取到任何有效数据。请检查该文件夹下是否有格式正确的 .xlsx 文件。")

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"\n❌ 发生严重未知异常: {e}")
    finally:
        input("\n按回车键退出本窗口...")