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
package io.aklivity.zilla.runtime.binding.mcp.eager;

/**
 * The write side of an eager policy's usage tracking, invoked once per {@code tools/call}
 * dispatch. Extracted as its own type so it can be held, composed, and interned as a
 * singleton ({@link #NONE}) rather than requiring every policy to hand-write a no-op method
 * body.
 */
@FunctionalInterface
public interface McpToolsEagerRecorder
{
    void record(
        long authorization,
        CharSequence tool);

    McpToolsEagerRecorder NONE = new McpToolsEagerRecorder()
    {
        @Override
        public void record(
            long authorization,
            CharSequence tool)
        {
        }

        @Override
        public McpToolsEagerRecorder andThen(
            McpToolsEagerRecorder after)
        {
            return after;
        }
    };

    default McpToolsEagerRecorder andThen(
        McpToolsEagerRecorder after)
    {
        return after == NONE ? this : (authorization, tool) ->
        {
            record(authorization, tool);
            after.record(authorization, tool);
        };
    }
}
