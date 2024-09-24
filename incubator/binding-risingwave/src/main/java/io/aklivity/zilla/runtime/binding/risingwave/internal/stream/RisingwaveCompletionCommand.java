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
package io.aklivity.zilla.runtime.binding.risingwave.internal.stream;

public enum RisingwaveCompletionCommand
{
    UNKNOWN_COMMAND("UNKNOWN".getBytes()),
    CREATE_TABLE_COMMAND("CREATE_TABLE".getBytes()),
    DROP_TABLE_COMMAND("DROP_TABLE".getBytes()),
    CREATE_MATERIALIZED_VIEW_COMMAND("CREATE_MATERIALIZED_VIEW".getBytes()),
    CREATE_FUNCTION_COMMAND("CREATE_FUNCTION".getBytes());

    private final byte[] value;

    RisingwaveCompletionCommand(byte[] value)
    {
        this.value = value;
    }

    public byte[] value()
    {
        return value;
    }
}
