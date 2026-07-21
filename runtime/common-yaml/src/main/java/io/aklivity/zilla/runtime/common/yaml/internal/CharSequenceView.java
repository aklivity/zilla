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
package io.aklivity.zilla.runtime.common.yaml.internal;

/**
 * Reusable, allocation-free {@link CharSequence} view over a slice of a backing sequence — the parse
 * buffer or a previously materialized scalar. A single instance is wrapped onto each scalar in turn so
 * a streaming caller reads or matches the value without copying; {@link #toString()} materializes a
 * {@code String} only on demand.
 */
public final class CharSequenceView implements CharSequence
{
    private CharSequence base;
    private int offset;
    private int length;

    public CharSequenceView wrap(
        CharSequence base,
        int offset,
        int length)
    {
        this.base = base;
        this.offset = offset;
        this.length = length;
        return this;
    }

    @Override
    public int length()
    {
        return length;
    }

    @Override
    public char charAt(
        int index)
    {
        return base.charAt(offset + index);
    }

    @Override
    public CharSequence subSequence(
        int start,
        int end)
    {
        return new CharSequenceView().wrap(base, offset + start, end - start);
    }

    @Override
    public String toString()
    {
        return base.subSequence(offset, offset + length).toString();
    }
}
