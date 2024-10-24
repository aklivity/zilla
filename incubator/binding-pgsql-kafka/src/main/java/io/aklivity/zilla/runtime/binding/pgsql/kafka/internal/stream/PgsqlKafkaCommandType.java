/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.pgsql.kafka.internal.stream;

import java.util.Arrays;
import java.util.Map;

import org.agrona.collections.Object2ObjectHashMap;

public enum PgsqlKafkaCommandType
{
    CREATE_TOPIC_COMMAND("CREATE TOPIC".getBytes()),
    ALTER_TOPIC_COMMAND("ALTER TOPIC".getBytes()),
    DROP_TOPIC_COMMAND("DROP TOPIC".getBytes()),
    UNKNOWN_COMMAND("UNKNOWN".getBytes());

    private final byte[] value;

    private static final Map<String, PgsqlKafkaCommandType> COMMAND_MAP = new Object2ObjectHashMap<>();

    static
    {
        for (PgsqlKafkaCommandType commandType : PgsqlKafkaCommandType.values())
        {
            COMMAND_MAP.put(Arrays.toString(commandType.value), commandType);
        }
    }

    PgsqlKafkaCommandType(byte[] value)
    {
        this.value = value;
    }

    public byte[] value()
    {
        return value;
    }

    public static PgsqlKafkaCommandType valueOf(byte[] value)
    {
        return COMMAND_MAP.getOrDefault(Arrays.toString(value), UNKNOWN_COMMAND);
    }
}
