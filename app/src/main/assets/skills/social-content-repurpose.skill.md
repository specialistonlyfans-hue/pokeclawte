---
description: Turn one idea into platform-specific Instagram and Facebook drafts, e.g. "repurpose this listing for Instagram and Facebook"
tools: get_screen_info, screenshot, finish
author: PhoneAgent Lab
version: 0.1.1
---

# Social Content Repurpose

Turn one visible idea, listing, note, or message into safe platform-specific drafts for Instagram and Facebook.

## Steps

1. From the user's request, identify:
   - **source content**: visible screen text, pasted text, image/listing idea, or user description
   - **platforms**: Instagram, Facebook, or both (default: both)
   - **goal**: awareness, lead, sale, booking, reply, update, or engagement question
   - **language**: requested language (default: user's language)
2. If source content is missing, ask the user to paste it or open it on screen.
3. If content is visible on screen, use get_screen_info first to read visible text from the Accessibility tree. Use screenshot only when the source is image-only or the UI tree is incomplete.
4. Produce separate drafts for each platform:
   - Instagram: hook, short caption, CTA, 3-8 hashtags.
   - Facebook: stronger first line, fuller body, CTA, optional question to invite comments.
5. Keep claims factual. Do not invent prices, availability, guarantees, addresses, discounts, legal claims, or testimonials.
6. Do not open apps, post, comment, message, like, follow, collect hidden data, or boost. This skill only drafts copy.
7. Use finish with clean copy-ready sections.

## Example

User: "Repurpose this Pattaya villa listing for Instagram and Facebook"
→ get_screen_info()
→ screenshot() only if the listing is image-only
→ finish("Instagram draft: ...\n\nFacebook draft: ...")

## If something goes wrong

- If the listing lacks key facts: write a conservative draft and mark missing facts clearly.
- If the user asks to copy a competitor post: extract only the structure and create original wording.
- If the request is for broad automated posting or fake engagement: refuse and offer manual platform-safe drafts.
