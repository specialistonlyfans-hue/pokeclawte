---
description: Draft an Instagram caption and hashtags for a visible post or user idea, e.g. "make an Instagram caption for this property photo"
tools: open_app, get_screen_info, screenshot, type_text, finish
author: PhoneAgent Lab
version: 0.1.1
---

# Instagram Caption Draft

Draft a caption, hook, CTA, and hashtags for an Instagram post without publishing it.

## Steps

1. From the user's request, identify:
   - **topic**: what the post is about
   - **tone**: casual, premium, funny, local, sales, or informative (default: natural)
   - **language**: requested language (default: user's language)
   - **target**: audience or business goal if given
2. If the topic is unclear, ask for the missing detail before proceeding.
3. If Instagram is visible, use get_screen_info first to read text, buttons, input fields, and the current UI tree. Use screenshot only when the relevant context is image-only or missing from the Accessibility tree. Do not scrape profiles, hidden data, followers, or private content.
4. Draft three caption options: short, medium, and sales-focused. Include one clear CTA and 3-8 relevant hashtags.
5. If the user asks to prepare the post in Instagram, open Instagram, navigate only as far as the caption field, and type the selected caption using the most direct visible UI field from the tree.
6. Never tap Share, Post, Send, Follow, Like, or Comment. Stop before the final publishing action.
7. Use finish to return the caption options or confirm that the selected caption was inserted for manual review.

## Example

User: "Make an Instagram caption for this Pattaya condo"
→ get_screen_info()
→ screenshot() only if the post context is visual-only
→ finish("Here are three caption options...")

## If something goes wrong

- If Instagram is not installed: provide the captions in chat.
- If the caption field cannot be found from the UI tree: provide copy-ready text and tell the user to paste it manually.
- If the user asks for spam, fake engagement, scraping, or mass posting: refuse and offer a manual content-drafting alternative.
