# PokeClaw Skill File Specification v1.1

## Overview

Skills are predefined playbooks that tell the on-device LLM exactly what steps to follow. Instead of the model guessing which tools to use, a skill provides a recipe. The model follows it.

Skills are written in natural language Markdown. What you write is what the model sees.

## File Format

**Naming:** `{skill-name}.skill.md`  
**Location:** `app/src/main/assets/skills/` for built-in Android assets, `/sdcard/PokeClaw/skills/` for user-created skills  
**Encoding:** UTF-8

## Frontmatter

Delimited by `---` lines. Flat key-value pairs only.

```yaml
---
description: <required> One sentence. Include a trigger example. Shown to the router LLM for skill selection.
tools: <required> Comma-separated list of PokeClaw runtime tool identifiers.
author: <optional> Name or handle.
version: <optional> Semver.
---
```

- `description` is what the router LLM sees. Write it to maximize matching accuracy. Include an example trigger phrase after a comma.
- `tools` must use exact runtime tool identifiers from `ToolRegistry`. Unknown tools produce a load-time warning; the skill should be skipped if zero valid tools remain.

## Body

Everything after the closing `---` is injected verbatim into the LLM prompt when this skill is selected.

### Recommended Structure

```markdown
# Skill Name

One sentence summary.

## Steps

1. From the user's request, identify:
   - **param1**: description (default: value)
   - **param2**: description
2. If any required information is unclear, ask the user.
3. [Action step with inline error handling if critical]
4. [Action step]
...
N. Call finish with a clear user-visible summary.

## Example

User: "natural language request"
→ tool_call(args)
→ tool_call(args)
→ finish("Confirmation message")

## If something goes wrong

- If [condition]: [response]
- If [condition]: [response]
```

### Rules

- Target 250-350 tokens. Warn at 400.
- Steps are numbered and imperative.
- Step 1 is always parameter extraction. Include defaults.
- Step 2 is always clarification or safe default behavior.
- Use natural language references, not template syntax like `{contact}`.
- Put critical error handling inline with the step.
- One example recommended. Two max.
- Use exact runtime tool names, not friendly display names.

## Routing

On each user message:

1. Runtime builds a routing prompt listing all loaded skills by `description`.
2. LLM outputs a skill name or `none`.
3. Runtime normalizes output and matches against loaded skill filenames.
4. No match → `none` → general conversation mode.

## Execution

1. Runtime reads the matched skill's `tools` field.
2. Runtime builds execution prompt: skill body plus tool definitions for listed tools only.
3. Model follows steps using scoped tools.
4. Skill should call `finish` with the user-visible result when complete.

## Valid Runtime Tool Identifiers

These names must match `BaseTool.getName()` values in the Android runtime.

| Tool | Description |
|------|-------------|
| `open_app` | Launch an app by package/name path available to the runtime |
| `tap` | Tap screen coordinates |
| `tap_node` | Tap a node ID returned by `get_screen_info` |
| `input_text` | Enter text into the focused field or a specific `node_id` |
| `find_node_info` | Find node info by description/text when available |
| `scroll_to_find` | Search for text by scrolling a view |
| `swipe` | Swipe gesture |
| `send_message` | Full messaging flow |
| `auto_reply` | Enable/disable auto-reply monitoring |
| `get_notifications` | Read recent notifications |
| `get_installed_apps` | List installed apps |
| `get_device_info` | Read device/battery/storage/network information |
| `clipboard` | Read or set clipboard content |
| `system_key` | Press Android system keys such as back/home |
| `wait` | Wait for a specified duration |
| `take_screenshot` | Capture current screen and return a local PNG path |
| `get_screen_info` | Read the current Accessibility UI tree |
| `finish` | Signal task completion and return the final user-visible summary |

## Tree-First Control Pattern

For smooth phone control, prefer the Accessibility tree over screenshot loops:

1. Call `get_screen_info`.
2. Use returned text, bounds, node IDs, and editable state.
3. Prefer `input_text(node_id="n5", text="...")` for fields.
4. Prefer `tap_node` or node-derived coordinates over raw guessing.
5. Use `take_screenshot` only for image-only context or when the UI tree is incomplete.

## Example Skills

### send-message.skill.md

```markdown
---
description: Send a message to a contact on a messaging app, e.g. "text Mom hello on WhatsApp"
tools: send_message, finish
---

# Send Message

Send a single text message to a specific contact using a messaging app.

## Steps

1. From the user's request, identify:
   - **contact**: who to message
   - **app**: which app to use (default: WhatsApp)
   - **message**: what to send
2. If any of these are unclear, ask the user before proceeding.
3. Use send_message with the contact, app, and message.
4. Call finish to confirm the message was sent.

## Example

User: "Tell Mom I'll be late on WhatsApp"
→ send_message(contact="Mom", app="WhatsApp", message="I'll be late")
→ finish("Message sent to Mom on WhatsApp.")
```

### open-app.skill.md

```markdown
---
description: Open an app by name, e.g. "open Chrome" or "launch Settings"
tools: open_app, finish
---

# Open App

Open an application on the device.

## Steps

1. From the user's request, identify:
   - **app**: which app to open
2. Use open_app to launch it.
3. Call finish to confirm: "Opened [app]."
```

### instagram-reply-draft.skill.md

```markdown
---
description: Draft a safe Instagram DM or comment reply from visible context, e.g. "reply to this Instagram DM politely"
tools: open_app, get_screen_info, input_text, finish
---

# Instagram Reply Draft

Draft an Instagram DM/comment reply without sending automatically.

## Steps

1. Identify the visible target and desired tone.
2. Use get_screen_info to read visible text, input fields, buttons, and node IDs. Do not use screenshots for normal replies.
3. Draft 1-3 short reply options.
4. If the user explicitly chooses one and asks to prepare it, use input_text with the reply field node_id.
5. Never tap Send, Like, Follow, Block, Report, Delete, Post, Share, Boost, or Publish.
6. Call finish with the draft or confirmation that it is inserted for manual review.
```
