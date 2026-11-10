/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaSkip.SKIP_MANY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaValueMatchFW.KIND_SKIP;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaValueMatchFW.KIND_VALUE;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaConditionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeadersFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaValueFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaValueMatchFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.message.MessageHeaderFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

final class KafkaRecordFilter
{
    private final Array32FW<KafkaFilterFW> filters;
    private final KafkaFilterFW filterRO;
    private final KafkaConditionFW conditionRO;
    private final KafkaValueMatchFW valueMatchRO;
    private final MessageHeaderFW messageHeaderRO;

    KafkaRecordFilter(
        Array32FW<KafkaFilterFW> filters)
    {
        final MutableDirectBufferEx copy = new UnsafeBufferEx(new byte[filters.sizeof()]);
        copy.putBytes(0, filters.buffer(), filters.offset(), filters.sizeof());

        this.filters = new Array32FW<>(new KafkaFilterFW()).wrap(copy, 0, copy.capacity());
        this.filterRO = new KafkaFilterFW();
        this.conditionRO = new KafkaConditionFW();
        this.valueMatchRO = new KafkaValueMatchFW();
        this.messageHeaderRO = new MessageHeaderFW();
    }

    boolean test(
        OctetsFW key,
        DirectBufferEx headers,
        int headerCount)
    {
        boolean matched = filters.isEmpty();

        final DirectBufferEx items = filters.items();
        final int limit = items.capacity();

        int offset = 0;
        while (!matched && offset < limit)
        {
            final KafkaFilterFW filter = filterRO.wrap(items, offset, limit);
            offset = filter.limit();

            matched = testConditions(filter, key, headers, headerCount);
        }

        return matched;
    }

    private boolean testConditions(
        KafkaFilterFW filter,
        OctetsFW key,
        DirectBufferEx headers,
        int headerCount)
    {
        boolean matched = true;

        final Array32FW<KafkaConditionFW> conditions = filter.conditions();
        final DirectBufferEx items = conditions.items();
        final int limit = items.capacity();

        int offset = 0;
        while (matched && offset < limit)
        {
            final KafkaConditionFW item = conditionRO.wrap(items, offset, limit);
            offset = item.limit();

            matched = testCondition(item, key, headers, headerCount);
        }

        return matched;
    }

    private boolean testCondition(
        KafkaConditionFW condition,
        OctetsFW key,
        DirectBufferEx headers,
        int headerCount)
    {
        final boolean matched;

        switch (condition.kind())
        {
        case KafkaConditionFW.KIND_KEY:
            matched = testKey(condition.key(), key);
            break;
        case KafkaConditionFW.KIND_HEADER:
            matched = testHeader(condition.header(), headers, headerCount);
            break;
        case KafkaConditionFW.KIND_NOT:
            matched = !testCondition(condition.not().condition(), key, headers, headerCount);
            break;
        case KafkaConditionFW.KIND_HEADERS:
            matched = testHeaders(condition.headers(), headers, headerCount);
            break;
        default:
            matched = false;
            break;
        }

        return matched;
    }

    private boolean testKey(
        KafkaKeyFW expected,
        OctetsFW key)
    {
        return equalsOctets(expected.value(), key);
    }

    private boolean testHeader(
        KafkaHeaderFW expected,
        DirectBufferEx headers,
        int headerCount)
    {
        boolean matched = false;

        final int limit = headers.capacity();

        int offset = 0;
        for (int index = 0; !matched && index < headerCount; index++)
        {
            final MessageHeaderFW header = messageHeaderRO.wrap(headers, offset, limit);
            offset = header.limit();

            matched = equalsOctets(expected.name(), header.key()) && equalsOctets(expected.value(), header.value());
        }

        return matched;
    }

    private boolean testHeaders(
        KafkaHeadersFW expected,
        DirectBufferEx headers,
        int headerCount)
    {
        final OctetsFW name = expected.name();
        final DirectBufferEx matchItems = expected.values().items();
        final int matchesLimit = matchItems.capacity();

        final int headersLimit = headers.capacity();

        int matchOffset = 0;
        boolean matched = true;
        boolean skipManyHeaders = false;

        int headerOffset = 0;
        for (int index = 0; matched && index < headerCount; index++)
        {
            final MessageHeaderFW header = messageHeaderRO.wrap(headers, headerOffset, headersLimit);
            headerOffset = header.limit();

            if (equalsOctets(name, header.key()))
            {
                if (matchOffset < matchesLimit)
                {
                    final KafkaValueMatchFW valueMatch = valueMatchRO.wrap(matchItems, matchOffset, matchesLimit);
                    matchOffset = valueMatch.limit();

                    switch (valueMatch.kind())
                    {
                    case KIND_VALUE:
                        final KafkaValueFW value = valueMatch.value();
                        matched = equalsOctets(value.value(), header.value());
                        break;
                    case KIND_SKIP:
                        skipManyHeaders = valueMatch.skip().get() == SKIP_MANY;
                        break;
                    default:
                        break;
                    }
                }
                else
                {
                    matched = skipManyHeaders;
                }
            }
        }

        return matched && matchOffset == matchesLimit;
    }

    private static boolean equalsOctets(
        OctetsFW expected,
        OctetsFW actual)
    {
        return expected == null ? actual == null : actual != null && expected.equals(actual);
    }
}
