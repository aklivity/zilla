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
package io.aklivity.zilla.runtime.binding.agui.internal.codec;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;

public final class AguiMessageEncoding
{
    private static final byte[][] NO_EVENTS = new byte[0][];
    private static final byte[] NO_BYTES = new byte[0];

    public final AguiContentMode mode;
    public final byte[][] open;
    public final byte[][] contentOpen;
    public final byte[] prefix;
    public final byte[] suffix;
    public final byte[][] contentClose;
    public final byte[][] close;

    AguiMessageEncoding(
        AguiContentMode mode,
        List<String> open,
        List<String> contentOpen,
        String prefix,
        String suffix,
        List<String> contentClose,
        List<String> close)
    {
        this.mode = mode;
        this.open = events(open);
        this.contentOpen = events(contentOpen);
        this.prefix = prefix != null ? prefix.getBytes(UTF_8) : NO_BYTES;
        this.suffix = suffix != null ? suffix.getBytes(UTF_8) : NO_BYTES;
        this.contentClose = events(contentClose);
        this.close = events(close);
    }

    public boolean within(
        int maximum)
    {
        return prefix.length <= maximum &&
            suffix.length <= maximum &&
            within(open, maximum) &&
            within(contentOpen, maximum) &&
            within(contentClose, maximum) &&
            within(close, maximum);
    }

    private static boolean within(
        byte[][] events,
        int maximum)
    {
        boolean within = true;

        for (byte[] event : events)
        {
            within &= event.length <= maximum;
        }

        return within;
    }

    static byte[][] events(
        List<String> events)
    {
        final byte[][] encoded = events.isEmpty() ? NO_EVENTS : new byte[events.size()][];

        for (int index = 0; index < encoded.length; index++)
        {
            encoded[index] = events.get(index).getBytes(UTF_8);
        }

        return encoded;
    }
}
