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

import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaValueMatchFW.KIND_VALUE;

import org.agrona.collections.IntArrayList;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaConditionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeadersFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaValueMatchFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

final class KafkaFilterTerms
{
    private static final int KEY_SEED = 0x9E3779B9;

    private final KafkaFilterFW filterRO = new KafkaFilterFW();
    private final KafkaConditionFW conditionRO = new KafkaConditionFW();
    private final KafkaValueMatchFW valueMatchRO = new KafkaValueMatchFW();

    boolean collect(
        ArrayFW<KafkaFilterFW> filters,
        IntArrayList terms)
    {
        terms.clear();

        boolean indexed = !filters.isEmpty();

        final DirectBufferEx items = filters.items();
        final int limit = items.capacity();

        int offset = 0;
        while (indexed && offset < limit)
        {
            final KafkaFilterFW filter = filterRO.wrap(items, offset, limit);
            offset = filter.limit();

            indexed = collectFilter(filter, terms);
        }

        if (!indexed)
        {
            terms.clear();
        }

        return indexed;
    }

    static int hashHeader(
        OctetsFW name,
        OctetsFW value)
    {
        int hash = hashBytes(name.sizeof(), name);
        hash = 31 * hash + (value != null ? value.sizeof() : -1);
        return hashBytes(hash, value);
    }

    static int hashKey(
        OctetsFW key)
    {
        final int hash = 31 * KEY_SEED + (key != null ? key.sizeof() : -1);
        return hashBytes(hash, key);
    }

    private boolean collectFilter(
        KafkaFilterFW filter,
        IntArrayList terms)
    {
        boolean found = false;

        final Array32FW<KafkaConditionFW> conditions = filter.conditions();
        final DirectBufferEx items = conditions.items();
        final int limit = items.capacity();

        int offset = 0;
        while (!found && offset < limit)
        {
            final KafkaConditionFW condition = conditionRO.wrap(items, offset, limit);
            offset = condition.limit();

            found = collectCondition(condition, terms);
        }

        return found;
    }

    private boolean collectCondition(
        KafkaConditionFW condition,
        IntArrayList terms)
    {
        final boolean found;

        switch (condition.kind())
        {
        case KafkaConditionFW.KIND_KEY:
            terms.addInt(hashKey(condition.key().value()));
            found = true;
            break;
        case KafkaConditionFW.KIND_HEADER:
            final KafkaHeaderFW header = condition.header();
            terms.addInt(hashHeader(header.name(), header.value()));
            found = true;
            break;
        case KafkaConditionFW.KIND_HEADERS:
            found = collectHeaders(condition.headers(), terms);
            break;
        default:
            found = false;
            break;
        }

        return found;
    }

    private boolean collectHeaders(
        KafkaHeadersFW headers,
        IntArrayList terms)
    {
        final OctetsFW name = headers.name();
        final DirectBufferEx items = headers.values().items();
        final int limit = items.capacity();

        boolean found = false;
        int lastLiteral = 0;

        int offset = 0;
        while (offset < limit)
        {
            final KafkaValueMatchFW valueMatch = valueMatchRO.wrap(items, offset, limit);
            offset = valueMatch.limit();

            if (valueMatch.kind() == KIND_VALUE)
            {
                lastLiteral = hashHeader(name, valueMatch.value().value());
                found = true;
            }
        }

        if (found)
        {
            terms.addInt(lastLiteral);
        }

        return found;
    }

    private static int hashBytes(
        int hash,
        OctetsFW octets)
    {
        int result = hash;

        if (octets != null)
        {
            final DirectBufferEx buffer = octets.buffer();
            final int offset = octets.offset();
            final int limit = octets.limit();

            for (int index = offset; index < limit; index++)
            {
                result = 31 * result + buffer.getByte(index);
            }
        }

        return result;
    }
}
