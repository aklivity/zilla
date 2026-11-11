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

import io.aklivity.zilla.runtime.common.json.JsonEvent;

final class LlmCacheBreakpoint
{
    static final String MEMBER = "prompt_cache_breakpoint";
    static final String MODE = "mode";
    static final String MODE_EXPLICIT = "explicit";
    static final String TTL = "ttl";
    static final String NATIVE_MEMBER = "cache_control";
    static final String NATIVE_TYPE = "type";
    static final String NATIVE_EPHEMERAL = "ephemeral";

    // top of the subclass bit range, clear of the bits a calling transform uses for its own emits
    private static final int BIT_MEMBER_KEY = 1 << 11;
    private static final int BIT_OBJECT_START = 1 << 12;
    private static final int BIT_MODE_KEY = 1 << 13;
    private static final int BIT_MODE_VALUE = 1 << 14;
    private static final int BIT_TTL_KEY = 1 << 15;
    private static final int BIT_TTL_VALUE = 1 << 16;
    private static final int BIT_OBJECT_END = 1 << 17;

    private static final int NOT_A_DURATION = -1;
    private static final int DURATION_DIGITS_MAX = 9;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_HOUR = 3600;
    private static final int SECONDS_PER_DAY = 86400;

    private final String[] ttls;
    private final int limit;

    private int admitted;
    private String ttl;

    LlmCacheBreakpoint(
        int limit,
        String... ttls)
    {
        this.limit = limit;
        this.ttls = ttls;
    }

    void reset()
    {
        admitted = 0;
        ttl = null;
    }

    boolean admit(
        LlmCapturedValue value)
    {
        final boolean admit = permits(value);

        if (admit)
        {
            ttl = selectedTtl(value);
            admitted++;
        }

        return admit;
    }

    String ttl()
    {
        return ttl;
    }

    static boolean toCanonical(
        LlmToolTransform transform,
        LlmCapturedValue value)
    {
        final int type = value.member(0, NATIVE_TYPE);
        boolean delivered = true;

        if (type != LlmCapturedValue.ABSENT && value.isString(type, NATIVE_EPHEMERAL))
        {
            final int requested = value.member(0, TTL);
            final String ttl = requested != LlmCapturedValue.ABSENT ? value.string(requested) : null;

            delivered = transform.emit(BIT_MEMBER_KEY, JsonEvent.KEY_NAME, MEMBER) &&
                transform.emit(BIT_OBJECT_START, JsonEvent.START_OBJECT) &&
                transform.emit(BIT_MODE_KEY, JsonEvent.KEY_NAME, MODE) &&
                transform.emit(BIT_MODE_VALUE, JsonEvent.VALUE_STRING, MODE_EXPLICIT) &&
                (ttl == null ||
                    transform.emit(BIT_TTL_KEY, JsonEvent.KEY_NAME, TTL) &&
                    transform.emit(BIT_TTL_VALUE, JsonEvent.VALUE_STRING, ttl)) &&
                transform.emit(BIT_OBJECT_END, JsonEvent.END_OBJECT);
        }

        return delivered;
    }

    boolean fromCanonical(
        LlmToolTransform transform,
        LlmCapturedValue value)
    {
        boolean delivered = true;

        if (permits(value))
        {
            final String selected = selectedTtl(value);
            final boolean counted = transform.emitted(BIT_OBJECT_END);

            delivered = transform.emit(BIT_MEMBER_KEY, JsonEvent.KEY_NAME, NATIVE_MEMBER) &&
                transform.emit(BIT_OBJECT_START, JsonEvent.START_OBJECT) &&
                transform.emit(BIT_MODE_KEY, JsonEvent.KEY_NAME, NATIVE_TYPE) &&
                transform.emit(BIT_MODE_VALUE, JsonEvent.VALUE_STRING, NATIVE_EPHEMERAL) &&
                (selected == null ||
                    transform.emit(BIT_TTL_KEY, JsonEvent.KEY_NAME, TTL) &&
                    transform.emit(BIT_TTL_VALUE, JsonEvent.VALUE_STRING, selected)) &&
                transform.emit(BIT_OBJECT_END, JsonEvent.END_OBJECT);

            if (delivered && !counted)
            {
                ttl = selected;
                admitted++;
            }
        }

        return delivered;
    }

    private boolean permits(
        LlmCapturedValue value)
    {
        final int mode = value.member(0, MODE);

        return mode != LlmCapturedValue.ABSENT && value.isString(mode, MODE_EXPLICIT) && admitted < limit;
    }

    private String selectedTtl(
        LlmCapturedValue value)
    {
        final int requested = value.member(0, TTL);

        return requested != LlmCapturedValue.ABSENT ? select(value.string(requested), ttls) : null;
    }

    static String select(
        String requested,
        String[] offered)
    {
        final long wanted = seconds(requested);
        String selected = null;

        if (wanted != NOT_A_DURATION)
        {
            selected = offered[offered.length - 1];

            for (int index = offered.length - 1; index >= 0; index--)
            {
                if (seconds(offered[index]) >= wanted)
                {
                    selected = offered[index];
                }
            }
        }

        return selected;
    }

    static long seconds(
        String duration)
    {
        final int length = duration != null ? duration.length() : 0;
        long seconds = NOT_A_DURATION;

        if (length > 1 && length <= DURATION_DIGITS_MAX + 1)
        {
            final int unit = switch (duration.charAt(length - 1))
            {
            case 's' -> 1;
            case 'm' -> SECONDS_PER_MINUTE;
            case 'h' -> SECONDS_PER_HOUR;
            case 'd' -> SECONDS_PER_DAY;
            default -> 0;
            };

            long count = 0;
            boolean digits = unit != 0;

            for (int index = 0; digits && index < length - 1; index++)
            {
                final char digit = duration.charAt(index);

                digits = digit >= '0' && digit <= '9';
                count = digits ? count * 10 + digit - '0' : count;
            }

            if (digits)
            {
                seconds = count * unit;
            }
        }

        return seconds;
    }
}
