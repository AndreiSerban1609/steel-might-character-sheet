"""Convert a .docx to plain text with heading markers. Usage: python docx2txt.py <in.docx> <out.txt>"""
import re
import sys
import zipfile

src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as z:
    xml = z.read("word/document.xml").decode("utf-8")

paragraphs = re.findall(r"<w:p[ >].*?</w:p>", xml, re.S)
lines = []
for p in paragraphs:
    style = re.search(r'<w:pStyle w:val="([^"]+)"', p)
    text = "".join(re.findall(r"<w:t[^>]*>(.*?)</w:t>", p, re.S))
    text = (
        text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"').replace("&apos;", "'")
    )
    if not text.strip():
        lines.append("")
        continue
    if style and style.group(1).lower().startswith("heading"):
        level = re.sub(r"\D", "", style.group(1)) or "1"
        text = "#" * int(level) + " " + text
    lines.append(text)

with open(dst, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
print(f"wrote {dst}: {len(lines)} paragraphs")
