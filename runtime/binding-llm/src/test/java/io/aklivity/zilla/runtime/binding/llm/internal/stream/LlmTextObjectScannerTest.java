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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmTextObjectScannerTest
{
    private final LlmTextObjectScanner scanner = new LlmTextObjectScanner();

    @Test
    public void shouldSplitTextOnlyObject()
    {
        assertSplit("{\"text\":\"", "Hello", "\"}", "{\"text\":\"Hello\"}");
    }

    @Test
    public void shouldKeepLeadingMemberInPrefix()
    {
        assertSplit("{\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"text\":\"", "my secret", "\"}",
            "{\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"text\":\"my secret\"}");
    }

    @Test
    public void shouldKeepTrailingMembersInSuffix()
    {
        assertSplit("{\"text\":\"", "my secret", "\",\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"x\":[1,2]}",
            "{\"text\":\"my secret\",\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"x\":[1,2]}");
    }

    @Test
    public void shouldKeepWhitespaceVerbatim()
    {
        assertSplit("{ \"a\" : 1 , \"text\" :  \"", "hi", "\" , \"b\" : 2 }",
            "{ \"a\" : 1 , \"text\" :  \"hi\" , \"b\" : 2 }");
    }

    @Test
    public void shouldNotConfuseTextKeyInsideMemberValue()
    {
        assertSplit("{\"a\":{\"text\":\"inner\"},\"b\":\"text\",\"text\":\"", "outer", "\"}",
            "{\"a\":{\"text\":\"inner\"},\"b\":\"text\",\"text\":\"outer\"}");
    }

    @Test
    public void shouldNotEndTextOnEscapedQuote()
    {
        assertSplit("{\"text\":\"", "say \\\"hi\\\" \\\\", "\"}", "{\"text\":\"say \\\"hi\\\" \\\\\"}");
    }

    @Test
    public void shouldSplitEmptyText()
    {
        assertSplit("{\"text\":\"", "", "\"}", "{\"text\":\"\"}");
    }

    @Test
    public void shouldKeepWholeObjectInPrefixWhenTextAbsent()
    {
        assertSplit("{\"a\":1}", "", "", "{\"a\":1}");
    }

    private void assertSplit(
        String prefix,
        String text,
        String suffix,
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);

        for (int chunk = 1; chunk <= bytes.length; chunk++)
        {
            scanner.begin();

            final StringBuilder prefixes = new StringBuilder();
            final StringBuilder texts = new StringBuilder();
            final StringBuilder suffixes = new StringBuilder();

            for (int from = 0; from < bytes.length; from += chunk)
            {
                final int limit = Math.min(from + chunk, bytes.length);
                final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes, 0, limit);

                int position = from;
                while (position < limit)
                {
                    final int region = scanner.region();
                    final int end = scanner.scan(buffer, position, limit);
                    final String piece = new String(bytes, position, end - position, UTF_8);

                    switch (region)
                    {
                    case LlmTextObjectScanner.PREFIX:
                        prefixes.append(piece);
                        break;
                    case LlmTextObjectScanner.TEXT:
                        texts.append(piece);
                        break;
                    default:
                        suffixes.append(piece);
                        break;
                    }

                    position = end;
                }
            }

            assertThat("prefix at chunk " + chunk, prefixes.toString(), equalTo(prefix));
            assertThat("text at chunk " + chunk, texts.toString(), equalTo(text));
            assertThat("suffix at chunk " + chunk, suffixes.toString(), equalTo(suffix));
        }
    }
}
