/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import static io.aklivity.zilla.runtime.binding.llm.config.LlmServiceTier.AUTO;
import static io.aklivity.zilla.runtime.binding.llm.config.LlmServiceTier.DEFAULT;
import static io.aklivity.zilla.runtime.binding.llm.config.LlmServiceTier.FLEX;
import static io.aklivity.zilla.runtime.binding.llm.config.LlmServiceTier.INVALID;
import static io.aklivity.zilla.runtime.binding.llm.config.LlmServiceTier.PRIORITY;

import io.aklivity.zilla.runtime.binding.llm.config.LlmModelCapability;
import io.aklivity.zilla.runtime.binding.llm.config.LlmServiceTier;

final class LlmServiceTiers
{
    static final int NAME_MAX = 16;

    private static final String ANTHROPIC_AUTO = "auto";
    private static final String ANTHROPIC_STANDARD_ONLY = "standard_only";
    private static final String GEMINI_STANDARD = "standard";
    private static final String GEMINI_FLEX = "flex";
    private static final String GEMINI_PRIORITY = "priority";

    static String canonicalOpenai(
        String tier)
    {
        final int canonical = tier != null ? LlmServiceTier.of(tier) : INVALID;

        return canonical != INVALID ? LlmServiceTier.nameOf(canonical) : null;
    }

    static String canonicalAnthropic(
        String tier)
    {
        String canonical = null;

        if (ANTHROPIC_AUTO.equals(tier))
        {
            canonical = LlmServiceTier.nameOf(AUTO);
        }
        else if (ANTHROPIC_STANDARD_ONLY.equals(tier))
        {
            canonical = LlmServiceTier.nameOf(DEFAULT);
        }

        return canonical;
    }

    static String canonicalGemini(
        String tier)
    {
        String canonical = null;

        if (GEMINI_STANDARD.equalsIgnoreCase(tier))
        {
            canonical = LlmServiceTier.nameOf(DEFAULT);
        }
        else if (GEMINI_FLEX.equalsIgnoreCase(tier))
        {
            canonical = LlmServiceTier.nameOf(FLEX);
        }
        else if (GEMINI_PRIORITY.equalsIgnoreCase(tier))
        {
            canonical = LlmServiceTier.nameOf(PRIORITY);
        }

        return canonical;
    }

    static String openai(
        LlmModelCapability capability,
        CharSequence tier)
    {
        final int canonical = LlmServiceTier.of(tier);

        return canonical != INVALID && capability.supports(canonical) ? LlmServiceTier.nameOf(canonical) : null;
    }

    static String anthropic(
        LlmModelCapability capability,
        CharSequence tier)
    {
        final int canonical = LlmServiceTier.of(tier);
        String wire = null;

        if (canonical != INVALID && capability.supports(canonical))
        {
            wire = switch (canonical)
            {
            case AUTO -> ANTHROPIC_AUTO;
            case DEFAULT -> ANTHROPIC_STANDARD_ONLY;
            default -> null;
            };
        }

        return wire;
    }

    static String gemini(
        LlmModelCapability capability,
        CharSequence tier)
    {
        final int canonical = LlmServiceTier.of(tier);
        String wire = null;

        if (canonical != INVALID && capability.supports(canonical))
        {
            wire = switch (canonical)
            {
            case DEFAULT -> GEMINI_STANDARD;
            case FLEX -> GEMINI_FLEX;
            case PRIORITY -> GEMINI_PRIORITY;
            default -> null;
            };
        }

        return wire;
    }

    private LlmServiceTiers()
    {
    }
}
