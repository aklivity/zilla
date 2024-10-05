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
package io.aklivity.zilla.runtime.binding.risingwave.internal.config;

import java.util.Arrays;

public enum RisingwaveCommandType
{
    CREATE_TOPIC_COMMAND("CREATE TOPIC".getBytes()),
    CREATE_TABLE_COMMAND("CREATE TABLE".getBytes()),
    CREATE_STREAM_COMMAND("CREATE STREAM".getBytes()),
    CREATE_MATERIALIZED_VIEW_COMMAND("CREATE MATERIALIZED VIEW".getBytes()),
    CREATE_FUNCTION_COMMAND("CREATE FUNCTION".getBytes()),
    DROP_TABLE_COMMAND("DROP TABLE".getBytes()),
    DROP_STREAM_COMMAND("DROP STREAM".getBytes()),
    UNKNOWN_COMMAND("UNKNOWN".getBytes());

    private final byte[] value;

    RisingwaveCommandType(byte[] value)
    {
        this.value = value;
    }

    public byte[] value()
    {
        return value;
    }

    public static RisingwaveCommandType valueOf(
        byte[] value)
    {
        RisingwaveCommandType command = UNKNOWN_COMMAND;

        command:
        for (RisingwaveCommandType commandType : RisingwaveCommandType.values())
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
