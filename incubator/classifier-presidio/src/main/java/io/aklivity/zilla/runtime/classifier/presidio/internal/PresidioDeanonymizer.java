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
package io.aklivity.zilla.runtime.classifier.presidio.internal;


import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Set;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.classifier.Deanonymizer;
import io.aklivity.zilla.runtime.engine.classifier.ValueConsumerEx;

final class PresidioDeanonymizer implements Deanonymizer
{
    private static final byte[] OPENER = PresidioSurrogates.OPEN.getBytes(UTF_8);
    private static final byte[] CLOSER = PresidioSurrogates.CLOSE.getBytes(UTF_8);
    private static final byte LEAD = OPENER[0];
    private static final int MAX_TOKEN = 96;
    private static final int HEX_DIGITS = 8;

    private static final int NOT_TOKEN = -1;
    private static final int PARTIAL = -2;
    private static final int PENDING = 0;

    private static final int IN_OPENER = 0;
    private static final int IN_LABEL = 1;
    private static final int IN_NONCE = 2;
    private static final int IN_CLOSER = 3;

    private final PresidioSurrogates surrogates;
    private final Set<String> aliases;

    private int produced;
    private int unresolved;

    PresidioDeanonymizer(
        PresidioSurrogates surrogates,
        Set<String> aliases)
    {
        this.surrogates = surrogates;
        this.aliases = aliases;
    }

    @Override
    public Result deanonymize(
        long traceId,
        long bindingId,
        long scopeId,
        DirectBuffer data,
        int index,
        int length,
        Completion completion,
        ValueConsumerEx next,
        Result result)
    {
        final int limit = index + length;

        produced = 0;
        unresolved = 0;

        int pending = index;
        int cursor = index;
        int resume = index;
        Status status = null;

        while (status == null)
        {
            final int candidate = lead(data, cursor, limit);
            final int end = candidate < 0 ? NOT_TOKEN : scan(data, candidate, limit);

            if (candidate < 0)
            {
                resume = pending + write(next, data, pending, limit);
                status = resume < limit ? Status.OVERFLOW : settled(completion);
            }
            else if (end == NOT_TOKEN)
            {
                cursor = candidate + 1;
            }
            else if (end == PARTIAL && completion == Completion.INCOMPLETE)
            {
                resume = pending + write(next, data, pending, candidate);
                status = resume < candidate ? Status.OVERFLOW : Status.UNDERFLOW;
            }
            else if (end == PARTIAL)
            {
                resume = pending + write(next, data, pending, limit);
                status = resume < limit ? Status.OVERFLOW : Status.COMPLETE;
                countConsumed(candidate, resume, candidate + OPENER.length <= limit);
            }
            else
            {
                final PresidioSurrogate surrogate = resolve(scopeId, data, candidate, end);
                final int written = write(next, data, pending, candidate);

                if (pending + written < candidate)
                {
                    resume = pending + written;
                    status = Status.OVERFLOW;
                }
                else if (surrogate == null)
                {
                    final int passed = write(next, data, candidate, end);

                    countConsumed(candidate, candidate + passed, true);

                    if (passed < end - candidate)
                    {
                        resume = candidate + passed;
                        status = Status.OVERFLOW;
                    }
                    else
                    {
                        pending = end;
                        cursor = end;
                    }
                }
                else if (substitute(next, surrogate))
                {
                    pending = end;
                    cursor = end;
                }
                else
                {
                    resume = candidate;
                    status = Status.OVERFLOW;
                }
            }
        }

        return result.set(status, resume - index, produced, unresolved);
    }

    private static Status settled(
        Completion completion)
    {
        return completion == Completion.COMPLETE ? Status.COMPLETE : Status.UNDERFLOW;
    }

    private void countConsumed(
        int start,
        int resume,
        boolean recognized)
    {
        if (recognized && resume > start)
        {
            unresolved++;
        }
    }

    private boolean substitute(
        ValueConsumerEx next,
        PresidioSurrogate surrogate)
    {
        final int length = surrogate.originalBuffer.capacity();
        final boolean accepted = next.writeSpan(surrogate.alias, surrogate.originalBuffer, 0, length);

        if (accepted)
        {
            produced += length;
        }

        return accepted;
    }

    private int write(
        ValueConsumerEx next,
        DirectBuffer data,
        int from,
        int to)
    {
        final int length = to - from;
        final int written = length > 0 ? next.write(data, from, length) : 0;

        produced += written;

        return written;
    }

    private PresidioSurrogate resolve(
        long scopeId,
        DirectBuffer data,
        int start,
        int end)
    {
        PresidioSurrogate surrogate = surrogates.lookup(scopeId, data.getStringWithoutLengthUtf8(start, end - start));

        return surrogate != null && aliases.contains(surrogate.alias) ? surrogate : null;
    }

    private static int lead(
        DirectBuffer data,
        int from,
        int limit)
    {
        int position = from;

        while (position < limit && data.getByte(position) != LEAD)
        {
            position++;
        }

        return position < limit ? position : -1;
    }

    private static int scan(
        DirectBuffer data,
        int start,
        int limit)
    {
        int result = PENDING;
        int offset = start;
        int state = IN_OPENER;
        int matched = 0;
        int ordinal = -1;

        while (result == PENDING)
        {
            if (offset >= limit)
            {
                result = PARTIAL;
            }
            else if (offset - start >= MAX_TOKEN)
            {
                result = NOT_TOKEN;
            }
            else
            {
                final int c = data.getByte(offset) & 0xFF;

                if (state == IN_OPENER)
                {
                    if (c == (OPENER[matched] & 0xFF))
                    {
                        matched++;

                        if (matched == OPENER.length)
                        {
                            state = IN_LABEL;
                            matched = 0;
                        }
                    }
                    else
                    {
                        result = NOT_TOKEN;
                    }
                }
                else if (state == IN_LABEL)
                {
                    if (c >= 'A' && c <= 'Z')
                    {
                        ordinal = -1;
                        matched++;
                    }
                    else if (matched > 0 && c == '_')
                    {
                        ordinal = 0;
                        matched++;
                    }
                    else if (matched > 0 && c >= '0' && c <= '9')
                    {
                        ordinal = ordinal >= 0 ? ordinal + 1 : -1;
                        matched++;
                    }
                    else if (matched > 0 && c == ':' && ordinal > 0)
                    {
                        state = IN_NONCE;
                        matched = 0;
                    }
                    else
                    {
                        result = NOT_TOKEN;
                    }
                }
                else if (state == IN_NONCE)
                {
                    if (c >= '0' && c <= '9' || c >= 'a' && c <= 'f')
                    {
                        matched++;

                        if (matched == HEX_DIGITS)
                        {
                            state = IN_CLOSER;
                            matched = 0;
                        }
                    }
                    else
                    {
                        result = NOT_TOKEN;
                    }
                }
                else if (c == (CLOSER[matched] & 0xFF))
                {
                    matched++;

                    if (matched == CLOSER.length)
                    {
                        result = offset + 1;
                    }
                }
                else
                {
                    result = NOT_TOKEN;
                }

                offset++;
            }
        }

        return result;
    }
}
