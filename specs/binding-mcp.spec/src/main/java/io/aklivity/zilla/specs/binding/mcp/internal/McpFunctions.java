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

import java.nio.ByteBuffer;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.specs.binding.mcp.internal.types.String16FW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpAbortExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpChallengeExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpLifecycleBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpProgressFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpPromptsGetBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpPromptsListBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpPromptsListChangedFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpResourcesListBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpResourcesListChangedFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpResourcesReadBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpResumableFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpResumeChallengeExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpSuspendFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpToolsCallBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpToolsListBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpToolsListChangedFlushExFW;

public final class McpFunctions
{
    @Function
    public static McpBeginExBuilder beginEx()
    {
        return new McpBeginExBuilder();
    }

    @Function
    public static McpBeginExMatcherBuilder matchBeginEx()
    {
        return new McpBeginExMatcherBuilder();
    }

    public static final class McpBeginExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024]);
        private final McpBeginExFW.Builder beginExRW = new McpBeginExFW.Builder();

        private McpBeginExBuilder()
        {
            beginExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public McpBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public McpLifecycleBeginExBuilder lifecycle()
        {
            return new McpLifecycleBeginExBuilder();
        }

        public McpToolsListBeginExBuilder toolsList()
        {
            return new McpToolsListBeginExBuilder();
        }

        public McpToolsCallBeginExBuilder toolsCall()
        {
            return new McpToolsCallBeginExBuilder();
        }

        public McpPromptsListBeginExBuilder promptsList()
        {
            return new McpPromptsListBeginExBuilder();
        }

        public McpPromptsGetBeginExBuilder promptsGet()
        {
            return new McpPromptsGetBeginExBuilder();
        }

        public McpResourcesListBeginExBuilder resourcesList()
        {
            return new McpResourcesListBeginExBuilder();
        }

        public McpResourcesReadBeginExBuilder resourcesRead()
        {
            return new McpResourcesReadBeginExBuilder();
        }

        public byte[] build()
        {
            final byte[] array = new byte[beginExRW.limit()];
            writeBuffer.getBytes(0, array);
            return array;
        }

        public final class McpLifecycleBeginExBuilder
        {
            private String sessionId;
            private int capabilities;

            public McpLifecycleBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpLifecycleBeginExBuilder capabilities(
                int capabilities)
            {
                this.capabilities = capabilities;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.lifecycle(b -> b.sessionId(sessionId).capabilities(capabilities));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpToolsListBeginExBuilder
        {
            private String sessionId;

            public McpToolsListBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.toolsList(b -> b.sessionId(sessionId));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpToolsCallBeginExBuilder
        {
            private String sessionId;
            private String name;

            public McpToolsCallBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpToolsCallBeginExBuilder name(
                String name)
            {
                this.name = name;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.toolsCall(b -> b.sessionId(sessionId).name(name));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpPromptsListBeginExBuilder
        {
            private String sessionId;

            public McpPromptsListBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.promptsList(b -> b.sessionId(sessionId));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpPromptsGetBeginExBuilder
        {
            private String sessionId;
            private String name;

            public McpPromptsGetBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpPromptsGetBeginExBuilder name(
                String name)
            {
                this.name = name;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.promptsGet(b -> b.sessionId(sessionId).name(name));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpResourcesListBeginExBuilder
        {
            private String sessionId;

            public McpResourcesListBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.resourcesList(b -> b.sessionId(sessionId));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpResourcesReadBeginExBuilder
        {
            private String sessionId;
            private String uri;

            public McpResourcesReadBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpResourcesReadBeginExBuilder uri(
                String uri)
            {
                this.uri = uri;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.resourcesRead(b -> b.sessionId(sessionId).uri(uri));
                return McpBeginExBuilder.this;
            }
        }
    }

    public static final class McpBeginExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();
        private final McpBeginExFW beginExRO = new McpBeginExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<McpBeginExFW> caseMatcher;

        public McpBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public McpLifecycleBeginExMatcherBuilder lifecycle()
        {
            this.kind = McpBeginExFW.KIND_LIFECYCLE;
            final McpLifecycleBeginExMatcherBuilder matcher = new McpLifecycleBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpToolsListBeginExMatcherBuilder toolsList()
        {
            this.kind = McpBeginExFW.KIND_TOOLS_LIST;
            final McpToolsListBeginExMatcherBuilder matcher = new McpToolsListBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpToolsCallBeginExMatcherBuilder toolsCall()
        {
            this.kind = McpBeginExFW.KIND_TOOLS_CALL;
            final McpToolsCallBeginExMatcherBuilder matcher = new McpToolsCallBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpPromptsListBeginExMatcherBuilder promptsList()
        {
            this.kind = McpBeginExFW.KIND_PROMPTS_LIST;
            final McpPromptsListBeginExMatcherBuilder matcher = new McpPromptsListBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpPromptsGetBeginExMatcherBuilder promptsGet()
        {
            this.kind = McpBeginExFW.KIND_PROMPTS_GET;
            final McpPromptsGetBeginExMatcherBuilder matcher = new McpPromptsGetBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpResourcesListBeginExMatcherBuilder resourcesList()
        {
            this.kind = McpBeginExFW.KIND_RESOURCES_LIST;
            final McpResourcesListBeginExMatcherBuilder matcher = new McpResourcesListBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpResourcesReadBeginExMatcherBuilder resourcesRead()
        {
            this.kind = McpBeginExFW.KIND_RESOURCES_READ;
            final McpResourcesReadBeginExMatcherBuilder matcher = new McpResourcesReadBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private McpBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final McpBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

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
            McpBeginExFW beginEx)
        {
            return typeId == null || typeId == beginEx.typeId();
        }

        private boolean matchKind(
            McpBeginExFW beginEx)
        {
            return kind == null || kind == beginEx.kind();
        }

        private boolean matchCase(
            McpBeginExFW beginEx)
        {
            return caseMatcher == null || caseMatcher.test(beginEx);
        }

        public final class McpLifecycleBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private Integer capabilities;

            public McpLifecycleBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpLifecycleBeginExMatcherBuilder capabilities(
                int capabilities)
            {
                this.capabilities = capabilities;
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpLifecycleBeginExFW lifecycle = beginEx.lifecycle();
                return matchSessionId(lifecycle) && matchCapabilities(lifecycle);
            }

            private boolean matchSessionId(
                McpLifecycleBeginExFW lifecycle)
            {
                return sessionId == null || sessionId.equals(lifecycle.sessionId());
            }

            private boolean matchCapabilities(
                McpLifecycleBeginExFW lifecycle)
            {
                return capabilities == null || capabilities == lifecycle.capabilities();
            }
        }

        public final class McpToolsListBeginExMatcherBuilder
        {
            private String16FW sessionId;

            public McpToolsListBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                return matchSessionId(beginEx.toolsList());
            }

            private boolean matchSessionId(
                McpToolsListBeginExFW toolsList)
            {
                return sessionId == null || sessionId.equals(toolsList.sessionId());
            }
        }

        public final class McpToolsCallBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW name;

            public McpToolsCallBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpToolsCallBeginExMatcherBuilder name(
                String name)
            {
                this.name = new String16FW(name);
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpToolsCallBeginExFW toolsCall = beginEx.toolsCall();
                return matchSessionId(toolsCall) && matchName(toolsCall);
            }

            private boolean matchSessionId(
                McpToolsCallBeginExFW toolsCall)
            {
                return sessionId == null || sessionId.equals(toolsCall.sessionId());
            }

            private boolean matchName(
                McpToolsCallBeginExFW toolsCall)
            {
                return name == null || name.equals(toolsCall.name());
            }
        }

        public final class McpPromptsListBeginExMatcherBuilder
        {
            private String16FW sessionId;

            public McpPromptsListBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                return matchSessionId(beginEx.promptsList());
            }

            private boolean matchSessionId(
                McpPromptsListBeginExFW promptsList)
            {
                return sessionId == null || sessionId.equals(promptsList.sessionId());
            }
        }

        public final class McpPromptsGetBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW name;

            public McpPromptsGetBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpPromptsGetBeginExMatcherBuilder name(
                String name)
            {
                this.name = new String16FW(name);
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpPromptsGetBeginExFW promptsGet = beginEx.promptsGet();
                return matchSessionId(promptsGet) && matchName(promptsGet);
            }

            private boolean matchSessionId(
                McpPromptsGetBeginExFW promptsGet)
            {
                return sessionId == null || sessionId.equals(promptsGet.sessionId());
            }

            private boolean matchName(
                McpPromptsGetBeginExFW promptsGet)
            {
                return name == null || name.equals(promptsGet.name());
            }
        }

        public final class McpResourcesListBeginExMatcherBuilder
        {
            private String16FW sessionId;

            public McpResourcesListBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                return matchSessionId(beginEx.resourcesList());
            }

            private boolean matchSessionId(
                McpResourcesListBeginExFW resourcesList)
            {
                return sessionId == null || sessionId.equals(resourcesList.sessionId());
            }
        }

        public final class McpResourcesReadBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW uri;

            public McpResourcesReadBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpResourcesReadBeginExMatcherBuilder uri(
                String uri)
            {
                this.uri = new String16FW(uri);
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpResourcesReadBeginExFW resourcesRead = beginEx.resourcesRead();
                return matchSessionId(resourcesRead) && matchUri(resourcesRead);
            }

            private boolean matchSessionId(
                McpResourcesReadBeginExFW resourcesRead)
            {
                return sessionId == null || sessionId.equals(resourcesRead.sessionId());
            }

            private boolean matchUri(
                McpResourcesReadBeginExFW resourcesRead)
            {
                return uri == null || uri.equals(resourcesRead.uri());
            }
        }
    }

    @Function
    public static McpAbortExBuilder abortEx()
    {
        return new McpAbortExBuilder();
    }

    @Function
    public static McpAbortExMatcherBuilder matchAbortEx()
    {
        return new McpAbortExMatcherBuilder();
    }

    @Function
    public static McpFlushExBuilder flushEx()
    {
        return new McpFlushExBuilder();
    }

    @Function
    public static McpFlushExMatcherBuilder matchFlushEx()
    {
        return new McpFlushExMatcherBuilder();
    }

    public static final class McpFlushExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024]);
        private final McpFlushExFW.Builder flushExRW = new McpFlushExFW.Builder();

        private McpFlushExBuilder()
        {
            flushExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public McpFlushExBuilder typeId(
            int typeId)
        {
            flushExRW.typeId(typeId);
            return this;
        }

        public McpResumableFlushExBuilder resumable()
        {
            return new McpResumableFlushExBuilder();
        }

        public McpToolsListChangedFlushExBuilder toolsListChanged()
        {
            return new McpToolsListChangedFlushExBuilder();
        }

        public McpPromptsListChangedFlushExBuilder promptsListChanged()
        {
            return new McpPromptsListChangedFlushExBuilder();
        }

        public McpResourcesListChangedFlushExBuilder resourcesListChanged()
        {
            return new McpResourcesListChangedFlushExBuilder();
        }

        public McpProgressFlushExBuilder progress()
        {
            return new McpProgressFlushExBuilder();
        }

        public McpSuspendFlushExBuilder suspend()
        {
            return new McpSuspendFlushExBuilder();
        }

        public byte[] build()
        {
            final byte[] array = new byte[flushExRW.limit()];
            writeBuffer.getBytes(0, array);
            return array;
        }

        public final class McpResumableFlushExBuilder
        {
            private String id;

            public McpResumableFlushExBuilder id(
                String id)
            {
                this.id = id;
                return this;
            }

            public McpFlushExBuilder build()
            {
                flushExRW.resumable(b -> b.id(id));
                return McpFlushExBuilder.this;
            }
        }

        public final class McpToolsListChangedFlushExBuilder
        {
            private String id;

            public McpToolsListChangedFlushExBuilder id(
                String id)
            {
                this.id = id;
                return this;
            }

            public McpFlushExBuilder build()
            {
                flushExRW.toolsListChanged(b -> b.id(id));
                return McpFlushExBuilder.this;
            }
        }

        public final class McpPromptsListChangedFlushExBuilder
        {
            private String id;

            public McpPromptsListChangedFlushExBuilder id(
                String id)
            {
                this.id = id;
                return this;
            }

            public McpFlushExBuilder build()
            {
                flushExRW.promptsListChanged(b -> b.id(id));
                return McpFlushExBuilder.this;
            }
        }

        public final class McpResourcesListChangedFlushExBuilder
        {
            private String id;

            public McpResourcesListChangedFlushExBuilder id(
                String id)
            {
                this.id = id;
                return this;
            }

            public McpFlushExBuilder build()
            {
                flushExRW.resourcesListChanged(b -> b.id(id));
                return McpFlushExBuilder.this;
            }
        }

        public final class McpProgressFlushExBuilder
        {
            private String id;
            private String token;
            private long progress;
            private long total = -1L;
            private String message;

            public McpProgressFlushExBuilder id(
                String id)
            {
                this.id = id;
                return this;
            }

            public McpProgressFlushExBuilder token(
                String token)
            {
                this.token = token;
                return this;
            }

            public McpProgressFlushExBuilder progress(
                long progress)
            {
                this.progress = progress;
                return this;
            }

            public McpProgressFlushExBuilder total(
                long total)
            {
                this.total = total;
                return this;
            }

            public McpProgressFlushExBuilder message(
                String message)
            {
                this.message = message;
                return this;
            }

            public McpFlushExBuilder build()
            {
                flushExRW.progress(b -> b
                    .id(id)
                    .token(token)
                    .progress(progress)
                    .total(total)
                    .message(message));
                return McpFlushExBuilder.this;
            }
        }

        public final class McpSuspendFlushExBuilder
        {
            private long retry = 0L;

            public McpSuspendFlushExBuilder retry(
                long retry)
            {
                this.retry = retry;
                return this;
            }

            public McpFlushExBuilder build()
            {
                flushExRW.suspend(b -> b.retry(retry));
                return McpFlushExBuilder.this;
            }
        }
    }

    public static final class McpFlushExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();
        private final McpFlushExFW flushExRO = new McpFlushExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<McpFlushExFW> caseMatcher;

        public McpFlushExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public McpResumableFlushExMatcherBuilder resumable()
        {
            this.kind = McpFlushExFW.KIND_RESUMABLE;
            final McpResumableFlushExMatcherBuilder matcher = new McpResumableFlushExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpToolsListChangedFlushExMatcherBuilder toolsListChanged()
        {
            this.kind = McpFlushExFW.KIND_TOOLS_LIST_CHANGED;
            final McpToolsListChangedFlushExMatcherBuilder matcher = new McpToolsListChangedFlushExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpPromptsListChangedFlushExMatcherBuilder promptsListChanged()
        {
            this.kind = McpFlushExFW.KIND_PROMPTS_LIST_CHANGED;
            final McpPromptsListChangedFlushExMatcherBuilder matcher = new McpPromptsListChangedFlushExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpResourcesListChangedFlushExMatcherBuilder resourcesListChanged()
        {
            this.kind = McpFlushExFW.KIND_RESOURCES_LIST_CHANGED;
            final McpResourcesListChangedFlushExMatcherBuilder matcher = new McpResourcesListChangedFlushExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpProgressFlushExMatcherBuilder progress()
        {
            this.kind = McpFlushExFW.KIND_PROGRESS;
            final McpProgressFlushExMatcherBuilder matcher = new McpProgressFlushExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpSuspendFlushExMatcherBuilder suspend()
        {
            this.kind = McpFlushExFW.KIND_SUSPEND;
            final McpSuspendFlushExMatcherBuilder matcher = new McpSuspendFlushExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private McpFlushExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final McpFlushExFW flushEx = flushExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (flushEx != null &&
                matchTypeId(flushEx) &&
                matchKind(flushEx) &&
                matchCase(flushEx))
            {
                byteBuf.position(byteBuf.position() + flushEx.sizeof());
                return flushEx;
            }

            throw new Exception(flushEx != null ? flushEx.toString() : "null");
        }

        private boolean matchTypeId(
            McpFlushExFW flushEx)
        {
            return typeId == null || typeId == flushEx.typeId();
        }

        private boolean matchKind(
            McpFlushExFW flushEx)
        {
            return kind == null || kind == flushEx.kind();
        }

        private boolean matchCase(
            McpFlushExFW flushEx)
        {
            return caseMatcher == null || caseMatcher.test(flushEx);
        }

        public final class McpResumableFlushExMatcherBuilder
        {
            private String16FW id;

            public McpResumableFlushExMatcherBuilder id(
                String id)
            {
                this.id = new String16FW(id);
                return this;
            }

            public McpFlushExMatcherBuilder build()
            {
                return McpFlushExMatcherBuilder.this;
            }

            private boolean match(
                McpFlushExFW flushEx)
            {
                return matchId(flushEx.resumable());
            }

            private boolean matchId(
                McpResumableFlushExFW resumable)
            {
                return id == null || id.equals(resumable.id());
            }
        }

        public final class McpToolsListChangedFlushExMatcherBuilder
        {
            private String16FW id;

            public McpToolsListChangedFlushExMatcherBuilder id(
                String id)
            {
                this.id = new String16FW(id);
                return this;
            }

            public McpFlushExMatcherBuilder build()
            {
                return McpFlushExMatcherBuilder.this;
            }

            private boolean match(
                McpFlushExFW flushEx)
            {
                return matchId(flushEx.toolsListChanged());
            }

            private boolean matchId(
                McpToolsListChangedFlushExFW toolsListChanged)
            {
                return id == null || id.equals(toolsListChanged.id());
            }
        }

        public final class McpPromptsListChangedFlushExMatcherBuilder
        {
            private String16FW id;

            public McpPromptsListChangedFlushExMatcherBuilder id(
                String id)
            {
                this.id = new String16FW(id);
                return this;
            }

            public McpFlushExMatcherBuilder build()
            {
                return McpFlushExMatcherBuilder.this;
            }

            private boolean match(
                McpFlushExFW flushEx)
            {
                return matchId(flushEx.promptsListChanged());
            }

            private boolean matchId(
                McpPromptsListChangedFlushExFW promptsListChanged)
            {
                return id == null || id.equals(promptsListChanged.id());
            }
        }

        public final class McpResourcesListChangedFlushExMatcherBuilder
        {
            private String16FW id;

            public McpResourcesListChangedFlushExMatcherBuilder id(
                String id)
            {
                this.id = new String16FW(id);
                return this;
            }

            public McpFlushExMatcherBuilder build()
            {
                return McpFlushExMatcherBuilder.this;
            }

            private boolean match(
                McpFlushExFW flushEx)
            {
                return matchId(flushEx.resourcesListChanged());
            }

            private boolean matchId(
                McpResourcesListChangedFlushExFW resourcesListChanged)
            {
                return id == null || id.equals(resourcesListChanged.id());
            }
        }

        public final class McpProgressFlushExMatcherBuilder
        {
            private String16FW id;
            private String16FW token;
            private Long progress;
            private Long total;
            private String16FW message;

            public McpProgressFlushExMatcherBuilder id(
                String id)
            {
                this.id = new String16FW(id);
                return this;
            }

            public McpProgressFlushExMatcherBuilder token(
                String token)
            {
                this.token = new String16FW(token);
                return this;
            }

            public McpProgressFlushExMatcherBuilder progress(
                long progress)
            {
                this.progress = progress;
                return this;
            }

            public McpProgressFlushExMatcherBuilder total(
                long total)
            {
                this.total = total;
                return this;
            }

            public McpProgressFlushExMatcherBuilder message(
                String message)
            {
                this.message = new String16FW(message);
                return this;
            }

            public McpFlushExMatcherBuilder build()
            {
                return McpFlushExMatcherBuilder.this;
            }

            private boolean match(
                McpFlushExFW flushEx)
            {
                final McpProgressFlushExFW progress = flushEx.progress();
                return matchId(progress) && matchToken(progress) && matchProgress(progress) &&
                    matchTotal(progress) && matchMessage(progress);
            }

            private boolean matchId(
                McpProgressFlushExFW progress)
            {
                return id == null || id.equals(progress.id());
            }

            private boolean matchToken(
                McpProgressFlushExFW progress)
            {
                return token == null || token.equals(progress.token());
            }

            private boolean matchProgress(
                McpProgressFlushExFW progress)
            {
                return this.progress == null || this.progress == progress.progress();
            }

            private boolean matchTotal(
                McpProgressFlushExFW progress)
            {
                return total == null || total == progress.total();
            }

            private boolean matchMessage(
                McpProgressFlushExFW progress)
            {
                return message == null || message.equals(progress.message());
            }
        }

        public final class McpSuspendFlushExMatcherBuilder
        {
            private Long retry;

            public McpSuspendFlushExMatcherBuilder retry(
                long retry)
            {
                this.retry = retry;
                return this;
            }

            public McpFlushExMatcherBuilder build()
            {
                return McpFlushExMatcherBuilder.this;
            }

            private boolean match(
                McpFlushExFW flushEx)
            {
                return matchRetry(flushEx.suspend());
            }

            private boolean matchRetry(
                McpSuspendFlushExFW suspend)
            {
                return retry == null || retry == suspend.retry();
            }
        }
    }

    @Function
    public static McpChallengeExBuilder challengeEx()
    {
        return new McpChallengeExBuilder();
    }

    @Function
    public static McpChallengeExMatcherBuilder matchChallengeEx()
    {
        return new McpChallengeExMatcherBuilder();
    }

    public static final class McpChallengeExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024]);
        private final McpChallengeExFW.Builder challengeExRW = new McpChallengeExFW.Builder();

        private McpChallengeExBuilder()
        {
            challengeExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public McpChallengeExBuilder typeId(
            int typeId)
        {
            challengeExRW.typeId(typeId);
            return this;
        }

        public McpResumeChallengeExBuilder resume()
        {
            return new McpResumeChallengeExBuilder();
        }

        public McpSuspendedChallengeExBuilder suspended()
        {
            return new McpSuspendedChallengeExBuilder();
        }

        public byte[] build()
        {
            final byte[] array = new byte[challengeExRW.limit()];
            writeBuffer.getBytes(0, array);
            return array;
        }

        public final class McpResumeChallengeExBuilder
        {
            private String id;

            public McpResumeChallengeExBuilder id(
                String id)
            {
                this.id = id;
                return this;
            }

            public McpChallengeExBuilder build()
            {
                challengeExRW.resume(b -> b.id(id));
                return McpChallengeExBuilder.this;
            }
        }

        public final class McpSuspendedChallengeExBuilder
        {
            public McpChallengeExBuilder build()
            {
                challengeExRW.suspended(b ->
                {
                });
                return McpChallengeExBuilder.this;
            }
        }
    }

    public static final class McpChallengeExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();
        private final McpChallengeExFW challengeExRO = new McpChallengeExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<McpChallengeExFW> caseMatcher;

        public McpChallengeExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public McpResumeChallengeExMatcherBuilder resume()
        {
            this.kind = McpChallengeExFW.KIND_RESUME;
            final McpResumeChallengeExMatcherBuilder matcher = new McpResumeChallengeExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpSuspendedChallengeExMatcherBuilder suspended()
        {
            this.kind = McpChallengeExFW.KIND_SUSPENDED;
            final McpSuspendedChallengeExMatcherBuilder matcher = new McpSuspendedChallengeExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private McpChallengeExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final McpChallengeExFW challengeEx = challengeExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (challengeEx != null &&
                matchTypeId(challengeEx) &&
                matchKind(challengeEx) &&
                matchCase(challengeEx))
            {
                byteBuf.position(byteBuf.position() + challengeEx.sizeof());
                return challengeEx;
            }

            throw new Exception(challengeEx != null ? challengeEx.toString() : "null");
        }

        private boolean matchTypeId(
            McpChallengeExFW challengeEx)
        {
            return typeId == null || typeId == challengeEx.typeId();
        }

        private boolean matchKind(
            McpChallengeExFW challengeEx)
        {
            return kind == null || kind == challengeEx.kind();
        }

        private boolean matchCase(
            McpChallengeExFW challengeEx)
        {
            return caseMatcher == null || caseMatcher.test(challengeEx);
        }

        public final class McpResumeChallengeExMatcherBuilder
        {
            private String16FW id;

            public McpResumeChallengeExMatcherBuilder id(
                String id)
            {
                this.id = new String16FW(id);
                return this;
            }

            public McpChallengeExMatcherBuilder build()
            {
                return McpChallengeExMatcherBuilder.this;
            }

            private boolean match(
                McpChallengeExFW challengeEx)
            {
                return matchId(challengeEx.resume());
            }

            private boolean matchId(
                McpResumeChallengeExFW resume)
            {
                return id == null || id.equals(resume.id());
            }
        }

        public final class McpSuspendedChallengeExMatcherBuilder
        {
            public McpChallengeExMatcherBuilder build()
            {
                return McpChallengeExMatcherBuilder.this;
            }

            private boolean match(
                McpChallengeExFW challengeEx)
            {
                return challengeEx.kind() == McpChallengeExFW.KIND_SUSPENDED;
            }
        }
    }

    public static final class McpAbortExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[256]);
        private final McpAbortExFW.Builder abortExRW = new McpAbortExFW.Builder();

        private McpAbortExBuilder()
        {
            abortExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public McpAbortExBuilder typeId(
            int typeId)
        {
            abortExRW.typeId(typeId);
            return this;
        }

        public McpAbortExBuilder reason(
            String reason)
        {
            abortExRW.reason(reason);
            return this;
        }

        public byte[] build()
        {
            final McpAbortExFW abortEx = abortExRW.build();
            final byte[] array = new byte[abortEx.sizeof()];
            writeBuffer.getBytes(abortEx.offset(), array);
            return array;
        }
    }

    public static final class McpAbortExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();
        private final McpAbortExFW abortExRO = new McpAbortExFW();

        private Integer typeId;
        private String16FW reason;

        public McpAbortExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public McpAbortExMatcherBuilder reason(
            String reason)
        {
            this.reason = new String16FW(reason);
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null || reason != null ? this::match : buf -> null;
        }

        private McpAbortExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final McpAbortExFW abortEx = abortExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

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
            McpAbortExFW abortEx)
        {
            return typeId == null || typeId == abortEx.typeId();
        }

        private boolean matchReason(
            McpAbortExFW abortEx)
        {
            return reason == null || reason.equals(abortEx.reason());
        }
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {
        public Mapper()
        {
            super(McpFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "mcp";
        }
    }

    private McpFunctions()
    {
        // utility
    }
}
