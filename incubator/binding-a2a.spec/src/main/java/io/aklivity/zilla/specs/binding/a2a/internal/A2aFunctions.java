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

import java.nio.ByteBuffer;
import java.util.function.Predicate;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.a2a.internal.types.A2aTaskState;
import io.aklivity.zilla.specs.binding.a2a.internal.types.String16FW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aAbortExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aAgentCardBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aArtifactUpdateDataExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aBearerError;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aBearerResetExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aDataExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aEndExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aErrorResetExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aExtendedAgentCardBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aMessageDataExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aMessageSendBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aMessageStreamBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aOutcome;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aPushConfigCreateBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aPushConfigDeleteBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aPushConfigGetBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aPushConfigListBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aPushNotifyBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aResetExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aStatusUpdateDataExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aTaskDataExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aTasksCancelBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aTasksGetBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aTasksListBeginExFW;
import io.aklivity.zilla.specs.binding.a2a.internal.types.stream.A2aTasksSubscribeBeginExFW;

public final class A2aFunctions
{
    @Function
    public static A2aBeginExBuilder beginEx()
    {
        return new A2aBeginExBuilder();
    }

    @Function
    public static A2aBeginExMatcherBuilder matchBeginEx()
    {
        return new A2aBeginExMatcherBuilder();
    }

    @Function
    public static A2aDataExBuilder dataEx()
    {
        return new A2aDataExBuilder();
    }

    @Function
    public static A2aDataExMatcherBuilder matchDataEx()
    {
        return new A2aDataExMatcherBuilder();
    }

    @Function
    public static A2aResetExBuilder resetEx()
    {
        return new A2aResetExBuilder();
    }

    @Function
    public static A2aResetExMatcherBuilder matchResetEx()
    {
        return new A2aResetExMatcherBuilder();
    }

    @Function
    public static A2aAbortExBuilder abortEx()
    {
        return new A2aAbortExBuilder();
    }

    @Function
    public static A2aAbortExMatcherBuilder matchAbortEx()
    {
        return new A2aAbortExMatcherBuilder();
    }

    @Function
    public static A2aEndExBuilder endEx()
    {
        return new A2aEndExBuilder();
    }

    @Function
    public static A2aEndExMatcherBuilder matchEndEx()
    {
        return new A2aEndExMatcherBuilder();
    }

    public static final class A2aBeginExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
        private final A2aBeginExFW.Builder beginExRW = new A2aBeginExFW.Builder();

