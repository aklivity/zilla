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
package io.aklivity.zilla.specs.binding.mcp.internal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.zilla.specs.binding.mcp.internal.types.String16FW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpAbortExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpChallengeExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpFlushExFW;

public class McpFunctionsTest
{
    @Test
    public void shouldGetPrefixName()
    {
        assertNotNull(new McpFunctions.Mapper().getPrefixName());
    }

    @Test
    public void shouldGenerateLifecycleBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .lifecycle()
                .sessionId("session-1")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchLifecycleBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .lifecycle()
                .sessionId("session-1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .lifecycle(b -> b.sessionId("session-1"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateToolsListBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .toolsList()
                .sessionId("session-1")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchToolsListBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .toolsList()
                .sessionId("session-1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .toolsList(b -> b.sessionId("session-1"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateToolsCallBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .toolsCall()
                .sessionId("session-1")
                .name("my-tool")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchToolsCallBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .toolsCall()
                .sessionId("session-1")
                .name("my-tool")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .toolsCall(b -> b
                .sessionId("session-1")
                .name("my-tool"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGeneratePromptsListBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .promptsList()
                .sessionId("session-1")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchPromptsListBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .promptsList()
                .sessionId("session-1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .promptsList(b -> b
                .sessionId("session-1"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGeneratePromptsGetBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .promptsGet()
                .sessionId("session-1")
                .name("my-prompt")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchPromptsGetBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .promptsGet()
                .sessionId("session-1")
                .name("my-prompt")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .promptsGet(b -> b
                .sessionId("session-1")
                .name("my-prompt"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateResourcesListBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .resourcesList()
                .sessionId("session-1")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchResourcesBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .resourcesList()
                .sessionId("session-1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .resourcesList(b -> b
                .sessionId("session-1"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateResourcesReadBeginEx()
    {
        byte[] bytes = McpFunctions.beginEx()
            .typeId(0)
            .resourcesRead()
                .sessionId("session-1")
                .uri("file:///data/resource.txt")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchResourcesReadBeginEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .resourcesRead()
                .sessionId("session-1")
                .uri("file:///data/resource.txt")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .resourcesRead(b -> b
                .sessionId("session-1")
                .uri("file:///data/resource.txt"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldFailWhenCaseMismatch() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchBeginEx()
            .typeId(0)
            .lifecycle()
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpBeginExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .toolsList(b -> b.sessionId("session-1"))
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

    @Test
    public void shouldGenerateAbortEx()
    {
        byte[] bytes = McpFunctions.abortEx()
            .typeId(0)
            .reason("connection reset")
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchAbortEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchAbortEx()
            .typeId(0)
            .reason("connection reset")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpAbortExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .reason("connection reset")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldReturnNullWhenAbortExMatcherIsEmpty() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchAbortEx()
            .build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test(expected = Exception.class)
    public void shouldFailWhenAbortExReasonMismatch() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchAbortEx()
            .typeId(0)
            .reason("expected reason")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpAbortExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .reason("actual reason")
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldExerciseAbortExBuilderOverloads() throws Exception
    {
        // reason(String16FW) overload
        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpAbortExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .reason(new String16FW("string-fw-reason"))
            .build();

        assertNotNull(McpFunctions.matchAbortEx()
            .typeId(0)
            .reason("string-fw-reason")
            .build()
            .match(byteBuf));

        // reason(Consumer<String16FW.Builder>) overload
        byteBuf.clear();

        new McpAbortExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .reason(b -> b.set("consumer-reason", StandardCharsets.UTF_8))
            .build();

        assertNotNull(McpFunctions.matchAbortEx()
            .typeId(0)
            .reason("consumer-reason")
            .build()
            .match(byteBuf));
    }

    @Test
    public void shouldWrapAndReadAbortEx()
    {
        ByteBuffer byteBuf = ByteBuffer.allocate(256);
        final UnsafeBuffer buffer = new UnsafeBuffer(byteBuf);

        new McpAbortExFW.Builder()
            .wrap(buffer, 0, buffer.capacity())
            .typeId(0)
            .reason("wrap-test")
            .build();

        final McpAbortExFW abortEx = new McpAbortExFW();
        abortEx.wrap(buffer, 0, buffer.capacity());

        assertNotNull(abortEx.reason());
    }

    @Test
    public void shouldCopyAbortEx()
    {
        ByteBuffer srcBuf = ByteBuffer.allocate(256);
        final UnsafeBuffer srcBuffer = new UnsafeBuffer(srcBuf);

        final McpAbortExFW source = new McpAbortExFW.Builder()
            .wrap(srcBuffer, 0, srcBuffer.capacity())
            .typeId(0)
            .reason("copy-test")
            .build();

        ByteBuffer dstBuf = ByteBuffer.allocate(256);
        final UnsafeBuffer dstBuffer = new UnsafeBuffer(dstBuf);

        final McpAbortExFW copy = new McpAbortExFW.Builder()
            .wrap(dstBuffer, 0, dstBuffer.capacity())
            .set(source)
            .build();

        assertNotNull(copy.reason());
    }

    @Test
    public void shouldGenerateResumableFlushEx()
    {
        byte[] bytes = McpFunctions.flushEx()
            .typeId(0)
            .resumable()
                .id("2:0")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchResumableFlushEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchFlushEx()
            .typeId(0)
            .resumable()
                .id("2:0")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpFlushExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .resumable(b -> b.id("2:0"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateToolsListChangedFlushEx()
    {
        byte[] bytes = McpFunctions.flushEx()
            .typeId(0)
            .toolsListChanged()
                .id("1")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchToolsListChangedFlushEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchFlushEx()
            .typeId(0)
            .toolsListChanged()
                .id("1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpFlushExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .toolsListChanged(b -> b.id("1"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGeneratePromptsListChangedFlushEx()
    {
        byte[] bytes = McpFunctions.flushEx()
            .typeId(0)
            .promptsListChanged()
                .id("1")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchPromptsListChangedFlushEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchFlushEx()
            .typeId(0)
            .promptsListChanged()
                .id("1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpFlushExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .promptsListChanged(b -> b.id("1"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateResourcesListChangedFlushEx()
    {
        byte[] bytes = McpFunctions.flushEx()
            .typeId(0)
            .resourcesListChanged()
                .id("1")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchResourcesListChangedFlushEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchFlushEx()
            .typeId(0)
            .resourcesListChanged()
                .id("1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpFlushExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .resourcesListChanged(b -> b.id("1"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateProgressFlushEx()
    {
        byte[] bytes = McpFunctions.flushEx()
            .typeId(0)
            .progress()
                .id("2:1")
                .token("abc123")
                .progress(50L)
                .total(100L)
                .message("halfway")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchProgressFlushEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchFlushEx()
            .typeId(0)
            .progress()
                .id("2:1")
                .token("abc123")
                .progress(50L)
                .total(100L)
                .message("halfway")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpFlushExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .progress(b -> b
                .id("2:1")
                .token("abc123")
                .progress(50L)
                .total(100L)
                .message("halfway"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldReturnNullWhenFlushExMatcherIsEmpty() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchFlushEx()
            .build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test
    public void shouldGenerateSuspendFlushEx()
    {
        byte[] bytes = McpFunctions.flushEx()
            .typeId(0)
            .suspend()
                .retry(60000L)
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchSuspendFlushEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchFlushEx()
            .typeId(0)
            .suspend()
                .retry(60000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpFlushExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .suspend(b -> b.retry(60000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateResumeChallengeEx()
    {
        byte[] bytes = McpFunctions.challengeEx()
            .typeId(0)
            .resume()
                .id("2:0")
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldGenerateResumeChallengeExWithoutId()
    {
        byte[] bytes = McpFunctions.challengeEx()
            .typeId(0)
            .resume()
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchResumeChallengeEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchChallengeEx()
            .typeId(0)
            .resume()
                .id("2:0")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpChallengeExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .resume(b -> b.id("2:0"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateSuspendedChallengeEx()
    {
        byte[] bytes = McpFunctions.challengeEx()
            .typeId(0)
            .suspended()
                .build()
            .build();

        assertNotNull(bytes);
    }

    @Test
    public void shouldMatchSuspendedChallengeEx() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchChallengeEx()
            .typeId(0)
            .suspended()
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new McpChallengeExFW.Builder()
            .wrap(new UnsafeBuffer(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .suspended(b ->
            {
            })
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldReturnNullWhenChallengeExMatcherIsEmpty() throws Exception
    {
        BytesMatcher matcher = McpFunctions.matchChallengeEx()
            .build();

        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }
}
