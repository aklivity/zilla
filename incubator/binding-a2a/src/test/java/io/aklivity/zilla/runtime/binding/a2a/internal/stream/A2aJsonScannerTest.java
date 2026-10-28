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
package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class A2aJsonScannerTest
{
    private final A2aJsonScanner scanner = new A2aJsonScanner();

    @Test
    public void shouldFindEndOfCompleteObject()
    {
        final UnsafeBufferEx buffer = buffer("{\"a\":{\"b\":[1,2]}}");

        assertThat(scanner.inMessage(), equalTo(false));
        assertThat(scanner.scan(buffer, 0, buffer.capacity()), equalTo(buffer.capacity()));
        assertThat(scanner.inMessage(), equalTo(false));
    }

    @Test
    public void shouldFindEndAcrossFragments()
    {
        final UnsafeBufferEx first = buffer("{\"text\":\"a}\\\"b");
        final UnsafeBufferEx second = buffer("c\"}");

        assertThat(scanner.scan(first, 0, first.capacity()), equalTo(-1));
        assertThat(scanner.inMessage(), equalTo(true));
        assertThat(scanner.scan(second, 0, second.capacity()), equalTo(3));
        assertThat(scanner.inMessage(), equalTo(false));
    }

    @Test
    public void shouldStopAtFirstMessageEnd()
    {
        final UnsafeBufferEx buffer = buffer("{}{\"x\":1}");

        assertThat(scanner.scan(buffer, 0, buffer.capacity()), equalTo(2));
        assertThat(scanner.scan(buffer, 2, buffer.capacity()), equalTo(buffer.capacity()));
    }

    @Test
    public void shouldIgnoreUnbalancedClose()
    {
        final UnsafeBufferEx buffer = buffer("}] ");

        assertThat(scanner.scan(buffer, 0, buffer.capacity()), equalTo(-1));
        assertThat(scanner.inMessage(), equalTo(false));
    }

    private static UnsafeBufferEx buffer(
        String json)
    {
        return new UnsafeBufferEx(json.getBytes(UTF_8));
    }
}
