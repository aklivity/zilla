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
package io.aklivity.zilla.runtime.classifier.patterns.internal;

final class PatternsInput implements CharSequence
{
    private String value;
    private long remaining;

    PatternsInput()
    {
        this.value = "";
    }

    void reset(
        String value,
        long steps)
    {
        this.value = value;
        this.remaining = steps;
    }

    @Override
    public int length()
    {
        return value.length();
    }

    @Override
    public char charAt(
        int index)
    {
        if (--remaining < 0L)
        {
            throw new IllegalStateException("match steps exceeded");
        }

        return value.charAt(index);
    }

    @Override
    public CharSequence subSequence(
        int start,
        int end)
    {
        return value.subSequence(start, end);
    }

    @Override
    public String toString()
    {
        return value;
    }
}
