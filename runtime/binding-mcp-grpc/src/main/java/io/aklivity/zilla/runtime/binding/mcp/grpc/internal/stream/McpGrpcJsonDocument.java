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
package io.aklivity.zilla.runtime.binding.mcp.grpc.internal.stream;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

final class McpGrpcJsonDocument
{
    private int depth;
    private boolean started;
    private boolean inString;
    private boolean escaped;
    private boolean complete;

    boolean advance(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        for (int index = offset; index < limit && !complete; index++)
        {
            final byte value = buffer.getByte(index);

            if (inString)
            {
                if (escaped)
                {
                    escaped = false;
                }
                else if (value == '\\')
                {
                    escaped = true;
                }
                else if (value == '"')
                {
                    inString = false;
                }
            }
            else if (value == '"')
            {
                inString = true;
            }
            else if (value == '{' || value == '[')
            {
                started = true;
                depth++;
            }
            else if ((value == '}' || value == ']') && started)
            {
                depth--;
                complete = depth == 0;
            }
        }

        return complete;
    }

    void reset()
    {
        depth = 0;
        started = false;
        inString = false;
        escaped = false;
        complete = false;
    }
}
