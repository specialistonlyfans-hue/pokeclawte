---
description: Draft and prepare a Facebook Page post for a business or creator page, e.g. "make a Facebook post for this real estate listing"
tools: open_app, get_screen_info, type_text, finish
author: PhoneAgent Lab
version: 0.1.1
---

# Facebook Page Post Draft

Create a Facebook Page post draft for business, real estate, creator, or local-service content.

## Steps

1. From the user's request, identify:
   - **topic**: listing, offer, update, event, testimonial, announcement, or general post
   - **audience**: buyers, renters, followers, locals, expats, clients, or unknown
   - **tone**: professional, premium, friendly, urgent, local, or informative (default: professional)
   - **CTA**: message, WhatsApp, call, book viewing, learn more, or ask a question
2. If the topic is missing, ask for the content or listing details before proceeding.
3. Use get_screen_info for visible screen text and composer state only when helpful. Do not collect profiles, group members, followers, hidden data, or private user data.
4. Draft one strong Facebook post with a hook, body, CTA, and optional emoji/hashtag line.
5. If the user asks to prepare it in Facebook, open Facebook or the relevant Page tool, navigate only to the composer, and type the selected draft into the composer field found in the UI tree.
6. Never tap Post, Share, Boost, Invite, Send, Like, Follow, Join, or Publish. Stop before the final action.
7. Use finish to return the draft or confirm it is inserted for manual review.

## Example

User: "Make a Facebook post for a Jomtien sea view condo"
→ finish("Dreaming of a sea-view condo in Jomtien? ... Message us to book a viewing.")

## If something goes wrong

- If the Facebook app or Page composer is not available: provide copy-ready text in chat.
- If the composer field cannot be found from the UI tree: provide the post draft and ask the user to paste it manually.
- If the user asks for broad automated posting, keep the workflow to a single manual post draft.
- Keep the draft factual and ask for missing proof when important details are unclear.
