import sys
from pathlib import Path

def replace_markdown_links(s):
    hostname = 'https://nixiesearch.ai'
    result = ''
    i = 0
    while i < len(s):
        if s[i] == '[':
            # Find the closing ']'
            close_bracket = s.find(']', i+1)
            if close_bracket != -1 and close_bracket+1 < len(s) and s[close_bracket+1] == '(':
                # Find the closing ')'
                open_paren = close_bracket + 1
                close_paren = s.find(')', open_paren+1)
                if close_paren != -1:
                    link_text = s[i+1:close_bracket]
                    link_url = s[open_paren+1:close_paren]
                    # Process link_url
                    if not (link_url.startswith('http://') or link_url.startswith('https://')):
                        # Check if link_url ends with '.md'
                        if link_url.endswith('.md'):
                            link_url = link_url[:-3]  # Remove '.md'
                            link_url = hostname + '/' + link_url
                    # Append processed link
                    result += '[' + link_text + '](' + link_url + ')'
                    # Move i to after ')'
                    i = close_paren + 1
                    continue
        # Else, append current character
        result += s[i]
        i += 1
    return result

txt = Path(sys.argv[1]).read_text()
result = replace_markdown_links(txt)
print(result)