        private A2aBeginExBuilder()
        {
            beginExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public A2aBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public A2aAgentCardBeginExBuilder agentCard()
        {
            return new A2aAgentCardBeginExBuilder();
        }

        public A2aMessageSendBeginExBuilder messageSend()
        {
            return new A2aMessageSendBeginExBuilder();
        }

        public A2aMessageStreamBeginExBuilder messageStream()
        {
            return new A2aMessageStreamBeginExBuilder();
        }

        public A2aTasksGetBeginExBuilder tasksGet()
        {
            return new A2aTasksGetBeginExBuilder();
        }

        public A2aTasksListBeginExBuilder tasksList()
        {
            return new A2aTasksListBeginExBuilder();
        }

        public A2aTasksCancelBeginExBuilder tasksCancel()
        {
            return new A2aTasksCancelBeginExBuilder();
        }

        public A2aTasksSubscribeBeginExBuilder tasksSubscribe()
        {
            return new A2aTasksSubscribeBeginExBuilder();
        }

        public A2aPushConfigCreateBeginExBuilder pushConfigCreate()
        {
            return new A2aPushConfigCreateBeginExBuilder();
        }

        public A2aPushConfigGetBeginExBuilder pushConfigGet()
        {
            return new A2aPushConfigGetBeginExBuilder();
        }

        public A2aPushConfigListBeginExBuilder pushConfigList()
        {
            return new A2aPushConfigListBeginExBuilder();
        }

        public A2aPushConfigDeleteBeginExBuilder pushConfigDelete()
        {
            return new A2aPushConfigDeleteBeginExBuilder();
        }

        public A2aExtendedAgentCardBeginExBuilder extendedAgentCard()
        {
            return new A2aExtendedAgentCardBeginExBuilder();
        }

        public A2aPushNotifyBeginExBuilder pushNotify()
        {
            return new A2aPushNotifyBeginExBuilder();
        }

        public byte[] build()
        {
            final byte[] array = new byte[beginExRW.limit()];
            writeBuffer.getBytes(0, array);
            return array;
        }

        public final class A2aAgentCardBeginExBuilder
        {
            private String agent;
            private long timeout;

            public A2aAgentCardBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aAgentCardBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.agentCard(b -> b.agent(agent).timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aMessageSendBeginExBuilder
        {
            private String agent;
            private String extensions;
            private int contentLength = -1;
            private long timeout;

            public A2aMessageSendBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aMessageSendBeginExBuilder extensions(
                String extensions)
            {
                this.extensions = extensions;
                return this;
            }

            public A2aMessageSendBeginExBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public A2aMessageSendBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.messageSend(b -> b.agent(agent).extensions(extensions).contentLength(contentLength).timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aMessageStreamBeginExBuilder
        {
            private String agent;
            private String extensions;
            private int contentLength = -1;
            private long timeout;

            public A2aMessageStreamBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aMessageStreamBeginExBuilder extensions(
                String extensions)
            {
                this.extensions = extensions;
                return this;
            }

            public A2aMessageStreamBeginExBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public A2aMessageStreamBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.messageStream(b -> b
                    .agent(agent)
                    .extensions(extensions)
                    .contentLength(contentLength)
                    .timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aTasksGetBeginExBuilder
        {
            private String agent;
            private String extensions;
            private String taskId;
            private int historyLength = -1;
            private long timeout;

            public A2aTasksGetBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aTasksGetBeginExBuilder extensions(
                String extensions)
            {
                this.extensions = extensions;
                return this;
            }

            public A2aTasksGetBeginExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aTasksGetBeginExBuilder historyLength(
                int historyLength)
            {
                this.historyLength = historyLength;
                return this;
            }

            public A2aTasksGetBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.tasksGet(b -> b
                    .agent(agent)
                    .extensions(extensions)
                    .taskId(taskId)
                    .historyLength(historyLength)
                    .timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aTasksListBeginExBuilder
        {
            private String agent;
            private String extensions;
            private String contextId;
            private int contentLength = -1;
            private long timeout;

            public A2aTasksListBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aTasksListBeginExBuilder extensions(
                String extensions)
            {
                this.extensions = extensions;
                return this;
            }

            public A2aTasksListBeginExBuilder contextId(
                String contextId)
            {
                this.contextId = contextId;
                return this;
            }

            public A2aTasksListBeginExBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public A2aTasksListBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.tasksList(b -> b
                    .agent(agent)
                    .extensions(extensions)
                    .contextId(contextId)
                    .contentLength(contentLength)
                    .timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aTasksCancelBeginExBuilder
        {
            private String agent;
            private String extensions;
            private String taskId;
            private long timeout;

            public A2aTasksCancelBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aTasksCancelBeginExBuilder extensions(
                String extensions)
            {
                this.extensions = extensions;
                return this;
            }

            public A2aTasksCancelBeginExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aTasksCancelBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.tasksCancel(b -> b.agent(agent).extensions(extensions).taskId(taskId).timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aTasksSubscribeBeginExBuilder
        {
            private String agent;
            private String extensions;
            private String taskId;
            private long timeout;

            public A2aTasksSubscribeBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aTasksSubscribeBeginExBuilder extensions(
                String extensions)
            {
                this.extensions = extensions;
                return this;
            }

            public A2aTasksSubscribeBeginExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aTasksSubscribeBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.tasksSubscribe(b -> b.agent(agent).extensions(extensions).taskId(taskId).timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aPushConfigCreateBeginExBuilder
        {
            private String agent;
            private String extensions;
            private String taskId;
            private int contentLength = -1;
            private long timeout;

            public A2aPushConfigCreateBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aPushConfigCreateBeginExBuilder extensions(
                String extensions)
            {
                this.extensions = extensions;
                return this;
            }

            public A2aPushConfigCreateBeginExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aPushConfigCreateBeginExBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public A2aPushConfigCreateBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.pushConfigCreate(b -> b
                    .agent(agent)
                    .extensions(extensions)
                    .taskId(taskId)
                    .contentLength(contentLength)
                    .timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aPushConfigGetBeginExBuilder
        {
            private String agent;
            private String extensions;
            private String taskId;
            private String configId;
            private long timeout;

            public A2aPushConfigGetBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aPushConfigGetBeginExBuilder extensions(
                String extensions)
            {
                this.extensions = extensions;
                return this;
            }

            public A2aPushConfigGetBeginExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aPushConfigGetBeginExBuilder configId(
                String configId)
            {
                this.configId = configId;
                return this;
            }

            public A2aPushConfigGetBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.pushConfigGet(b -> b
                    .agent(agent)
                    .extensions(extensions)
                    .taskId(taskId)
                    .configId(configId)
                    .timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aPushConfigListBeginExBuilder
        {
            private String agent;
            private String extensions;
            private String taskId;
            private long timeout;

            public A2aPushConfigListBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aPushConfigListBeginExBuilder extensions(
                String extensions)
            {
                this.extensions = extensions;
                return this;
            }

            public A2aPushConfigListBeginExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aPushConfigListBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.pushConfigList(b -> b.agent(agent).extensions(extensions).taskId(taskId).timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aPushConfigDeleteBeginExBuilder
        {
            private String agent;
            private String extensions;
            private String taskId;
            private String configId;
            private long timeout;

            public A2aPushConfigDeleteBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aPushConfigDeleteBeginExBuilder extensions(
                String extensions)
            {
                this.extensions = extensions;
                return this;
            }

            public A2aPushConfigDeleteBeginExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aPushConfigDeleteBeginExBuilder configId(
                String configId)
            {
                this.configId = configId;
                return this;
            }

            public A2aPushConfigDeleteBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.pushConfigDelete(b -> b
                    .agent(agent)
                    .extensions(extensions)
                    .taskId(taskId)
                    .configId(configId)
                    .timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aExtendedAgentCardBeginExBuilder
        {
            private String agent;
            private String extensions;
            private long timeout;

            public A2aExtendedAgentCardBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aExtendedAgentCardBeginExBuilder extensions(
                String extensions)
            {
                this.extensions = extensions;
                return this;
            }

            public A2aExtendedAgentCardBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.extendedAgentCard(b -> b.agent(agent).extensions(extensions).timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }

        public final class A2aPushNotifyBeginExBuilder
        {
            private String agent;
            private String configId;
            private String taskId;
            private String url;
            private String token;
            private String scheme;
            private String credentials;
            private int contentLength = -1;
            private long timeout;

            public A2aPushNotifyBeginExBuilder agent(
                String agent)
            {
                this.agent = agent;
                return this;
            }

            public A2aPushNotifyBeginExBuilder configId(
                String configId)
            {
                this.configId = configId;
                return this;
            }

            public A2aPushNotifyBeginExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aPushNotifyBeginExBuilder url(
                String url)
            {
                this.url = url;
                return this;
            }

            public A2aPushNotifyBeginExBuilder token(
                String token)
            {
                this.token = token;
                return this;
            }

            public A2aPushNotifyBeginExBuilder scheme(
                String scheme)
            {
                this.scheme = scheme;
                return this;
            }

            public A2aPushNotifyBeginExBuilder credentials(
                String credentials)
            {
                this.credentials = credentials;
                return this;
            }

            public A2aPushNotifyBeginExBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public A2aPushNotifyBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExBuilder build()
            {
                beginExRW.pushNotify(b -> b
                    .agent(agent)
                    .configId(configId)
                    .taskId(taskId)
                    .url(url)
                    .token(token)
                    .scheme(scheme)
                    .credentials(credentials)
                    .contentLength(contentLength)
                    .timeout(timeout));
                return A2aBeginExBuilder.this;
            }
        }
    }

    public static final class A2aBeginExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final A2aBeginExFW beginExRO = new A2aBeginExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<A2aBeginExFW> caseMatcher;

        public A2aBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public A2aAgentCardBeginExMatcherBuilder agentCard()
        {
            this.kind = A2aBeginExFW.KIND_AGENT_CARD;
            final A2aAgentCardBeginExMatcherBuilder matcher = new A2aAgentCardBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aMessageSendBeginExMatcherBuilder messageSend()
        {
            this.kind = A2aBeginExFW.KIND_MESSAGE_SEND;
            final A2aMessageSendBeginExMatcherBuilder matcher = new A2aMessageSendBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aMessageStreamBeginExMatcherBuilder messageStream()
        {
            this.kind = A2aBeginExFW.KIND_MESSAGE_STREAM;
            final A2aMessageStreamBeginExMatcherBuilder matcher = new A2aMessageStreamBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aTasksGetBeginExMatcherBuilder tasksGet()
        {
            this.kind = A2aBeginExFW.KIND_TASKS_GET;
            final A2aTasksGetBeginExMatcherBuilder matcher = new A2aTasksGetBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aTasksListBeginExMatcherBuilder tasksList()
        {
            this.kind = A2aBeginExFW.KIND_TASKS_LIST;
            final A2aTasksListBeginExMatcherBuilder matcher = new A2aTasksListBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aTasksCancelBeginExMatcherBuilder tasksCancel()
        {
            this.kind = A2aBeginExFW.KIND_TASKS_CANCEL;
            final A2aTasksCancelBeginExMatcherBuilder matcher = new A2aTasksCancelBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aTasksSubscribeBeginExMatcherBuilder tasksSubscribe()
        {
            this.kind = A2aBeginExFW.KIND_TASKS_SUBSCRIBE;
            final A2aTasksSubscribeBeginExMatcherBuilder matcher = new A2aTasksSubscribeBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aPushConfigCreateBeginExMatcherBuilder pushConfigCreate()
        {
            this.kind = A2aBeginExFW.KIND_PUSH_CONFIG_CREATE;
            final A2aPushConfigCreateBeginExMatcherBuilder matcher = new A2aPushConfigCreateBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aPushConfigGetBeginExMatcherBuilder pushConfigGet()
        {
            this.kind = A2aBeginExFW.KIND_PUSH_CONFIG_GET;
            final A2aPushConfigGetBeginExMatcherBuilder matcher = new A2aPushConfigGetBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aPushConfigListBeginExMatcherBuilder pushConfigList()
        {
            this.kind = A2aBeginExFW.KIND_PUSH_CONFIG_LIST;
            final A2aPushConfigListBeginExMatcherBuilder matcher = new A2aPushConfigListBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aPushConfigDeleteBeginExMatcherBuilder pushConfigDelete()
        {
            this.kind = A2aBeginExFW.KIND_PUSH_CONFIG_DELETE;
            final A2aPushConfigDeleteBeginExMatcherBuilder matcher = new A2aPushConfigDeleteBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aExtendedAgentCardBeginExMatcherBuilder extendedAgentCard()
        {
            this.kind = A2aBeginExFW.KIND_EXTENDED_AGENT_CARD;
            final A2aExtendedAgentCardBeginExMatcherBuilder matcher = new A2aExtendedAgentCardBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aPushNotifyBeginExMatcherBuilder pushNotify()
        {
            this.kind = A2aBeginExFW.KIND_PUSH_NOTIFY;
            final A2aPushNotifyBeginExMatcherBuilder matcher = new A2aPushNotifyBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private A2aBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final A2aBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchKind(beginEx) &&
                matchCase(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(beginEx != null ? beginEx.toString() : "null");
        }

        private boolean matchTypeId(
            A2aBeginExFW beginEx)
        {
            return typeId == null || typeId == beginEx.typeId();
        }

        private boolean matchKind(
            A2aBeginExFW beginEx)
        {
            return kind == null || kind == beginEx.kind();
        }

        private boolean matchCase(
            A2aBeginExFW beginEx)
        {
            return caseMatcher == null || caseMatcher.test(beginEx);
        }

        public final class A2aAgentCardBeginExMatcherBuilder
        {
            private String16FW agent;
            private Long timeout;

            public A2aAgentCardBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aAgentCardBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aAgentCardBeginExFW agentCard = beginEx.agentCard();
                return matchAgent(agentCard) && matchTimeout(agentCard);
            }

            private boolean matchAgent(
                A2aAgentCardBeginExFW agentCard)
            {
                return agent == null || agent.equals(agentCard.agent());
            }

            private boolean matchTimeout(
                A2aAgentCardBeginExFW agentCard)
            {
                return timeout == null || timeout == agentCard.timeout();
            }
        }

        public final class A2aMessageSendBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW extensions;
            private Integer contentLength;
            private Long timeout;

            public A2aMessageSendBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aMessageSendBeginExMatcherBuilder extensions(
                String extensions)
            {
                this.extensions = new String16FW(extensions);
                return this;
            }

            public A2aMessageSendBeginExMatcherBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public A2aMessageSendBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aMessageSendBeginExFW messageSend = beginEx.messageSend();
                return matchAgent(messageSend) && matchExtensions(messageSend) && matchContentLength(messageSend) &&
                    matchTimeout(messageSend);
            }

            private boolean matchAgent(
                A2aMessageSendBeginExFW messageSend)
            {
                return agent == null || agent.equals(messageSend.agent());
            }

            private boolean matchExtensions(
                A2aMessageSendBeginExFW messageSend)
            {
                return extensions == null || extensions.equals(messageSend.extensions());
            }

            private boolean matchContentLength(
                A2aMessageSendBeginExFW messageSend)
            {
                return contentLength == null || contentLength == messageSend.contentLength();
            }

            private boolean matchTimeout(
                A2aMessageSendBeginExFW messageSend)
            {
                return timeout == null || timeout == messageSend.timeout();
            }
        }

        public final class A2aMessageStreamBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW extensions;
            private Integer contentLength;
            private Long timeout;

            public A2aMessageStreamBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aMessageStreamBeginExMatcherBuilder extensions(
                String extensions)
            {
                this.extensions = new String16FW(extensions);
                return this;
            }

            public A2aMessageStreamBeginExMatcherBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public A2aMessageStreamBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aMessageStreamBeginExFW messageStream = beginEx.messageStream();
                return matchAgent(messageStream) && matchExtensions(messageStream) && matchContentLength(messageStream) &&
                    matchTimeout(messageStream);
            }

            private boolean matchAgent(
                A2aMessageStreamBeginExFW messageStream)
            {
                return agent == null || agent.equals(messageStream.agent());
            }

            private boolean matchExtensions(
                A2aMessageStreamBeginExFW messageStream)
            {
                return extensions == null || extensions.equals(messageStream.extensions());
            }

            private boolean matchContentLength(
                A2aMessageStreamBeginExFW messageStream)
            {
                return contentLength == null || contentLength == messageStream.contentLength();
            }

            private boolean matchTimeout(
                A2aMessageStreamBeginExFW messageStream)
            {
                return timeout == null || timeout == messageStream.timeout();
            }
        }

        public final class A2aTasksGetBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW extensions;
            private String16FW taskId;
            private Integer historyLength;
            private Long timeout;

            public A2aTasksGetBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aTasksGetBeginExMatcherBuilder extensions(
                String extensions)
            {
                this.extensions = new String16FW(extensions);
                return this;
            }

            public A2aTasksGetBeginExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aTasksGetBeginExMatcherBuilder historyLength(
                int historyLength)
            {
                this.historyLength = historyLength;
                return this;
            }

            public A2aTasksGetBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aTasksGetBeginExFW tasksGet = beginEx.tasksGet();
                return matchAgent(tasksGet) && matchExtensions(tasksGet) && matchTaskId(tasksGet) &&
                    matchHistoryLength(tasksGet) && matchTimeout(tasksGet);
            }

            private boolean matchAgent(
                A2aTasksGetBeginExFW tasksGet)
            {
                return agent == null || agent.equals(tasksGet.agent());
            }

            private boolean matchExtensions(
                A2aTasksGetBeginExFW tasksGet)
            {
                return extensions == null || extensions.equals(tasksGet.extensions());
            }

            private boolean matchTaskId(
                A2aTasksGetBeginExFW tasksGet)
            {
                return taskId == null || taskId.equals(tasksGet.taskId());
            }

            private boolean matchHistoryLength(
                A2aTasksGetBeginExFW tasksGet)
            {
                return historyLength == null || historyLength == tasksGet.historyLength();
            }

            private boolean matchTimeout(
                A2aTasksGetBeginExFW tasksGet)
            {
                return timeout == null || timeout == tasksGet.timeout();
            }
        }

        public final class A2aTasksListBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW extensions;
            private String16FW contextId;
            private Integer contentLength;
            private Long timeout;

            public A2aTasksListBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aTasksListBeginExMatcherBuilder extensions(
                String extensions)
            {
                this.extensions = new String16FW(extensions);
                return this;
            }

            public A2aTasksListBeginExMatcherBuilder contextId(
                String contextId)
            {
                this.contextId = new String16FW(contextId);
                return this;
            }

            public A2aTasksListBeginExMatcherBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public A2aTasksListBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aTasksListBeginExFW tasksList = beginEx.tasksList();
                return matchAgent(tasksList) && matchExtensions(tasksList) && matchContextId(tasksList) &&
                    matchContentLength(tasksList) && matchTimeout(tasksList);
            }

            private boolean matchAgent(
                A2aTasksListBeginExFW tasksList)
            {
                return agent == null || agent.equals(tasksList.agent());
            }

            private boolean matchExtensions(
                A2aTasksListBeginExFW tasksList)
            {
                return extensions == null || extensions.equals(tasksList.extensions());
            }

            private boolean matchContextId(
                A2aTasksListBeginExFW tasksList)
            {
                return contextId == null || contextId.equals(tasksList.contextId());
            }

            private boolean matchContentLength(
                A2aTasksListBeginExFW tasksList)
            {
                return contentLength == null || contentLength == tasksList.contentLength();
            }

            private boolean matchTimeout(
                A2aTasksListBeginExFW tasksList)
            {
                return timeout == null || timeout == tasksList.timeout();
            }
        }

        public final class A2aTasksCancelBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW extensions;
            private String16FW taskId;
            private Long timeout;

            public A2aTasksCancelBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aTasksCancelBeginExMatcherBuilder extensions(
                String extensions)
            {
                this.extensions = new String16FW(extensions);
                return this;
            }

            public A2aTasksCancelBeginExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aTasksCancelBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aTasksCancelBeginExFW tasksCancel = beginEx.tasksCancel();
                return matchAgent(tasksCancel) && matchExtensions(tasksCancel) && matchTaskId(tasksCancel) &&
                    matchTimeout(tasksCancel);
            }

            private boolean matchAgent(
                A2aTasksCancelBeginExFW tasksCancel)
            {
                return agent == null || agent.equals(tasksCancel.agent());
            }

            private boolean matchExtensions(
                A2aTasksCancelBeginExFW tasksCancel)
            {
                return extensions == null || extensions.equals(tasksCancel.extensions());
            }

            private boolean matchTaskId(
                A2aTasksCancelBeginExFW tasksCancel)
            {
                return taskId == null || taskId.equals(tasksCancel.taskId());
            }

            private boolean matchTimeout(
                A2aTasksCancelBeginExFW tasksCancel)
            {
                return timeout == null || timeout == tasksCancel.timeout();
            }
        }

        public final class A2aTasksSubscribeBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW extensions;
            private String16FW taskId;
            private Long timeout;

            public A2aTasksSubscribeBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aTasksSubscribeBeginExMatcherBuilder extensions(
                String extensions)
            {
                this.extensions = new String16FW(extensions);
                return this;
            }

            public A2aTasksSubscribeBeginExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aTasksSubscribeBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aTasksSubscribeBeginExFW tasksSubscribe = beginEx.tasksSubscribe();
                return matchAgent(tasksSubscribe) && matchExtensions(tasksSubscribe) && matchTaskId(tasksSubscribe) &&
                    matchTimeout(tasksSubscribe);
            }

            private boolean matchAgent(
                A2aTasksSubscribeBeginExFW tasksSubscribe)
            {
                return agent == null || agent.equals(tasksSubscribe.agent());
            }

            private boolean matchExtensions(
                A2aTasksSubscribeBeginExFW tasksSubscribe)
            {
                return extensions == null || extensions.equals(tasksSubscribe.extensions());
            }

            private boolean matchTaskId(
                A2aTasksSubscribeBeginExFW tasksSubscribe)
            {
                return taskId == null || taskId.equals(tasksSubscribe.taskId());
            }

            private boolean matchTimeout(
                A2aTasksSubscribeBeginExFW tasksSubscribe)
            {
                return timeout == null || timeout == tasksSubscribe.timeout();
            }
        }

        public final class A2aPushConfigCreateBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW extensions;
            private String16FW taskId;
            private Integer contentLength;
            private Long timeout;

            public A2aPushConfigCreateBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aPushConfigCreateBeginExMatcherBuilder extensions(
                String extensions)
            {
                this.extensions = new String16FW(extensions);
                return this;
            }

            public A2aPushConfigCreateBeginExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aPushConfigCreateBeginExMatcherBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public A2aPushConfigCreateBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aPushConfigCreateBeginExFW pushConfigCreate = beginEx.pushConfigCreate();
                return matchAgent(pushConfigCreate) && matchExtensions(pushConfigCreate) && matchTaskId(pushConfigCreate) &&
                    matchContentLength(pushConfigCreate) && matchTimeout(pushConfigCreate);
            }

            private boolean matchAgent(
                A2aPushConfigCreateBeginExFW pushConfigCreate)
            {
                return agent == null || agent.equals(pushConfigCreate.agent());
            }

            private boolean matchExtensions(
                A2aPushConfigCreateBeginExFW pushConfigCreate)
            {
                return extensions == null || extensions.equals(pushConfigCreate.extensions());
            }

            private boolean matchTaskId(
                A2aPushConfigCreateBeginExFW pushConfigCreate)
            {
                return taskId == null || taskId.equals(pushConfigCreate.taskId());
            }

            private boolean matchContentLength(
                A2aPushConfigCreateBeginExFW pushConfigCreate)
            {
                return contentLength == null || contentLength == pushConfigCreate.contentLength();
            }

            private boolean matchTimeout(
                A2aPushConfigCreateBeginExFW pushConfigCreate)
            {
                return timeout == null || timeout == pushConfigCreate.timeout();
            }
        }

        public final class A2aPushConfigGetBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW extensions;
            private String16FW taskId;
            private String16FW configId;
            private Long timeout;

            public A2aPushConfigGetBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aPushConfigGetBeginExMatcherBuilder extensions(
                String extensions)
            {
                this.extensions = new String16FW(extensions);
                return this;
            }

            public A2aPushConfigGetBeginExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aPushConfigGetBeginExMatcherBuilder configId(
                String configId)
            {
                this.configId = new String16FW(configId);
                return this;
            }

            public A2aPushConfigGetBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aPushConfigGetBeginExFW pushConfigGet = beginEx.pushConfigGet();
                return matchAgent(pushConfigGet) && matchExtensions(pushConfigGet) && matchTaskId(pushConfigGet) &&
                    matchConfigId(pushConfigGet) && matchTimeout(pushConfigGet);
            }

            private boolean matchAgent(
                A2aPushConfigGetBeginExFW pushConfigGet)
            {
                return agent == null || agent.equals(pushConfigGet.agent());
            }

            private boolean matchExtensions(
                A2aPushConfigGetBeginExFW pushConfigGet)
            {
                return extensions == null || extensions.equals(pushConfigGet.extensions());
            }

            private boolean matchTaskId(
                A2aPushConfigGetBeginExFW pushConfigGet)
            {
                return taskId == null || taskId.equals(pushConfigGet.taskId());
            }

            private boolean matchConfigId(
                A2aPushConfigGetBeginExFW pushConfigGet)
            {
                return configId == null || configId.equals(pushConfigGet.configId());
            }

            private boolean matchTimeout(
                A2aPushConfigGetBeginExFW pushConfigGet)
            {
                return timeout == null || timeout == pushConfigGet.timeout();
            }
        }

        public final class A2aPushConfigListBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW extensions;
            private String16FW taskId;
            private Long timeout;

            public A2aPushConfigListBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aPushConfigListBeginExMatcherBuilder extensions(
                String extensions)
            {
                this.extensions = new String16FW(extensions);
                return this;
            }

            public A2aPushConfigListBeginExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aPushConfigListBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aPushConfigListBeginExFW pushConfigList = beginEx.pushConfigList();
                return matchAgent(pushConfigList) && matchExtensions(pushConfigList) && matchTaskId(pushConfigList) &&
                    matchTimeout(pushConfigList);
            }

            private boolean matchAgent(
                A2aPushConfigListBeginExFW pushConfigList)
            {
                return agent == null || agent.equals(pushConfigList.agent());
            }

            private boolean matchExtensions(
                A2aPushConfigListBeginExFW pushConfigList)
            {
                return extensions == null || extensions.equals(pushConfigList.extensions());
            }

            private boolean matchTaskId(
                A2aPushConfigListBeginExFW pushConfigList)
            {
                return taskId == null || taskId.equals(pushConfigList.taskId());
            }

            private boolean matchTimeout(
                A2aPushConfigListBeginExFW pushConfigList)
            {
                return timeout == null || timeout == pushConfigList.timeout();
            }
        }

        public final class A2aPushConfigDeleteBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW extensions;
            private String16FW taskId;
            private String16FW configId;
            private Long timeout;

            public A2aPushConfigDeleteBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aPushConfigDeleteBeginExMatcherBuilder extensions(
                String extensions)
            {
                this.extensions = new String16FW(extensions);
                return this;
            }

            public A2aPushConfigDeleteBeginExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aPushConfigDeleteBeginExMatcherBuilder configId(
                String configId)
            {
                this.configId = new String16FW(configId);
                return this;
            }

            public A2aPushConfigDeleteBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aPushConfigDeleteBeginExFW pushConfigDelete = beginEx.pushConfigDelete();
                return matchAgent(pushConfigDelete) && matchExtensions(pushConfigDelete) && matchTaskId(pushConfigDelete) &&
                    matchConfigId(pushConfigDelete) && matchTimeout(pushConfigDelete);
            }

            private boolean matchAgent(
                A2aPushConfigDeleteBeginExFW pushConfigDelete)
            {
                return agent == null || agent.equals(pushConfigDelete.agent());
            }

            private boolean matchExtensions(
                A2aPushConfigDeleteBeginExFW pushConfigDelete)
            {
                return extensions == null || extensions.equals(pushConfigDelete.extensions());
            }

            private boolean matchTaskId(
                A2aPushConfigDeleteBeginExFW pushConfigDelete)
            {
                return taskId == null || taskId.equals(pushConfigDelete.taskId());
            }

            private boolean matchConfigId(
                A2aPushConfigDeleteBeginExFW pushConfigDelete)
            {
                return configId == null || configId.equals(pushConfigDelete.configId());
            }

            private boolean matchTimeout(
                A2aPushConfigDeleteBeginExFW pushConfigDelete)
            {
                return timeout == null || timeout == pushConfigDelete.timeout();
            }
        }

        public final class A2aExtendedAgentCardBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW extensions;
            private Long timeout;

            public A2aExtendedAgentCardBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aExtendedAgentCardBeginExMatcherBuilder extensions(
                String extensions)
            {
                this.extensions = new String16FW(extensions);
                return this;
            }

            public A2aExtendedAgentCardBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aExtendedAgentCardBeginExFW extendedAgentCard = beginEx.extendedAgentCard();
                return matchAgent(extendedAgentCard) && matchExtensions(extendedAgentCard) && matchTimeout(extendedAgentCard);
            }

            private boolean matchAgent(
                A2aExtendedAgentCardBeginExFW extendedAgentCard)
            {
                return agent == null || agent.equals(extendedAgentCard.agent());
            }

            private boolean matchExtensions(
                A2aExtendedAgentCardBeginExFW extendedAgentCard)
            {
                return extensions == null || extensions.equals(extendedAgentCard.extensions());
            }

            private boolean matchTimeout(
                A2aExtendedAgentCardBeginExFW extendedAgentCard)
            {
                return timeout == null || timeout == extendedAgentCard.timeout();
            }
        }

        public final class A2aPushNotifyBeginExMatcherBuilder
        {
            private String16FW agent;
            private String16FW configId;
            private String16FW taskId;
            private String16FW url;
            private String16FW token;
            private String16FW scheme;
            private String16FW credentials;
            private Integer contentLength;
            private Long timeout;

            public A2aPushNotifyBeginExMatcherBuilder agent(
                String agent)
            {
                this.agent = new String16FW(agent);
                return this;
            }

            public A2aPushNotifyBeginExMatcherBuilder configId(
                String configId)
            {
                this.configId = new String16FW(configId);
                return this;
            }

            public A2aPushNotifyBeginExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aPushNotifyBeginExMatcherBuilder url(
                String url)
            {
                this.url = new String16FW(url);
                return this;
            }

            public A2aPushNotifyBeginExMatcherBuilder token(
                String token)
            {
                this.token = new String16FW(token);
                return this;
            }

            public A2aPushNotifyBeginExMatcherBuilder scheme(
                String scheme)
            {
                this.scheme = new String16FW(scheme);
                return this;
            }

            public A2aPushNotifyBeginExMatcherBuilder credentials(
                String credentials)
            {
                this.credentials = new String16FW(credentials);
                return this;
            }

            public A2aPushNotifyBeginExMatcherBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public A2aPushNotifyBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public A2aBeginExMatcherBuilder build()
            {
                return A2aBeginExMatcherBuilder.this;
            }

            private boolean match(
                A2aBeginExFW beginEx)
            {
                final A2aPushNotifyBeginExFW pushNotify = beginEx.pushNotify();
                return matchAgent(pushNotify) && matchConfigId(pushNotify) && matchTaskId(pushNotify) &&
                    matchUrl(pushNotify) && matchToken(pushNotify) && matchScheme(pushNotify) &&
                    matchCredentials(pushNotify) && matchContentLength(pushNotify) && matchTimeout(pushNotify);
            }

            private boolean matchAgent(
                A2aPushNotifyBeginExFW pushNotify)
            {
                return agent == null || agent.equals(pushNotify.agent());
            }

            private boolean matchConfigId(
                A2aPushNotifyBeginExFW pushNotify)
            {
                return configId == null || configId.equals(pushNotify.configId());
            }

            private boolean matchTaskId(
                A2aPushNotifyBeginExFW pushNotify)
            {
                return taskId == null || taskId.equals(pushNotify.taskId());
            }

            private boolean matchUrl(
                A2aPushNotifyBeginExFW pushNotify)
            {
                return url == null || url.equals(pushNotify.url());
            }

            private boolean matchToken(
                A2aPushNotifyBeginExFW pushNotify)
            {
                return token == null || token.equals(pushNotify.token());
            }

            private boolean matchScheme(
                A2aPushNotifyBeginExFW pushNotify)
            {
                return scheme == null || scheme.equals(pushNotify.scheme());
            }

            private boolean matchCredentials(
                A2aPushNotifyBeginExFW pushNotify)
            {
                return credentials == null || credentials.equals(pushNotify.credentials());
            }

            private boolean matchContentLength(
                A2aPushNotifyBeginExFW pushNotify)
            {
                return contentLength == null || contentLength == pushNotify.contentLength();
            }

            private boolean matchTimeout(
                A2aPushNotifyBeginExFW pushNotify)
            {
                return timeout == null || timeout == pushNotify.timeout();
            }
        }
    }

    public static final class A2aDataExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[512]);
        private final A2aDataExFW.Builder dataExRW = new A2aDataExFW.Builder();

        private A2aDataExBuilder()
        {
            dataExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public A2aDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public A2aTaskDataExBuilder task()
        {
            return new A2aTaskDataExBuilder();
        }

        public A2aMessageDataExBuilder message()
        {
            return new A2aMessageDataExBuilder();
        }

        public A2aStatusUpdateDataExBuilder statusUpdate()
        {
            return new A2aStatusUpdateDataExBuilder();
        }

        public A2aArtifactUpdateDataExBuilder artifactUpdate()
        {
            return new A2aArtifactUpdateDataExBuilder();
        }

        public byte[] build()
        {
            final byte[] array = new byte[dataExRW.limit()];
            writeBuffer.getBytes(0, array);
            return array;
        }

        public final class A2aTaskDataExBuilder
        {
            private String taskId;
            private String contextId;
            private A2aTaskState state = A2aTaskState.UNSPECIFIED;

            public A2aTaskDataExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aTaskDataExBuilder contextId(
                String contextId)
            {
                this.contextId = contextId;
                return this;
            }

            public A2aTaskDataExBuilder state(
                String state)
            {
                this.state = A2aTaskState.valueOf(state);
                return this;
            }

            public A2aDataExBuilder build()
            {
                dataExRW.task(b -> b.taskId(taskId).contextId(contextId).state(s -> s.set(state)));
                return A2aDataExBuilder.this;
            }
        }

        public final class A2aMessageDataExBuilder
        {
            private String taskId;
            private String contextId;

            public A2aMessageDataExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aMessageDataExBuilder contextId(
                String contextId)
            {
                this.contextId = contextId;
                return this;
            }

            public A2aDataExBuilder build()
            {
                dataExRW.message(b -> b.taskId(taskId).contextId(contextId));
                return A2aDataExBuilder.this;
            }
        }

        public final class A2aStatusUpdateDataExBuilder
        {
            private String taskId;
            private String contextId;
            private A2aTaskState state = A2aTaskState.UNSPECIFIED;

            public A2aStatusUpdateDataExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aStatusUpdateDataExBuilder contextId(
                String contextId)
            {
                this.contextId = contextId;
                return this;
            }

            public A2aStatusUpdateDataExBuilder state(
                String state)
            {
                this.state = A2aTaskState.valueOf(state);
                return this;
            }

            public A2aDataExBuilder build()
            {
                dataExRW.statusUpdate(b -> b.taskId(taskId).contextId(contextId).state(s -> s.set(state)));
                return A2aDataExBuilder.this;
            }
        }

        public final class A2aArtifactUpdateDataExBuilder
        {
            private String taskId;
            private String contextId;
            private String artifactId;

            public A2aArtifactUpdateDataExBuilder taskId(
                String taskId)
            {
                this.taskId = taskId;
                return this;
            }

            public A2aArtifactUpdateDataExBuilder contextId(
                String contextId)
            {
                this.contextId = contextId;
                return this;
            }

            public A2aArtifactUpdateDataExBuilder artifactId(
                String artifactId)
            {
                this.artifactId = artifactId;
                return this;
            }

            public A2aDataExBuilder build()
            {
                dataExRW.artifactUpdate(b -> b.taskId(taskId).contextId(contextId).artifactId(artifactId));
                return A2aDataExBuilder.this;
            }
        }
    }

    public static final class A2aDataExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final A2aDataExFW dataExRO = new A2aDataExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<A2aDataExFW> caseMatcher;

        public A2aDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public A2aTaskDataExMatcherBuilder task()
        {
            this.kind = A2aDataExFW.KIND_TASK;
            final A2aTaskDataExMatcherBuilder matcher = new A2aTaskDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aMessageDataExMatcherBuilder message()
        {
            this.kind = A2aDataExFW.KIND_MESSAGE;
            final A2aMessageDataExMatcherBuilder matcher = new A2aMessageDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aStatusUpdateDataExMatcherBuilder statusUpdate()
        {
            this.kind = A2aDataExFW.KIND_STATUS_UPDATE;
            final A2aStatusUpdateDataExMatcherBuilder matcher = new A2aStatusUpdateDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aArtifactUpdateDataExMatcherBuilder artifactUpdate()
        {
            this.kind = A2aDataExFW.KIND_ARTIFACT_UPDATE;
            final A2aArtifactUpdateDataExMatcherBuilder matcher = new A2aArtifactUpdateDataExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private A2aDataExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final A2aDataExFW dataEx = dataExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (dataEx != null &&
                matchTypeId(dataEx) &&
                matchKind(dataEx) &&
                matchCase(dataEx))
            {
                byteBuf.position(byteBuf.position() + dataEx.sizeof());
                return dataEx;
            }

            throw new Exception(dataEx != null ? dataEx.toString() : "null");
        }

        private boolean matchTypeId(
            A2aDataExFW dataEx)
        {
            return typeId == null || typeId == dataEx.typeId();
        }

        private boolean matchKind(
            A2aDataExFW dataEx)
        {
            return kind == null || kind == dataEx.kind();
        }

        private boolean matchCase(
            A2aDataExFW dataEx)
        {
            return caseMatcher == null || caseMatcher.test(dataEx);
        }

        public final class A2aTaskDataExMatcherBuilder
        {
            private String16FW taskId;
            private String16FW contextId;
            private A2aTaskState state;

            public A2aTaskDataExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aTaskDataExMatcherBuilder contextId(
                String contextId)
            {
                this.contextId = new String16FW(contextId);
                return this;
            }

            public A2aTaskDataExMatcherBuilder state(
                String state)
            {
                this.state = A2aTaskState.valueOf(state);
                return this;
            }

            public A2aDataExMatcherBuilder build()
            {
                return A2aDataExMatcherBuilder.this;
            }

            private boolean match(
                A2aDataExFW dataEx)
            {
                final A2aTaskDataExFW task = dataEx.task();
                return matchTaskId(task) && matchContextId(task) && matchState(task);
            }

            private boolean matchTaskId(
                A2aTaskDataExFW task)
            {
                return taskId == null || taskId.equals(task.taskId());
            }

            private boolean matchContextId(
                A2aTaskDataExFW task)
            {
                return contextId == null || contextId.equals(task.contextId());
            }

            private boolean matchState(
                A2aTaskDataExFW task)
            {
                return state == null || state == task.state().get();
            }
        }

        public final class A2aMessageDataExMatcherBuilder
        {
            private String16FW taskId;
            private String16FW contextId;

            public A2aMessageDataExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aMessageDataExMatcherBuilder contextId(
                String contextId)
            {
                this.contextId = new String16FW(contextId);
                return this;
            }

            public A2aDataExMatcherBuilder build()
            {
                return A2aDataExMatcherBuilder.this;
            }

            private boolean match(
                A2aDataExFW dataEx)
            {
                final A2aMessageDataExFW message = dataEx.message();
                return matchTaskId(message) && matchContextId(message);
            }

            private boolean matchTaskId(
                A2aMessageDataExFW message)
            {
                return taskId == null || taskId.equals(message.taskId());
            }

            private boolean matchContextId(
                A2aMessageDataExFW message)
            {
                return contextId == null || contextId.equals(message.contextId());
            }
        }

        public final class A2aStatusUpdateDataExMatcherBuilder
        {
            private String16FW taskId;
            private String16FW contextId;
            private A2aTaskState state;

            public A2aStatusUpdateDataExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aStatusUpdateDataExMatcherBuilder contextId(
                String contextId)
            {
                this.contextId = new String16FW(contextId);
                return this;
            }

            public A2aStatusUpdateDataExMatcherBuilder state(
                String state)
            {
                this.state = A2aTaskState.valueOf(state);
                return this;
            }

            public A2aDataExMatcherBuilder build()
            {
                return A2aDataExMatcherBuilder.this;
            }

            private boolean match(
                A2aDataExFW dataEx)
            {
                final A2aStatusUpdateDataExFW statusUpdate = dataEx.statusUpdate();
                return matchTaskId(statusUpdate) && matchContextId(statusUpdate) && matchState(statusUpdate);
            }

            private boolean matchTaskId(
                A2aStatusUpdateDataExFW statusUpdate)
            {
                return taskId == null || taskId.equals(statusUpdate.taskId());
            }

            private boolean matchContextId(
                A2aStatusUpdateDataExFW statusUpdate)
            {
                return contextId == null || contextId.equals(statusUpdate.contextId());
            }

            private boolean matchState(
                A2aStatusUpdateDataExFW statusUpdate)
            {
                return state == null || state == statusUpdate.state().get();
            }
        }

        public final class A2aArtifactUpdateDataExMatcherBuilder
        {
            private String16FW taskId;
            private String16FW contextId;
            private String16FW artifactId;

            public A2aArtifactUpdateDataExMatcherBuilder taskId(
                String taskId)
            {
                this.taskId = new String16FW(taskId);
                return this;
            }

            public A2aArtifactUpdateDataExMatcherBuilder contextId(
                String contextId)
            {
                this.contextId = new String16FW(contextId);
                return this;
            }

            public A2aArtifactUpdateDataExMatcherBuilder artifactId(
                String artifactId)
            {
                this.artifactId = new String16FW(artifactId);
                return this;
            }

            public A2aDataExMatcherBuilder build()
            {
                return A2aDataExMatcherBuilder.this;
            }

            private boolean match(
                A2aDataExFW dataEx)
            {
                final A2aArtifactUpdateDataExFW artifactUpdate = dataEx.artifactUpdate();
                return matchTaskId(artifactUpdate) && matchContextId(artifactUpdate) && matchArtifactId(artifactUpdate);
            }

            private boolean matchTaskId(
                A2aArtifactUpdateDataExFW artifactUpdate)
            {
                return taskId == null || taskId.equals(artifactUpdate.taskId());
            }

            private boolean matchContextId(
                A2aArtifactUpdateDataExFW artifactUpdate)
            {
                return contextId == null || contextId.equals(artifactUpdate.contextId());
            }

            private boolean matchArtifactId(
                A2aArtifactUpdateDataExFW artifactUpdate)
            {
                return artifactId == null || artifactId.equals(artifactUpdate.artifactId());
            }
        }
    }

    public static final class A2aResetExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[512]);
        private final A2aResetExFW.Builder resetExRW = new A2aResetExFW.Builder();

        private A2aResetExBuilder()
        {
            resetExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public A2aResetExBuilder typeId(
            int typeId)
        {
            resetExRW.typeId(typeId);
            return this;
        }

        public A2aBearerResetExBuilder bearer()
        {
            return new A2aBearerResetExBuilder();
        }

        public A2aErrorResetExBuilder error()
        {
            return new A2aErrorResetExBuilder();
        }

        public byte[] build()
        {
            final byte[] array = new byte[resetExRW.limit()];
            writeBuffer.getBytes(0, array);
            return array;
        }

        public final class A2aBearerResetExBuilder
        {
            private String realm;
            private String scopes;
            private A2aBearerError error;

            public A2aBearerResetExBuilder realm(
                String realm)
            {
                this.realm = realm;
                return this;
            }

            public A2aBearerResetExBuilder scopes(
                String scopes)
            {
                this.scopes = scopes;
                return this;
            }

            public A2aBearerResetExBuilder error(
                String error)
            {
                this.error = A2aBearerError.valueOf(error);
                return this;
            }

            public A2aResetExBuilder build()
            {
                resetExRW.bearer(b -> b.realm(realm).scopes(scopes).error(s -> s.set(error)));
                return A2aResetExBuilder.this;
            }
        }

        public final class A2aErrorResetExBuilder
        {
            private int code;
            private String message;

            public A2aErrorResetExBuilder code(
                int code)
            {
                this.code = code;
                return this;
            }

            public A2aErrorResetExBuilder message(
                String message)
            {
                this.message = message;
                return this;
            }

            public A2aResetExBuilder build()
            {
                resetExRW.error(b -> b.code(code).message(message));
                return A2aResetExBuilder.this;
            }
        }
    }

    public static final class A2aResetExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final A2aResetExFW resetExRO = new A2aResetExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<A2aResetExFW> caseMatcher;

        public A2aResetExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public A2aBearerResetExMatcherBuilder bearer()
        {
            this.kind = A2aResetExFW.KIND_BEARER;
            final A2aBearerResetExMatcherBuilder matcher = new A2aBearerResetExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public A2aErrorResetExMatcherBuilder error()
        {
            this.kind = A2aResetExFW.KIND_ERROR;
            final A2aErrorResetExMatcherBuilder matcher = new A2aErrorResetExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private A2aResetExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final A2aResetExFW resetEx = resetExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (resetEx != null &&
                matchTypeId(resetEx) &&
                matchKind(resetEx) &&
                matchCase(resetEx))
            {
                byteBuf.position(byteBuf.position() + resetEx.sizeof());
                return resetEx;
            }

            throw new Exception(resetEx != null ? resetEx.toString() : "null");
        }

        private boolean matchTypeId(
            A2aResetExFW resetEx)
        {
            return typeId == null || typeId == resetEx.typeId();
        }

        private boolean matchKind(
            A2aResetExFW resetEx)
        {
            return kind == null || kind == resetEx.kind();
        }

        private boolean matchCase(
            A2aResetExFW resetEx)
        {
            return caseMatcher == null || caseMatcher.test(resetEx);
        }

        public final class A2aBearerResetExMatcherBuilder
        {
            private String16FW realm;
            private String16FW scopes;
            private A2aBearerError error;

            public A2aBearerResetExMatcherBuilder realm(
                String realm)
            {
                this.realm = new String16FW(realm);
                return this;
            }

            public A2aBearerResetExMatcherBuilder scopes(
                String scopes)
            {
                this.scopes = new String16FW(scopes);
                return this;
            }

            public A2aBearerResetExMatcherBuilder error(
                String error)
            {
                this.error = A2aBearerError.valueOf(error);
                return this;
            }

            public A2aResetExMatcherBuilder build()
            {
                return A2aResetExMatcherBuilder.this;
            }

            private boolean match(
                A2aResetExFW resetEx)
            {
                final A2aBearerResetExFW bearer = resetEx.bearer();
                return matchRealm(bearer) && matchScopes(bearer) && matchError(bearer);
            }

            private boolean matchRealm(
                A2aBearerResetExFW bearer)
            {
                return realm == null || realm.equals(bearer.realm());
            }

            private boolean matchScopes(
                A2aBearerResetExFW bearer)
            {
                return scopes == null || scopes.equals(bearer.scopes());
            }

            private boolean matchError(
                A2aBearerResetExFW bearer)
            {
                return error == null || error == bearer.error().get();
            }
        }

        public final class A2aErrorResetExMatcherBuilder
        {
            private Integer code;
            private String16FW message;

            public A2aErrorResetExMatcherBuilder code(
                int code)
            {
                this.code = code;
                return this;
            }

            public A2aErrorResetExMatcherBuilder message(
                String message)
            {
                this.message = new String16FW(message);
                return this;
            }

            public A2aResetExMatcherBuilder build()
            {
                return A2aResetExMatcherBuilder.this;
            }

            private boolean match(
                A2aResetExFW resetEx)
            {
                final A2aErrorResetExFW error = resetEx.error();
                return matchCode(error) && matchMessage(error);
            }

            private boolean matchCode(
                A2aErrorResetExFW error)
            {
                return code == null || code == error.code();
            }

            private boolean matchMessage(
                A2aErrorResetExFW error)
            {
                return message == null || message.equals(error.message());
            }
        }
    }

    public static final class A2aAbortExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[256]);
        private final A2aAbortExFW.Builder abortExRW = new A2aAbortExFW.Builder();

        private A2aAbortExBuilder()
        {
            abortExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public A2aAbortExBuilder typeId(
            int typeId)
        {
            abortExRW.typeId(typeId);
            return this;
        }

        public A2aAbortExBuilder reason(
            String reason)
        {
            abortExRW.reason(reason);
            return this;
        }

        public byte[] build()
        {
            final A2aAbortExFW abortEx = abortExRW.build();
            final byte[] array = new byte[abortEx.sizeof()];
            writeBuffer.getBytes(abortEx.offset(), array);
            return array;
        }
    }

    public static final class A2aAbortExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final A2aAbortExFW abortExRO = new A2aAbortExFW();

        private Integer typeId;
        private String16FW reason;

        public A2aAbortExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public A2aAbortExMatcherBuilder reason(
            String reason)
        {
            this.reason = new String16FW(reason);
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null || reason != null ? this::match : buf -> null;
        }

        private A2aAbortExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final A2aAbortExFW abortEx = abortExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (abortEx != null &&
                matchTypeId(abortEx) &&
                matchReason(abortEx))
            {
                byteBuf.position(byteBuf.position() + abortEx.sizeof());
                return abortEx;
            }

            throw new Exception(abortEx != null ? abortEx.toString() : "null");
        }

        private boolean matchTypeId(
            A2aAbortExFW abortEx)
        {
            return typeId == null || typeId == abortEx.typeId();
        }

        private boolean matchReason(
            A2aAbortExFW abortEx)
        {
            return reason == null || reason.equals(abortEx.reason());
        }
    }

    public static final class A2aEndExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[256]);
        private final A2aEndExFW.Builder endExRW = new A2aEndExFW.Builder();

        private A2aEndExBuilder()
        {
            endExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public A2aEndExBuilder typeId(
            int typeId)
        {
            endExRW.typeId(typeId);
            return this;
        }

        public A2aEndExBuilder outcome(
            String outcome)
        {
            final A2aOutcome resolved = A2aOutcome.valueOf(outcome);
            endExRW.outcome(o -> o.set(resolved));
            return this;
        }

        public byte[] build()
        {
            final A2aEndExFW endEx = endExRW.build();
            final byte[] array = new byte[endEx.sizeof()];
            writeBuffer.getBytes(endEx.offset(), array);
            return array;
        }
    }

    public static final class A2aEndExMatcherBuilder
    {
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
        private final A2aEndExFW endExRO = new A2aEndExFW();

        private Integer typeId;
        private A2aOutcome outcome;

        public A2aEndExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public A2aEndExMatcherBuilder outcome(
            String outcome)
        {
            this.outcome = A2aOutcome.valueOf(outcome);
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null || outcome != null ? this::match : buf -> null;
        }

        private A2aEndExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final A2aEndExFW endEx = endExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (endEx != null &&
                matchTypeId(endEx) &&
                matchOutcome(endEx))
            {
                byteBuf.position(byteBuf.position() + endEx.sizeof());
                return endEx;
            }

            throw new Exception(endEx != null ? endEx.toString() : "null");
        }

        private boolean matchTypeId(
            A2aEndExFW endEx)
        {
            return typeId == null || typeId == endEx.typeId();
        }

        private boolean matchOutcome(
            A2aEndExFW endEx)
        {
            return outcome == null || outcome == endEx.outcome().get();
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(A2aFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "a2a";
        }
    }

    private A2aFunctions()
    {
        // utility
    }
}
