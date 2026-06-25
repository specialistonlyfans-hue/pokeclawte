---
description: Review visible Instagram comments and identify questions, leads, or replies worth answering, e.g. "check these Instagram comments for leads"
tools: open_app, get_screen_info, scroll, finish
author: PhoneAgent Lab
version: 0.1.1
---

# Instagram Comment Triage

Summarize visible Instagram comments and suggest which ones deserve a manual reply.

## Steps

1. From the user's request, identify:
   - **goal**: leads, support questions, complaints, compliments, spam, or all important comments (default: important comments)
   - **scope**: currently visible comments or a small manual review session (default: visible only)
2. If the goal is unclear, proceed with important comments and explain the categories used.
3. Open Instagram only if requested. Use get_screen_info to read visible comments from the Accessibility tree. Scroll at most 3 times unless the user explicitly asks to continue.
4. Categorize comments into: likely lead, question, complaint, compliment, spam/low-value, and needs human judgment.
5. Suggest short reply drafts for the most valuable comments, but do not type or post them unless the user chooses one.
6. Never like, follow, delete, report, pin, hide, or post replies automatically.
7. Use finish with a concise triage summary and recommended next actions.

## Example

User: "Check these Instagram comments for buying interest"
→ get_screen_info()
→ scroll("down") if more visible comments are needed
→ finish("Likely leads: 2. Questions: 1. Suggested replies: ...")

## If something goes wrong

- If comments are not visible in the UI tree: ask the user to open the comment screen.
- If the screen contains private or sensitive personal data: summarize minimally and avoid copying unnecessary details.
- If the user asks for mass replies or engagement farming: refuse and keep the workflow to triage and manual reply drafts.
