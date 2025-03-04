import sys
from pathlib import Path
import re


def make_links_absolute(text, url):
    start = 0
    for match in re.finditer("\\[[a-zA-Z0-9\\ \\-]+]\\(([a-z0-9\\/\\.]+)\\)", text):
        if match:
            match_start, match_end = match.span()
            if not match.group(1).startswith("http"):
                substring = match.group(0)
                substring = substring.replace(
                    match.group(1), url + match.group(1)
                ).replace(".md", "")
                print(text[start:match_start], end="")
                print(substring, end="")
                # print(substring)
                start = match_end
    print(text[start:])
    return None


txt = Path(sys.argv[1]).read_text()
result = make_links_absolute(txt, "https://nixiesearch.ai/")
print(result)
