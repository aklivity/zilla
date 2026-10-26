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
        final int mode = value.member(0, MODE);
        final boolean explicit = mode != LlmCapturedValue.ABSENT && value.isString(mode, MODE_EXPLICIT);
        final boolean admit = explicit && admitted < limit;

        if (admit)
        {
            final int requested = value.member(0, TTL);

            ttl = requested != LlmCapturedValue.ABSENT ? select(value.string(requested), ttls) : null;
            admitted++;
        }

        return admit;
    }

    String ttl()
    {
        return ttl;
    }

    static void toCanonical(
        LlmToolTransform transform,
        LlmCapturedValue value)
    {
        final int type = value.member(0, NATIVE_TYPE);

        if (type != LlmCapturedValue.ABSENT && value.isString(type, NATIVE_EPHEMERAL))
        {
            final int requested = value.member(0, TTL);
            final String ttl = requested != LlmCapturedValue.ABSENT ? value.string(requested) : null;

            transform.emit(JsonEvent.KEY_NAME, MEMBER);
            transform.emit(JsonEvent.START_OBJECT);
            transform.emit(JsonEvent.KEY_NAME, MODE);
            transform.emit(JsonEvent.VALUE_STRING, MODE_EXPLICIT);

            if (ttl != null)
            {
                transform.emit(JsonEvent.KEY_NAME, TTL);
                transform.emit(JsonEvent.VALUE_STRING, ttl);
            }

            transform.emit(JsonEvent.END_OBJECT);
        }
    }

    void fromCanonical(
        LlmToolTransform transform,
        LlmCapturedValue value)
    {
        if (admit(value))
        {
            transform.emit(JsonEvent.KEY_NAME, NATIVE_MEMBER);
            transform.emit(JsonEvent.START_OBJECT);
            transform.emit(JsonEvent.KEY_NAME, NATIVE_TYPE);
            transform.emit(JsonEvent.VALUE_STRING, NATIVE_EPHEMERAL);

            if (ttl != null)
            {
                transform.emit(JsonEvent.KEY_NAME, TTL);
                transform.emit(JsonEvent.VALUE_STRING, ttl);
            }

            transform.emit(JsonEvent.END_OBJECT);
        }
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
