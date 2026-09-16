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
package io.aklivity.zilla.runtime.binding.llm.dialect;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class LlmOpenaiSubstitutedSourceTest
{
    private final LlmOpenaiSubstitutedSource source = new LlmOpenaiSubstitutedSource();

    @Test
    public void shouldExposeWrappedPathAndValue()
    {
        DirectBufferEx value = new UnsafeBufferEx("256".getBytes(UTF_8));

        LlmOpenaiSubstitutedSource wrapped = source.wrap("$.maxOutputTokens", value);

        assertThat(wrapped, sameInstance(source));
        assertThat(source.getPath(), equalTo("$.maxOutputTokens"));
        assertThat(source.getValue(), sameInstance(value));
    }

    @Test
    public void shouldRewrapForEachSubstitutedField()
    {
        DirectBufferEx first = new UnsafeBufferEx("256".getBytes(UTF_8));
        DirectBufferEx second = new UnsafeBufferEx("0.9".getBytes(UTF_8));

        source.wrap("$.maxOutputTokens", first);
        source.wrap("$.topP", second);

        assertThat(source.getPath(), equalTo("$.topP"));
        assertThat(source.getValue(), sameInstance(second));
    }
}
