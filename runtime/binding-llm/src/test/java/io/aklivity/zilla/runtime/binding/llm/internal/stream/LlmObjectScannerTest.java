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

public class LlmObjectScannerTest
{
    private final LlmObjectScanner text = LlmObjectScanner.text();
    private final LlmObjectScanner member = LlmObjectScanner.member("arguments");

    @Test
    public void shouldSplitTextOnlyObject()
    {
        assertTextSplit("{\"text\":\"", "Hello", "\"}", "{\"text\":\"Hello\"}");
    }

    @Test
    public void shouldKeepLeadingMemberInPrefix()
    {
        assertTextSplit("{\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"text\":\"", "my secret", "\"}",
            "{\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"text\":\"my secret\"}");
    }

    @Test
    public void shouldKeepTrailingMembersInSuffix()
    {
        assertTextSplit("{\"text\":\"", "my secret", "\",\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"x\":[1,2]}",
            "{\"text\":\"my secret\",\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"x\":[1,2]}");
    }

    @Test
    public void shouldKeepWhitespaceVerbatim()
    {
        assertTextSplit("{ \"a\" : 1 , \"text\" :  \"", "hi", "\" , \"b\" : 2 }",
            "{ \"a\" : 1 , \"text\" :  \"hi\" , \"b\" : 2 }");
    }

    @Test
    public void shouldNotConfuseTextKeyInsideMemberValue()
    {
        assertTextSplit("{\"a\":{\"text\":\"inner\"},\"b\":\"text\",\"text\":\"", "outer", "\"}",
            "{\"a\":{\"text\":\"inner\"},\"b\":\"text\",\"text\":\"outer\"}");
    }

    @Test
    public void shouldNotEndTextOnEscapedQuote()
    {
        assertTextSplit("{\"text\":\"", "say \\\"hi\\\" \\\\", "\"}", "{\"text\":\"say \\\"hi\\\" \\\\\"}");
    }

    @Test
    public void shouldSplitEmptyText()
    {
        assertTextSplit("{\"text\":\"", "", "\"}", "{\"text\":\"\"}");
    }

    @Test
    public void shouldKeepWholeObjectInPrefixWhenTextAbsent()
    {
        assertTextSplit("{\"a\":1}", "", "", "{\"a\":1}");
    }

    @Test
    public void shouldSplitObjectMember()
    {
        assertMemberSplit("{\"arguments\":", "{\"city\":\"Paris\"}", "}", "{\"arguments\":{\"city\":\"Paris\"}}");
    }

    @Test
    public void shouldKeepMembersAroundObjectMemberOutsideValue()
    {
        assertMemberSplit("{\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"arguments\":", "{\"a\":[1,2]}",
            ",\"anthropic:note\":\"x\"}",
            "{\"anthropic:cache_control\":{\"type\":\"ephemeral\"},\"arguments\":{\"a\":[1,2]},\"anthropic:note\":\"x\"}");
    }

    @Test
    public void shouldKeepWhitespaceAroundMemberValueOutsideValue()
    {
        assertMemberSplit("{ \"a\" : 1 , \"arguments\" :  ", "{\"a\":1}", " , \"b\" : 2 }",
            "{ \"a\" : 1 , \"arguments\" :  {\"a\":1} , \"b\" : 2 }");
    }

    @Test
    public void shouldNotEndMemberValueOnBracketsInsideStrings()
    {
        assertMemberSplit("{\"arguments\":", "{\"a\":\"}]\\\"}\"}", "}", "{\"arguments\":{\"a\":\"}]\\\"}\"}}");
    }

    @Test
    public void shouldSplitStringMember()
    {
        assertMemberSplit("{\"arguments\":", "\"{\\\"a\\\":1}\"", "}", "{\"arguments\":\"{\\\"a\\\":1}\"}");
    }

    @Test
    public void shouldSplitArrayMember()
    {
        assertMemberSplit("{\"arguments\":", "[1,[2],{\"b\":3}]", ",\"x\":1}", "{\"arguments\":[1,[2],{\"b\":3}],\"x\":1}");
    }

    @Test
    public void shouldSplitScalarMemberAtItsDelimiter()
    {
        assertMemberSplit("{\"arguments\":", "42", ",\"x\":1}", "{\"arguments\":42,\"x\":1}");
        assertMemberSplit("{\"arguments\":", "null", "}", "{\"arguments\":null}");
    }

    @Test
    public void shouldSplitEmptyObjectMember()
    {
        assertMemberSplit("{\"arguments\":", "{}", "}", "{\"arguments\":{}}");
    }

    @Test
    public void shouldNotConfuseMemberKeyInsideMemberValue()
    {
        assertMemberSplit("{\"a\":{\"arguments\":{\"x\":1}},\"b\":\"arguments\",\"arguments\":", "{\"y\":2}", "}",
            "{\"a\":{\"arguments\":{\"x\":1}},\"b\":\"arguments\",\"arguments\":{\"y\":2}}");
    }

    @Test
    public void shouldKeepWholeObjectInPrefixWhenMemberAbsent()
    {
        assertMemberSplit("{\"a\":1}", "", "", "{\"a\":1}");
    }

    private void assertSplit(
        LlmObjectScanner scanner,
        String prefix,
        String value,
        String suffix,
        String json)
    {
        final byte[] bytes = json.getBytes(UTF_8);

        for (int chunk = 1; chunk <= bytes.length; chunk++)
        {
            scanner.begin();

            final StringBuilder prefixes = new StringBuilder();
            final StringBuilder values = new StringBuilder();
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
                    case LlmObjectScanner.PREFIX:
                        prefixes.append(piece);
                        break;
                    case LlmObjectScanner.VALUE:
                        values.append(piece);
                        break;
                    default:
                        suffixes.append(piece);
                        break;
                    }

                    position = end;
                }
            }

            assertThat("prefix at chunk " + chunk, prefixes.toString(), equalTo(prefix));
            assertThat("value at chunk " + chunk, values.toString(), equalTo(value));
            assertThat("suffix at chunk " + chunk, suffixes.toString(), equalTo(suffix));
        }
    }

    private void assertTextSplit(
        String prefix,
        String value,
        String suffix,
        String json)
    {
        assertSplit(text, prefix, value, suffix, json);
    }

    private void assertMemberSplit(
        String prefix,
        String value,
        String suffix,
        String json)
    {
        assertSplit(member, prefix, value, suffix, json);
    }
}
