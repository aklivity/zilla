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
 * One cached tool, fed to {@link McpToolsEager#index} on every catalog rebuild.
 * <p>
 * {@code toolsBytes} is the shared backing array for the whole cached {@code tools/list}
 * response -- the same reference across every document from one rebuild -- with
 * {@code offset}/{@code length} bounding this tool's own whole-object byte range within it.
 * </p>
 */
public final class McpToolEagerDocument
{
    public final String name;

    private final byte[] toolsBytes;
    private final int offset;
    private final int length;

    public McpToolEagerDocument(
        String name,
        byte[] toolsBytes,
        int offset,
        int length)
    {
        this.name = name;
        this.toolsBytes = toolsBytes;
        this.offset = offset;
        this.length = length;
    }

    public int length()
    {
        return length;
    }

    public byte[] toolsBytes()
    {
        return toolsBytes;
    }

    public int offset()
    {
        return offset;
    }
}
