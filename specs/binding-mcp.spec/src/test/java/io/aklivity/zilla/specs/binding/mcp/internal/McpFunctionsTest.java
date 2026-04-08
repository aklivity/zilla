/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.mcp.internal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpFlushExFW;

public class McpFunctionsTest
{
    @Test
    public void shouldGetPrefixName()
    {
        assertNotNull(new McpFunctions.Mapper().getPrefixName());
    }

    @Test
    public void shouldGenerateInitializeBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .initialize()
                .version("2025-11-25")
                .capabilities(0x07)
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchInitializeBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .initialize()
                .version("2025-11-25")
                .capabilities(0x07)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .initialize(b -> b.sessionId((String) null)
                              .version("2025-11-25")
                              .capabilities(0x07))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateToolBeginExForList()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .tool()
                .sessionId("test-session-id")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateToolBeginExForCall()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .tool()
                .sessionId("test-session-id")
                .name("my-tool")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchToolBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .tool()
                .name("my-tool")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .tool(b -> b.sessionId("test-session-id")
                        .name("my-tool"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGeneratePromptBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .prompt()
                .sessionId("test-session-id")
                .name("my-prompt")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchPromptBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .prompt()
                .name("my-prompt")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .prompt(b -> b.sessionId("test-session-id")
                          .name("my-prompt"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateResourceBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .resource()
                .sessionId("test-session-id")
                .uri("file:///data/resource.txt")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchResourceBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .resource()
                .uri("file:///data/resource.txt")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .resource(b -> b.sessionId("test-session-id")
                            .uri("file:///data/resource.txt"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateLoggingBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .logging()
                .sessionId("test-session-id")
                .level("error")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchLoggingBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .logging()
                .level("error")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .logging(b -> b.sessionId("test-session-id")
                           .level("error"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGeneratePingBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .ping()
                .sessionId("test-session-id")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchPingBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .ping()
                .sessionId("test-session-id")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .ping(b -> b.sessionId("test-session-id"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateCancelBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .cancel()
                .sessionId("test-session-id")
                .reason("User cancelled")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchCancelBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .cancel()
                .reason("User cancelled")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .cancel(b -> b.sessionId("test-session-id")
                          .reason("User cancelled"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateCompletionBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .completion()
                .sessionId("test-session-id")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchCompletionBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .completion()
                .sessionId("test-session-id")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .completion(b -> b.sessionId("test-session-id"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateDisconnectBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .disconnect()
                .sessionId("test-session-id")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchDisconnectBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .disconnect()
                .sessionId("test-session-id")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .disconnect(b -> b.sessionId("test-session-id"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateFlushEx()
    {
        byte[] bytes = McpFunctions.flushEx()
            .typeId(0)
            .sessionId("test-session-id")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchFlushEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchFlushEx()
            .typeId(0)
            .sessionId("test-session-id")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpFlushExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .sessionId("test-session-id")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateInitializeBeginExWithSessionId()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .initialize()
                .sessionId("test-session-id")
                .version("2025-11-25")
                .capabilities(0x07)
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchInitializeBeginExBySessionId() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .initialize()
                .sessionId("test-session-id")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .initialize(b -> b.sessionId("test-session-id")
                              .version((String) null)
                              .capabilities(0))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchToolBeginExBySessionId() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .tool()
                .sessionId("test-session-id")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .tool(b -> b.sessionId("test-session-id")
                        .name((String) null))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchPromptBeginExBySessionId() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .prompt()
                .sessionId("test-session-id")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .prompt(b -> b.sessionId("test-session-id")
                          .name((String) null))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchResourceBeginExBySessionId() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .resource()
                .sessionId("test-session-id")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .resource(b -> b.sessionId("test-session-id")
                            .uri((String) null))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchLoggingBeginExBySessionId() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .logging()
                .sessionId("test-session-id")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .logging(b -> b.sessionId("test-session-id")
                           .level((String) null))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchCancelBeginExBySessionId() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .cancel()
                .sessionId("test-session-id")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .cancel(b -> b.sessionId("test-session-id")
                          .reason((String) null))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFlushExByTypeIdOnly() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchFlushEx()
            .typeId(0)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpFlushExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .sessionId("test-session-id")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldMatchFlushExBySessionIdOnly() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchFlushEx()
            .sessionId("test-session-id")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpFlushExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .sessionId("test-session-id")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldReturnNullWhenBeginExMatcherIsEmpty() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .initialize(b -> b.sessionId((String) null)
                              .version("2025-11-25")
                              .capabilities(0x07))
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldReturnNullWhenFlushExMatcherIsEmpty() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchFlushEx()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpFlushExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .sessionId("test-session-id")
            .build();

        assertNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldFailWhenVersionMismatch() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .initialize()
                .version("2024-11-25")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .initialize(b -> b.sessionId((String) null)
                              .version("2025-11-25")
                              .capabilities(0x07))
            .build();

        matcher.match(byteBuf);
    }

    @Test(expected = Exception.class)
    public void shouldFailWhenCaseMismatch() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .initialize()
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .tool(b -> b.sessionId("test-session-id")
                        .name("my-tool"))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldReturnNullWhenBufferIsEmpty() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }
}
