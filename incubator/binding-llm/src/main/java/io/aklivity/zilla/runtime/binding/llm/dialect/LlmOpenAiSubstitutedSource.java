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

import java.math.BigDecimal;

import jakarta.json.stream.JsonLocation;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonVerbatim;

/**
 * Wraps a {@link JsonSource} to substitute the text of the current scalar token -- an object key or a string
 * value -- with a different literal, so an OpenAI dialect transform can rename a key or remap a value by
 * handing the sink this view rather than re-serializing the surrounding document by hand. Every other
 * accessor delegates to the wrapped source unchanged.
 * <p>
 * One instance is held per transform and rewrapped once per substituted event via {@link #wrap(JsonSource,
 * String)}; it is never retained beyond the current {@code transform} call.
 * </p>
 */
final class LlmOpenAiSubstitutedSource implements JsonSource
{
    private JsonSource delegate;
    private String text;

    LlmOpenAiSubstitutedSource wrap(
        JsonSource delegate,
        String text)
    {
        this.delegate = delegate;
        this.text = text;
        return this;
    }

    @Override
    public String getString()
    {
        return text;
    }

    @Override
    public CharSequence getStringView()
    {
        return text;
    }

    @Override
    public BigDecimal getBigDecimal()
    {
        return delegate.getBigDecimal();
    }

    @Override
    public boolean isIntegralNumber()
    {
        return delegate.isIntegralNumber();
    }

    @Override
    public int getInt()
    {
        return delegate.getInt();
    }

    @Override
    public long getLong()
    {
        return delegate.getLong();
    }

    @Override
    public JsonLocation getLocation()
    {
        return delegate.getLocation();
    }

    @Override
    public DirectBufferEx getSegment()
    {
        return delegate.getSegment();
    }

    @Override
    public JsonVerbatim getVerbatim(
        int limit)
    {
        return delegate.getVerbatim(limit);
    }

    @Override
    public void skipValue()
    {
        delegate.skipValue();
    }

    @Override
    public boolean deferredBytes()
    {
        return delegate.deferredBytes();
    }
}
