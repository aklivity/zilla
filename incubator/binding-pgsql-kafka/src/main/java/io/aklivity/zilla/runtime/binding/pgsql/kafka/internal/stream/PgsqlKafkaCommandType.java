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

public enum PgsqlKafkaCommandType
{
    CREATE_TOPIC_COMMAND("CREATE TOPIC".getBytes()),
    UNKNOWN_COMMAND("UNKNOWN".getBytes());

    private final byte[] value;

    PgsqlKafkaCommandType(byte[] value)
    {
        this.value = value;
    }

    public byte[] value()
    {
        return value;
    }

    public static PgsqlKafkaCommandType valueOf(
        byte[] value)
    {
        PgsqlKafkaCommandType command = UNKNOWN_COMMAND;

        command:
        for (PgsqlKafkaCommandType commandType : PgsqlKafkaCommandType.values())
        {
            if (Arrays.equals(commandType.value, value))
            {
                command = commandType;
                break command;
            }
        }

        return command;
    }
}
