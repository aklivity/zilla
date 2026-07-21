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
package io.aklivity.zilla.runtime.binding.mcp.internal.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lowercases, splits on non-alphanumeric characters and camelCase word boundaries
 * (including acronym-to-word boundaries, e.g. {@code parseXMLDocument} to
 * {@code parse}/{@code xml}/{@code document}), and strips a small built-in English
 * stopword list. No stemming.
 */
public final class McpTextTokenizer
{
    private static final Set<String> STOPWORDS = Set.of(
        "a", "an", "the", "and", "or", "but", "if", "then", "else", "for", "to", "of", "in", "on",
        "at", "by", "with", "from", "as", "is", "are", "was", "were", "be", "been", "being",
        "this", "that", "these", "those", "it", "its", "into", "than", "so", "such", "not", "no",
        "do", "does", "did", "can", "will", "would", "should", "could", "may", "might", "must",
        "about", "up", "down", "out", "off", "over", "under", "again", "further", "once", "here",
        "there", "when", "where", "why", "how", "all", "each", "other", "some", "own", "same");

    private McpTextTokenizer()
    {
    }

    public static List<String> tokenize(
        String text)
    {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty())
        {
            return tokens;
        }

        StringBuilder token = new StringBuilder();
        int length = text.length();

        for (int i = 0; i < length; i++)
        {
            char current = text.charAt(i);
            if (Character.isLetterOrDigit(current))
            {
                if (token.length() > 0 && isWordBoundary(text, i))
                {
                    addToken(tokens, token);
                    token.setLength(0);
                }
                token.append(Character.toLowerCase(current));
            }
            else if (token.length() > 0)
            {
                addToken(tokens, token);
                token.setLength(0);
            }
        }

        if (token.length() > 0)
        {
            addToken(tokens, token);
        }

        return tokens;
    }

    private static boolean isWordBoundary(
        String text,
        int index)
    {
        char current = text.charAt(index);
        char previous = text.charAt(index - 1);

        boolean lowerToUpper = Character.isUpperCase(current) && Character.isLowerCase(previous);
        boolean acronymToWord = Character.isUpperCase(current) &&
            Character.isUpperCase(previous) &&
            index + 1 < text.length() &&
            Character.isLowerCase(text.charAt(index + 1));

        return lowerToUpper || acronymToWord;
    }

    private static void addToken(
        List<String> tokens,
        StringBuilder token)
    {
        String word = token.toString();
        if (!STOPWORDS.contains(word))
        {
            tokens.add(word);
        }
    }
}
