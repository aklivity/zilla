/*
 * Copyright 2021-2022 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static java.lang.Integer.toUnsignedLong;

public final class KafkaCacheCursorRecord
{
    public static final int NEXT_SEGMENT_VALUE = Integer.MAX_VALUE - 1;
    public static final int RETRY_SEGMENT_VALUE = -1;

    public static boolean cursorNextValue(
        long cursor)
    {
        return cursorValue(cursor) == NEXT_SEGMENT_VALUE;
    }

    public static boolean cursorRetryValue(
        long cursor)
    {
        return cursorValue(cursor) == RETRY_SEGMENT_VALUE;
    }

    public static int cursorIndex(
        long cursor)
    {
        return (int)(cursor >>> 32);
    }

    public static int cursorValue(
        long cursor)
    {
        return (int)(cursor & 0xFFFF_FFFFL);
    }

    public static long cursor(
        int index,
        int value)
    {
        return ((long) index << 32) | toUnsignedLong(value);
    }

    private KafkaCacheCursorRecord()
    {
        // no instances
    }
}
