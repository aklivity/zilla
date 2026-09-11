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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import jakarta.json.stream.JsonLocation;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

public class LlmOpenAiSubstitutedSourceTest
{
    private final JsonSource delegate = mock(JsonSource.class);
    private final LlmOpenAiSubstitutedSource source = new LlmOpenAiSubstitutedSource();

    @Test
    public void shouldReturnSubstitutedTextInsteadOfDelegateText()
    {
        source.wrap(delegate, "substituted");

        assertThat(source.getString(), equalTo("substituted"));
        assertThat(source.getStringView(), equalTo("substituted"));
    }

    @Test
    public void shouldDelegateNumericAccessors()
    {
        BigDecimal decimal = BigDecimal.TEN;
        when(delegate.getBigDecimal()).thenReturn(decimal);
        when(delegate.isIntegralNumber()).thenReturn(true);
        when(delegate.getInt()).thenReturn(42);
        when(delegate.getLong()).thenReturn(42L);

        source.wrap(delegate, "ignored");

        assertThat(source.getBigDecimal(), sameInstance(decimal));
        assertThat(source.isIntegralNumber(), equalTo(true));
        assertThat(source.getInt(), equalTo(42));
        assertThat(source.getLong(), equalTo(42L));
    }

    @Test
    public void shouldDelegateLocationSegmentVerbatimAndDeferred()
    {
        JsonLocation location = mock(JsonLocation.class);
        DirectBufferEx segment = mock(DirectBufferEx.class);
        JsonVerbatim verbatim = mock(JsonVerbatim.class);
        when(delegate.getLocation()).thenReturn(location);
        when(delegate.getSegment()).thenReturn(segment);
        when(delegate.getVerbatim(64)).thenReturn(verbatim);
        when(delegate.deferredBytes()).thenReturn(true);

        source.wrap(delegate, "ignored");

        assertThat(source.getLocation(), sameInstance(location));
        assertThat(source.getSegment(), sameInstance(segment));
        assertThat(source.getVerbatim(64), sameInstance(verbatim));
        assertThat(source.deferredBytes(), equalTo(true));
    }

    @Test
    public void shouldDelegateSkipValue()
    {
        source.wrap(delegate, "ignored");

        source.skipValue();

        verify(delegate).skipValue();
    }
}
