/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json;

import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;

public final class DirectBufferInputStreamEx extends DirectBufferInputStream
{
    private DirectBuffer wrapBuffer;
    private int wrapOffset;
    private int wrapLength;
    private int markPosition;

    @Override
    public void wrap(
        DirectBuffer buffer,
        int offset,
        int length)
    {
        super.wrap(buffer, offset, length);
        this.wrapBuffer = buffer;
        this.wrapOffset = offset;
        this.wrapLength = length;
        this.markPosition = 0;
    }

    @Override
    public DirectBuffer buffer()
    {
        return wrapBuffer;
    }

    @Override
    public int offset()
    {
        return wrapOffset;
    }

    @Override
    public int length()
    {
        return wrapLength;
    }

    @Override
    public boolean markSupported()
    {
        return true;
    }

    @Override
    public void mark(
        int readlimit)
    {
        markPosition = wrapLength - available();
    }

    @Override
    public void reset()
    {
        super.wrap(wrapBuffer, wrapOffset + markPosition, wrapLength - markPosition);
    }
}
