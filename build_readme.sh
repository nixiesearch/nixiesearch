#!/bin/bash

# Update all relevant links to the docs micro-site. Made with ChatGPT.

# What it does:
# \[(.*?)\] – This captures the text inside the square brackets (link label).
# \(([^)]+)\.md\) – This captures the link, ensuring it doesn't include ) and ends with .md.
# [^)] – Matches any character except for ) (to avoid prematurely ending the match for relative links).
# \.md – This ensures the match ends with .md, which we want to remove.
# [\1](https://site.com/\2) – In the replacement, we construct the new absolute URL by appending the site.com hostname to the captured relative link.

sed -E 's/\[(.*?)\]\(([^)]+)\.md\)/[\1](https:\/\/nixiesearch.ai\/\2)/g' docs/docs/index.md > README.md
