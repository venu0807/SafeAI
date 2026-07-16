"""Fix script: Add Figure 4.8 Web Dashboard placeholder to the report generator."""

import re

with open("generate_project_report.py", "r", encoding="utf-8") as f:
    content = f.read()

# Find the pattern and insert the diagram placeholder
old = (
    '"app. The settings page allows configuration of web notification preferences."\n'
    "    )\n"
    "    pdf.body(\n"
    '        "The dashboard UI follows the same dark-themed'
)

new = (
    '"app. The settings page allows configuration of web notification preferences."\n'
    "    )\n"
    "    pdf.ln(2)\n"
    "    pdf.diagram_placeholder(\n"
    '        "Figure 4.8: Web Dashboard Interface",\n'
    '        "Dark-themed monitoring dashboard showing live alert feed with threat type,\\nconfidence scores, timestamps, GPS locations, and real-time metrics cards."\n'
    "    )\n"
    "    pdf.body(\n"
    '        "The dashboard UI follows the same dark-themed'
)

if old in content:
    content = content.replace(old, new)
    with open("generate_project_report.py", "w", encoding="utf-8") as f:
        f.write(content)
    print("SUCCESS: Figure 4.8 added to the report generator.")
else:
    print("ERROR: Pattern not found. Checking for alternative patterns...")
    # Try Windows CRLF line endings
    old_crlf = (
        '"app. The settings page allows configuration of web notification preferences."\r\n'
        "    )\r\n"
        "    pdf.body(\r\n"
        '        "The dashboard UI follows the same dark-themed'
    )
    new_crlf = (
        '"app. The settings page allows configuration of web notification preferences."\r\n'
        "    )\r\n"
        "    pdf.ln(2)\r\n"
        "    pdf.diagram_placeholder(\r\n"
        '        "Figure 4.8: Web Dashboard Interface",\r\n'
        '        "Dark-themed monitoring dashboard showing live alert feed with threat type,\\r\nconfidence scores, timestamps, GPS locations, and real-time metrics cards."\r\n'
        "    )\r\n"
        "    pdf.body(\r\n"
        '        "The dashboard UI follows the same dark-themed'
    )
    if old_crlf in content:
        content = content.replace(old_crlf, new_crlf)
        with open("generate_project_report.py", "w", encoding="utf-8") as f:
            f.write(content)
        print("SUCCESS: Figure 4.8 added (CRLF line endings).")
    else:
        print("Pattern still not found. Showing context around 'web notification preferences':")
        idx = content.find("web notification preferences")
        if idx >= 0:
            print(repr(content[idx:idx+300]))
