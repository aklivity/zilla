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
package io.aklivity.zilla.specs.binding.a2a.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;

import org.junit.Test;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.a2a.internal.types.A2aTaskState;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aAbortExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aBearerError;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aEndExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aOutcome;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aResetExFW;

public class A2aFunctionsTest
{
    @Test
    public void shouldGetPrefixName()
    {
        assertNotNull(new A2aFunctions.Mapper().getPrefixName());
    }

    @Test
    public void shouldGenerateAgentCardBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .agentCard()
                .tenant("tenant-1")
                .version("version-1")
                .etag("etag-1")
                .maxAge(30000L)
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.agentCard().tenant().asString());
        assertEquals("version-1", beginEx.agentCard().version().asString());
        assertEquals("etag-1", beginEx.agentCard().etag().asString());
        assertEquals(30000L, beginEx.agentCard().maxAge());
        assertEquals(30000L, beginEx.agentCard().timeout());
    }

    @Test
    public void shouldMatchAgentCardBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .agentCard()
                .tenant("tenant-1")
                .version("version-1")
                .etag("etag-1")
                .maxAge(30000L)
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .agentCard(b -> b.tenant("tenant-1").version("version-1").etag("etag-1").maxAge(30000L).timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAgentCardBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .agentCard()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .agentCard(b -> b.tenant("tenant-1").version("version-1").etag("etag-1").maxAge(30000L).timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateMessageSendBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .messageSend()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contentLength(7)
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.messageSend().tenant().asString());
        assertEquals("version-1", beginEx.messageSend().version().asString());
        assertEquals("extensions-1", beginEx.messageSend().extensions().asString());
        assertEquals(7, beginEx.messageSend().contentLength());
        assertEquals(30000L, beginEx.messageSend().timeout());
    }

    @Test
    public void shouldMatchMessageSendBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .messageSend()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contentLength(7)
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .messageSend(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contentLength(7)
                .timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMessageSendBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .messageSend()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .messageSend(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contentLength(7)
                .timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateMessageStreamBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .messageStream()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contentLength(7)
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.messageStream().tenant().asString());
        assertEquals("version-1", beginEx.messageStream().version().asString());
        assertEquals("extensions-1", beginEx.messageStream().extensions().asString());
        assertEquals(7, beginEx.messageStream().contentLength());
        assertEquals(30000L, beginEx.messageStream().timeout());
    }

    @Test
    public void shouldMatchMessageStreamBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .messageStream()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contentLength(7)
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .messageStream(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contentLength(7)
                .timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMessageStreamBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .messageStream()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .messageStream(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contentLength(7)
                .timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateTasksGetBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .tasksGet()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .historyLength(7)
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.tasksGet().tenant().asString());
        assertEquals("version-1", beginEx.tasksGet().version().asString());
        assertEquals("extensions-1", beginEx.tasksGet().extensions().asString());
        assertEquals("taskId-1", beginEx.tasksGet().taskId().asString());
        assertEquals(7, beginEx.tasksGet().historyLength());
        assertEquals(30000L, beginEx.tasksGet().timeout());
    }

    @Test
    public void shouldMatchTasksGetBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .tasksGet()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .historyLength(7)
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .tasksGet(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .historyLength(7)
                .timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchTasksGetBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .tasksGet()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .tasksGet(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .historyLength(7)
                .timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateTasksListBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .tasksList()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contextId("contextId-1")
                .contentLength(7)
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.tasksList().tenant().asString());
        assertEquals("version-1", beginEx.tasksList().version().asString());
        assertEquals("extensions-1", beginEx.tasksList().extensions().asString());
        assertEquals("contextId-1", beginEx.tasksList().contextId().asString());
        assertEquals(7, beginEx.tasksList().contentLength());
        assertEquals(30000L, beginEx.tasksList().timeout());
    }

    @Test
    public void shouldMatchTasksListBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .tasksList()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contextId("contextId-1")
                .contentLength(7)
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .tasksList(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contextId("contextId-1")
                .contentLength(7)
                .timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchTasksListBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .tasksList()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .tasksList(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .contextId("contextId-1")
                .contentLength(7)
                .timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateTasksCancelBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .tasksCancel()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.tasksCancel().tenant().asString());
        assertEquals("version-1", beginEx.tasksCancel().version().asString());
        assertEquals("extensions-1", beginEx.tasksCancel().extensions().asString());
        assertEquals("taskId-1", beginEx.tasksCancel().taskId().asString());
        assertEquals(30000L, beginEx.tasksCancel().timeout());
    }

    @Test
    public void shouldMatchTasksCancelBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .tasksCancel()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .tasksCancel(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchTasksCancelBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .tasksCancel()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .tasksCancel(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateTasksSubscribeBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .tasksSubscribe()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.tasksSubscribe().tenant().asString());
        assertEquals("version-1", beginEx.tasksSubscribe().version().asString());
        assertEquals("extensions-1", beginEx.tasksSubscribe().extensions().asString());
        assertEquals("taskId-1", beginEx.tasksSubscribe().taskId().asString());
        assertEquals(30000L, beginEx.tasksSubscribe().timeout());
    }

    @Test
    public void shouldMatchTasksSubscribeBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .tasksSubscribe()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .tasksSubscribe(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchTasksSubscribeBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .tasksSubscribe()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .tasksSubscribe(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGeneratePushConfigCreateBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .pushConfigCreate()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .contentLength(7)
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.pushConfigCreate().tenant().asString());
        assertEquals("version-1", beginEx.pushConfigCreate().version().asString());
        assertEquals("extensions-1", beginEx.pushConfigCreate().extensions().asString());
        assertEquals("taskId-1", beginEx.pushConfigCreate().taskId().asString());
        assertEquals(7, beginEx.pushConfigCreate().contentLength());
        assertEquals(30000L, beginEx.pushConfigCreate().timeout());
    }

    @Test
    public void shouldMatchPushConfigCreateBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .pushConfigCreate()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .contentLength(7)
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .pushConfigCreate(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .contentLength(7)
                .timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchPushConfigCreateBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .pushConfigCreate()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .pushConfigCreate(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .contentLength(7)
                .timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGeneratePushConfigGetBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .pushConfigGet()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .configId("configId-1")
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.pushConfigGet().tenant().asString());
        assertEquals("version-1", beginEx.pushConfigGet().version().asString());
        assertEquals("extensions-1", beginEx.pushConfigGet().extensions().asString());
        assertEquals("taskId-1", beginEx.pushConfigGet().taskId().asString());
        assertEquals("configId-1", beginEx.pushConfigGet().configId().asString());
        assertEquals(30000L, beginEx.pushConfigGet().timeout());
    }

    @Test
    public void shouldMatchPushConfigGetBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .pushConfigGet()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .configId("configId-1")
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .pushConfigGet(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .configId("configId-1")
                .timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchPushConfigGetBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .pushConfigGet()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .pushConfigGet(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .configId("configId-1")
                .timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGeneratePushConfigListBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .pushConfigList()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.pushConfigList().tenant().asString());
        assertEquals("version-1", beginEx.pushConfigList().version().asString());
        assertEquals("extensions-1", beginEx.pushConfigList().extensions().asString());
        assertEquals("taskId-1", beginEx.pushConfigList().taskId().asString());
        assertEquals(30000L, beginEx.pushConfigList().timeout());
    }

    @Test
    public void shouldMatchPushConfigListBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .pushConfigList()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .pushConfigList(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchPushConfigListBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .pushConfigList()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .pushConfigList(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGeneratePushConfigDeleteBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .pushConfigDelete()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .configId("configId-1")
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.pushConfigDelete().tenant().asString());
        assertEquals("version-1", beginEx.pushConfigDelete().version().asString());
        assertEquals("extensions-1", beginEx.pushConfigDelete().extensions().asString());
        assertEquals("taskId-1", beginEx.pushConfigDelete().taskId().asString());
        assertEquals("configId-1", beginEx.pushConfigDelete().configId().asString());
        assertEquals(30000L, beginEx.pushConfigDelete().timeout());
    }

    @Test
    public void shouldMatchPushConfigDeleteBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .pushConfigDelete()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .configId("configId-1")
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .pushConfigDelete(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .configId("configId-1")
                .timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchPushConfigDeleteBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .pushConfigDelete()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .pushConfigDelete(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .taskId("taskId-1")
                .configId("configId-1")
                .timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateExtendedAgentCardBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .extendedAgentCard()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.extendedAgentCard().tenant().asString());
        assertEquals("version-1", beginEx.extendedAgentCard().version().asString());
        assertEquals("extensions-1", beginEx.extendedAgentCard().extensions().asString());
        assertEquals(30000L, beginEx.extendedAgentCard().timeout());
    }

    @Test
    public void shouldMatchExtendedAgentCardBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .extendedAgentCard()
                .tenant("tenant-1")
                .version("version-1")
                .extensions("extensions-1")
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .extendedAgentCard(b -> b.tenant("tenant-1").version("version-1").extensions("extensions-1").timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchExtendedAgentCardBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .extendedAgentCard()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .extendedAgentCard(b -> b.tenant("tenant-1").version("version-1").extensions("extensions-1").timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGeneratePushNotifyBeginEx()
    {
        byte[] bytes = A2aFunctions.beginEx()
            .typeId(0)
            .pushNotify()
                .tenant("tenant-1")
                .version("version-1")
                .configId("configId-1")
                .taskId("taskId-1")
                .url("url-1")
                .token("token-1")
                .scheme("scheme-1")
                .credentials("credentials-1")
                .contentLength(7)
                .timeout(30000L)
                .build()
            .build();

        assertNotNull(bytes);

        A2aBeginExFW beginEx = new A2aBeginExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("tenant-1", beginEx.pushNotify().tenant().asString());
        assertEquals("version-1", beginEx.pushNotify().version().asString());
        assertEquals("configId-1", beginEx.pushNotify().configId().asString());
        assertEquals("taskId-1", beginEx.pushNotify().taskId().asString());
        assertEquals("url-1", beginEx.pushNotify().url().asString());
        assertEquals("token-1", beginEx.pushNotify().token().asString());
        assertEquals("scheme-1", beginEx.pushNotify().scheme().asString());
        assertEquals("credentials-1", beginEx.pushNotify().credentials().asString());
        assertEquals(7, beginEx.pushNotify().contentLength());
        assertEquals(30000L, beginEx.pushNotify().timeout());
    }

    @Test
    public void shouldMatchPushNotifyBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .pushNotify()
                .tenant("tenant-1")
                .version("version-1")
                .configId("configId-1")
                .taskId("taskId-1")
                .url("url-1")
                .token("token-1")
                .scheme("scheme-1")
                .credentials("credentials-1")
                .contentLength(7)
                .timeout(30000L)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .pushNotify(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .configId("configId-1")
                .taskId("taskId-1")
                .url("url-1")
                .token("token-1")
                .scheme("scheme-1")
                .credentials("credentials-1")
                .contentLength(7)
                .timeout(30000L))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchPushNotifyBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .pushNotify()
                .tenant("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(512);

        new A2aBeginExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .pushNotify(b -> b
                .tenant("tenant-1")
                .version("version-1")
                .configId("configId-1")
                .taskId("taskId-1")
                .url("url-1")
                .token("token-1")
                .scheme("scheme-1")
                .credentials("credentials-1")
                .contentLength(7)
                .timeout(30000L))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldMatchAnyBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx().build();

        assertNotNull(matcher);
        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test
    public void shouldMatchEmptyBeginEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchBeginEx()
            .typeId(0)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(0);

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateTaskDataEx()
    {
        byte[] bytes = A2aFunctions.dataEx()
            .typeId(0)
            .task()
                .taskId("taskId-1")
                .contextId("contextId-1")
                .state("WORKING")
                .build()
            .build();

        assertNotNull(bytes);

        A2aDataExFW dataEx = new A2aDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("taskId-1", dataEx.task().taskId().asString());
        assertEquals("contextId-1", dataEx.task().contextId().asString());
        assertEquals(A2aTaskState.WORKING, dataEx.task().state().get());
    }

    @Test
    public void shouldMatchTaskDataEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchDataEx()
            .typeId(0)
            .task()
                .taskId("taskId-1")
                .contextId("contextId-1")
                .state("WORKING")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .task(b -> b.taskId("taskId-1").contextId("contextId-1").state(s -> s.set(A2aTaskState.WORKING)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchTaskDataEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchDataEx()
            .typeId(0)
            .task()
                .taskId("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .task(b -> b.taskId("taskId-1").contextId("contextId-1").state(s -> s.set(A2aTaskState.WORKING)))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateMessageDataEx()
    {
        byte[] bytes = A2aFunctions.dataEx()
            .typeId(0)
            .message()
                .taskId("taskId-1")
                .contextId("contextId-1")
                .build()
            .build();

        assertNotNull(bytes);

        A2aDataExFW dataEx = new A2aDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("taskId-1", dataEx.message().taskId().asString());
        assertEquals("contextId-1", dataEx.message().contextId().asString());
    }

    @Test
    public void shouldMatchMessageDataEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchDataEx()
            .typeId(0)
            .message()
                .taskId("taskId-1")
                .contextId("contextId-1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .message(b -> b.taskId("taskId-1").contextId("contextId-1"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchMessageDataEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchDataEx()
            .typeId(0)
            .message()
                .taskId("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .message(b -> b.taskId("taskId-1").contextId("contextId-1"))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateStatusUpdateDataEx()
    {
        byte[] bytes = A2aFunctions.dataEx()
            .typeId(0)
            .statusUpdate()
                .taskId("taskId-1")
                .contextId("contextId-1")
                .state("WORKING")
                .build()
            .build();

        assertNotNull(bytes);

        A2aDataExFW dataEx = new A2aDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("taskId-1", dataEx.statusUpdate().taskId().asString());
        assertEquals("contextId-1", dataEx.statusUpdate().contextId().asString());
        assertEquals(A2aTaskState.WORKING, dataEx.statusUpdate().state().get());
    }

    @Test
    public void shouldMatchStatusUpdateDataEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchDataEx()
            .typeId(0)
            .statusUpdate()
                .taskId("taskId-1")
                .contextId("contextId-1")
                .state("WORKING")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .statusUpdate(b -> b.taskId("taskId-1").contextId("contextId-1").state(s -> s.set(A2aTaskState.WORKING)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchStatusUpdateDataEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchDataEx()
            .typeId(0)
            .statusUpdate()
                .taskId("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .statusUpdate(b -> b.taskId("taskId-1").contextId("contextId-1").state(s -> s.set(A2aTaskState.WORKING)))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateArtifactUpdateDataEx()
    {
        byte[] bytes = A2aFunctions.dataEx()
            .typeId(0)
            .artifactUpdate()
                .taskId("taskId-1")
                .contextId("contextId-1")
                .artifactId("artifactId-1")
                .build()
            .build();

        assertNotNull(bytes);

        A2aDataExFW dataEx = new A2aDataExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("taskId-1", dataEx.artifactUpdate().taskId().asString());
        assertEquals("contextId-1", dataEx.artifactUpdate().contextId().asString());
        assertEquals("artifactId-1", dataEx.artifactUpdate().artifactId().asString());
    }

    @Test
    public void shouldMatchArtifactUpdateDataEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchDataEx()
            .typeId(0)
            .artifactUpdate()
                .taskId("taskId-1")
                .contextId("contextId-1")
                .artifactId("artifactId-1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .artifactUpdate(b -> b.taskId("taskId-1").contextId("contextId-1").artifactId("artifactId-1"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchArtifactUpdateDataEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchDataEx()
            .typeId(0)
            .artifactUpdate()
                .taskId("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aDataExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .artifactUpdate(b -> b.taskId("taskId-1").contextId("contextId-1").artifactId("artifactId-1"))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldMatchAnyDataEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchDataEx().build();

        assertNotNull(matcher);
        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test
    public void shouldMatchEmptyDataEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchDataEx()
            .typeId(0)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(0);

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateBearerResetEx()
    {
        byte[] bytes = A2aFunctions.resetEx()
            .typeId(0)
            .bearer()
                .realm("realm-1")
                .scopes("scopes-1")
                .error("INVALID_TOKEN")
                .build()
            .build();

        assertNotNull(bytes);

        A2aResetExFW resetEx = new A2aResetExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("realm-1", resetEx.bearer().realm().asString());
        assertEquals("scopes-1", resetEx.bearer().scopes().asString());
        assertEquals(A2aBearerError.INVALID_TOKEN, resetEx.bearer().error().get());
    }

    @Test
    public void shouldMatchBearerResetEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchResetEx()
            .typeId(0)
            .bearer()
                .realm("realm-1")
                .scopes("scopes-1")
                .error("INVALID_TOKEN")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aResetExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .bearer(b -> b.realm("realm-1").scopes("scopes-1").error(s -> s.set(A2aBearerError.INVALID_TOKEN)))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchBearerResetEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchResetEx()
            .typeId(0)
            .bearer()
                .realm("wrong")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aResetExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .bearer(b -> b.realm("realm-1").scopes("scopes-1").error(s -> s.set(A2aBearerError.INVALID_TOKEN)))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldGenerateErrorResetEx()
    {
        byte[] bytes = A2aFunctions.resetEx()
            .typeId(0)
            .error()
                .code(7)
                .message("message-1")
                .build()
            .build();

        assertNotNull(bytes);

        A2aResetExFW resetEx = new A2aResetExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals(7, resetEx.error().code());
        assertEquals("message-1", resetEx.error().message().asString());
    }

    @Test
    public void shouldMatchErrorResetEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchResetEx()
            .typeId(0)
            .error()
                .code(7)
                .message("message-1")
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aResetExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .error(b -> b.code(7).message("message-1"))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchErrorResetEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchResetEx()
            .typeId(0)
            .error()
                .code(8)
                .build()
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aResetExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .error(b -> b.code(7).message("message-1"))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldMatchAnyResetEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchResetEx().build();

        assertNotNull(matcher);
        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test
    public void shouldMatchEmptyResetEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchResetEx()
            .typeId(0)
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(0);

        assertNull(matcher.match(byteBuf));
    }

    @Test
    public void shouldGenerateAbortEx()
    {
        byte[] bytes = A2aFunctions.abortEx()
            .typeId(0)
            .reason("reason-1")
            .build();

        A2aAbortExFW abortEx = new A2aAbortExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals("reason-1", abortEx.reason().asString());
    }

    @Test
    public void shouldMatchAbortEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchAbortEx()
            .typeId(0)
            .reason("reason-1")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .reason("reason-1")
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchAbortEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchAbortEx()
            .typeId(0)
            .reason("wrong")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aAbortExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .reason("reason-1")
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldMatchAnyAbortEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchAbortEx().build();

        assertNotNull(matcher);
        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }

    @Test
    public void shouldGenerateEndEx()
    {
        byte[] bytes = A2aFunctions.endEx()
            .typeId(0)
            .outcome("ERROR")
            .build();

        A2aEndExFW endEx = new A2aEndExFW().wrap(new UnsafeBufferEx(bytes), 0, bytes.length);
        assertEquals(A2aOutcome.ERROR, endEx.outcome().get());
    }

    @Test
    public void shouldMatchEndEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchEndEx()
            .typeId(0)
            .outcome("ERROR")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .outcome(o -> o.set(A2aOutcome.ERROR))
            .build();

        assertNotNull(matcher.match(byteBuf));
    }

    @Test(expected = Exception.class)
    public void shouldNotMatchEndEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchEndEx()
            .typeId(0)
            .outcome("OK")
            .build();

        ByteBuffer byteBuf = ByteBuffer.allocate(256);

        new A2aEndExFW.Builder()
            .wrap(new UnsafeBufferEx(byteBuf), 0, byteBuf.capacity())
            .typeId(0)
            .outcome(o -> o.set(A2aOutcome.ERROR))
            .build();

        matcher.match(byteBuf);
    }

    @Test
    public void shouldMatchAnyEndEx() throws Exception
    {
        BytesMatcher matcher = A2aFunctions.matchEndEx().build();

        assertNotNull(matcher);
        assertNull(matcher.match(ByteBuffer.allocate(0)));
    }
}
