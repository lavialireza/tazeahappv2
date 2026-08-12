# -*- coding: utf-8 -*-
"""
اسکریپت تبدیل فایل ورد (docx) به فایل JSON مورد نیاز اپلیکیشن تعزیه.

نحوه‌ی نوشتن فایل ورد شما (قرارداد ساده با استایل‌های Heading):
    Heading 1  -> عنوان زمینه        (مثلاً: اصفهان، تهران، میرعزا)
    Heading 2  -> عنوان تعزیه        (مثلاً: عاشورا، بازار شام)
    Heading 3  -> عنوان نقش          (مثلاً: شمر، امام، یزید)
    Heading 4  -> عنوان بخش          (مثلاً: ورود، ساقی‌نامه، شهادت)
    پاراگراف‌های معمولی زیر Heading 4  -> متن اشعار همان بخش

نصب پیش‌نیاز:
    pip install python-docx --break-system-packages

اجرا:
    python docx_to_json.py input.docx output.json
"""

import sys
import json
from docx import Document


def convert(docx_path: str) -> list:
    doc = Document(docx_path)

    fields = []
    current_field = None
    current_tazieh = None
    current_role = None
    current_section = None

    for para in doc.paragraphs:
        style = para.style.name if para.style else ""
        text = para.text.strip()
        if not text:
            continue

        if style == "Heading 1":
            current_field = {"title": text, "taziehs": []}
            fields.append(current_field)
            current_tazieh = current_role = current_section = None

        elif style == "Heading 2":
            if current_field is None:
                current_field = {"title": "بدون زمینه", "taziehs": []}
                fields.append(current_field)
            current_tazieh = {"title": text, "roles": []}
            current_field["taziehs"].append(current_tazieh)
            current_role = current_section = None

        elif style == "Heading 3":
            if current_tazieh is None:
                current_tazieh = {"title": "بدون تعزیه", "roles": []}
                current_field["taziehs"].append(current_tazieh)
            current_role = {"title": text, "sections": []}
            current_tazieh["roles"].append(current_role)
            current_section = None

        elif style == "Heading 4":
            if current_role is None:
                current_role = {"title": "بدون نقش", "sections": []}
                current_tazieh["roles"].append(current_role)
            current_section = {"title": text, "content": ""}
            current_role["sections"].append(current_section)

        else:
            # پاراگراف متن معمولی (شعر)
            if current_section is not None:
                if current_section["content"]:
                    current_section["content"] += "\n" + text
                else:
                    current_section["content"] = text

    return fields


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("استفاده: python docx_to_json.py input.docx output.json")
        sys.exit(1)

    input_path = sys.argv[1]
    output_path = sys.argv[2]

    result = convert(input_path)

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"تمام شد. فایل خروجی: {output_path}")
