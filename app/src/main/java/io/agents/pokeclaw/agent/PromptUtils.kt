// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent

import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog

/**
 * Prompt composition helpers (#45 persistent global prompt).
 *
 * Single responsibility: take a system prompt that some component is about to feed
 * to the LLM, and prepend the user's persistent global instructions when present.
 *
 * Empty / blank user global prompt = no-op. Built-in assistant skill rules are still
 * injected into the base prompt so feature playbooks work without user configuration.
 */
object PromptUtils {
    private const val TAG = "PromptUtils"

    private const val PREFIX_HEADER = "User's persistent global instructions:"
    private const val SEPARATOR = "\n\n---\n\n"

    /**
     * Returns the base prompt with built-in assistant skill rules applied, and then
     * prepends the user's global instructions if any. Stable separator lets
     * debug-report tooling detect user-global injection.
     */
    fun applyGlobalPrompt(basePrompt: String): String {
        val enrichedBase = SocialAssistantPrompt.apply(basePrompt)
        val global = KVUtils.getGlobalPrompt()
        if (global.isBlank()) {
            XLog.d(TAG, "applyGlobalPrompt: no global prompt set, returning enriched base (${enrichedBase.length} chars)")
            return enrichedBase
        }
        XLog.i(
            TAG,
            "applyGlobalPrompt: injecting global prompt (${global.length} chars) into enriched base prompt (${enrichedBase.length} chars)"
        )
        return "$PREFIX_HEADER\n$global$SEPARATOR$enrichedBase"
    }
}
