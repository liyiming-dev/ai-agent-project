import sys
import os

try:
    from docx import Document
    from docx.shared import Pt, Inches
    from docx.enum.text import WD_PARAGRAPH_ALIGNMENT
    from docx.oxml.ns import qn
except ImportError:
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "python-docx"])
    from docx import Document
    from docx.shared import Pt, Inches
    from docx.enum.text import WD_PARAGRAPH_ALIGNMENT
    from docx.oxml.ns import qn

def get_desktop_path():
    import ctypes
    from ctypes import wintypes
    CSIDL_DESKTOP = 0x0000
    path_buf = ctypes.create_unicode_buffer(wintypes.MAX_PATH)
    ctypes.windll.shell32.SHGetFolderPathW(0, CSIDL_DESKTOP, 0, 0, path_buf)
    return path_buf.value

def create_doc():
    doc = Document()
    doc.styles['Normal'].font.name = u'宋体'
    doc.styles['Normal']._element.rPr.rFonts.set(qn('w:eastAsia'), u'宋体')
    doc.styles['Normal'].font.size = Pt(11)
    
    heading = doc.add_heading('理论课作业4-软工四班', 0)
    heading.alignment = WD_PARAGRAPH_ALIGNMENT.CENTER
    
    # Q1
    doc.add_heading('1. GOMS模型分析（以微信“拍一拍”功能为例）', level=1)
    txt1 = (
        "分析对象：微信聊天中的“拍一拍”（双击头像触发）功能。\n\n"
        "GOMS 分析：\n"
        "- Goal（目标）：在聊天界面引起对方注意，或进行轻松打招呼。\n"
        "- Operator（操作）：\n"
        "  1. 移动手指（M）到目标用户的头像上。\n"
        "  2. 点击头像（K）。\n"
        "  3. 快速再次点击头像（K）。\n"
        "- Method（方法）：运用连续双击动作组合，避免了打字或寻找专门的按钮。\n"
        "- Selection Rules（选择规则）：当用户希望不用文字且快速地“滴”一下对方以示提醒或打招呼时，选择此方法。\n\n"
        "优缺点阐述：\n"
        "优点：将原先需要（点击输入框 -> 打字 -> 点击发送）且耗时较长、认知负荷较大的动作，压缩成了不到1秒钟内完成的纯物理双击操作。该设计极大地提高了操作效率，符合移动端的自然交互直觉。\n"
        "缺点：第一，缺乏明显的可用性提示（Affordance）。由于界面上没有任何“双击可以拍一拍”的视觉提示，新用户很难自动发现该功能，存在一定的学习成本。第二，存在较高的误触率。用户在试图单点头像以查看详细信息时，如果手指颤动或点击稍快，就容易触发“拍一拍”，引发社交尴尬（Social Awkwardness）。"
    )
    doc.add_paragraph(txt1)
    
    # Q2
    doc.add_heading('2. 通读WCAG指南文件简述', level=1)
    txt2 = (
        "我已经通读了W3C发布的WCAG（Web Content Accessibility Guidelines / Web内容无障碍指南）文件。\n\n"
        "阅读体会与核心提炼：\n"
        "WCAG的核心目的是让残障人士（如弱视、色盲、听障、运动障碍或认知障碍者）甚至所有普通用户都能无障碍地获取网络上的数字信息和操作界面。这份指南围绕着著名的“POUR”四大核心原则展开：\n"
        "1. 可感知性 (Perceivable)：信息和用户界面组件必须以用户能够察觉的方式呈现（例如图片必须有替代文本Alt、视力受损者可通过读屏软件获取信息）。\n"
        "2. 可操作性 (Operable)：用户界面组件和导航必须可以操作（例如确保绝大多数的交互功能能够通过单纯的键盘完成，不要强求精准的鼠标点击，避免诱发癫痫的闪烁设计）。\n"
        "3. 可理解性 (Understandable)：界面设计和内容的表达必须是可以被理解的（例如提供一致且可预测的导航路径、出现输入错误时提供明确的文字修改建议）。\n"
        "4. 鲁棒性 / 健壮性 (Robust)：内容必须足够健壮，以至于能够被广泛的用户代理（包括各种辅助技术和未来的设备）可靠地解读解析。"
    )
    doc.add_paragraph(txt2)

    # Q3
    doc.add_heading('3. CHI论文阅读与思考', level=1)
    txt3 = (
        "选择文章：《DirectGPT: A Direct Manipulation Interface to Interact with Large Language Models》（入选于CHI国际人机交互顶级会议）\n\n"
        "【文章概要】\n"
        "目前人类与大语言模型（LLM）的绝大多数交互都局限在“对话框式的文字指令”（Prompt机制，例如早期的ChatGPT界面）。然而，对于需要空间感知、非线性思维或多次局部迭代打磨的复杂工作（如文本排版或局部修改）而言，反复修改提示词显得低效且脆弱。这篇论文提出了一种结合大型AI模型与“直接操纵（Direct Manipulation）”的全新交互范式——DirectGPT。该系统打破了传统的文本输入框对话流，允许用户直接对AI生成的文本模块进行高亮、拖拽、点选标记、合并等图形化GUI动作，而系统会在后台自动将这些符合人类直觉的物理交互，翻译成LLM能精准执行的微调指令。\n\n"
        "【我的思考】\n"
        "这篇文章精准切中了目前生成式AI界面工具的痛点：对话式界面的“操作不可视”与“精准控制力弱”。传统的对话流虽然门槛极低，但在修改前面已生成内容的局部细节时，想要准确用文字描述“修改第3段第2句话的语气”往往笨拙冗长。DirectGPT重新将“所见即所得”的经典HCI（人机交互）理念带回到了AI工具设计中。这启示我在软件设计与需求工程中：不仅要关注底层AI模型能力或业务逻辑是否强大，还要注重界面交互形式的变革。有时，一个好的直接操纵界面范式设计（如从CLI命令行到GUI图形界面），能为产品带来指数级的用户体验飞跃。"
    )
    doc.add_paragraph(txt3)
    
    desktop = get_desktop_path()
    save_path = os.path.join(desktop, '理论课作业4-软工四班.docx')
    doc.save(save_path)
    print(save_path)

if __name__ == '__main__':
    create_doc()
