/*
 * Copyright 2021-2024 Aklivity Inc
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

public enum PgsqlKafkaCompletionCommand
{
    CREATE_TOPIC_COMMAND("CREATE_TOPIC".getBytes()),
    ALTER_TOPIC_COMMAND("ALTER_TOPIC".getBytes()),
    DROP_TOPIC_COMMAND("DROP_TOPIC".getBytes()),
    UNKNOWN_COMMAND("UNKNOWN".getBytes());

    private final byte[] value;

    PgsqlKafkaCompletionCommand(byte[] value)
    {
        this.value = value;
    }

    public byte[] value()
    {
        return value;
    }
}
