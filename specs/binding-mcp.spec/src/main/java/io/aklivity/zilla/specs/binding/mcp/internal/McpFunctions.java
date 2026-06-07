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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.specs.binding.mcp.internal.types.McpCapabilities;
import io.aklivity.zilla.specs.binding.mcp.internal.types.String16FW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.String8FW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpAbortExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpBearerError;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpBearerResetExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpChallengeExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpElicitAction;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpElicitCallbackFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpElicitCompleteFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpElicitCreateChallengeExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpElicitResponseFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpElicitStatus;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpEndExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpLifecycleBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpOutcome;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpProgressFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpPromptsGetBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpPromptsListBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpPromptsListChangedFlushExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpResetExFW;
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

    private static int capabilities(
        String[] names)
    {
        int capabilities = 0;
        for (String name : names)
        {
            capabilities |= McpCapabilities.valueOf(name).value();
        }
        return capabilities;
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
                String... names)
            {
                this.capabilities = McpFunctions.capabilities(names);
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
            private long timeout;

            public McpToolsListBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpToolsListBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.toolsList(b -> b.sessionId(sessionId).timeout(timeout));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpToolsCallBeginExBuilder
        {
            private String sessionId;
            private String name;
            private int contentLength = -1;
            private long timeout;

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

            public McpToolsCallBeginExBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public McpToolsCallBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.toolsCall(b -> b.sessionId(sessionId).name(name).contentLength(contentLength).timeout(timeout));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpPromptsListBeginExBuilder
        {
            private String sessionId;
            private long timeout;

            public McpPromptsListBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpPromptsListBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.promptsList(b -> b.sessionId(sessionId).timeout(timeout));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpPromptsGetBeginExBuilder
        {
            private String sessionId;
            private String name;
            private int contentLength = -1;
            private long timeout;

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

            public McpPromptsGetBeginExBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public McpPromptsGetBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.promptsGet(b -> b.sessionId(sessionId).name(name).contentLength(contentLength).timeout(timeout));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpResourcesListBeginExBuilder
        {
            private String sessionId;
            private long timeout;

            public McpResourcesListBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpResourcesListBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.resourcesList(b -> b.sessionId(sessionId).timeout(timeout));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpResourcesReadBeginExBuilder
        {
            private String sessionId;
            private String uri;
            private int contentLength = -1;
            private long timeout;

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

            public McpResourcesReadBeginExBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public McpResourcesReadBeginExBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.resourcesRead(b -> b.sessionId(sessionId).uri(uri).contentLength(contentLength).timeout(timeout));
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
                String... names)
            {
                this.capabilities = McpFunctions.capabilities(names);
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
            private Long timeout;

            public McpToolsListBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpToolsListBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpToolsListBeginExFW toolsList = beginEx.toolsList();
                return matchSessionId(toolsList) && matchTimeout(toolsList);
            }

            private boolean matchSessionId(
                McpToolsListBeginExFW toolsList)
            {
                return sessionId == null || sessionId.equals(toolsList.sessionId());
            }

            private boolean matchTimeout(
                McpToolsListBeginExFW toolsList)
            {
                return timeout == null || timeout == toolsList.timeout();
            }
        }

        public final class McpToolsCallBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW name;
            private Integer contentLength;
            private Long timeout;

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

            public McpToolsCallBeginExMatcherBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public McpToolsCallBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
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
                return matchSessionId(toolsCall) && matchName(toolsCall) && matchContentLength(toolsCall) &&
                    matchTimeout(toolsCall);
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

            private boolean matchContentLength(
                McpToolsCallBeginExFW toolsCall)
            {
                return contentLength == null || contentLength == toolsCall.contentLength();
            }

            private boolean matchTimeout(
                McpToolsCallBeginExFW toolsCall)
            {
                return timeout == null || timeout == toolsCall.timeout();
            }
        }

        public final class McpPromptsListBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private Long timeout;

            public McpPromptsListBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpPromptsListBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpPromptsListBeginExFW promptsList = beginEx.promptsList();
                return matchSessionId(promptsList) && matchTimeout(promptsList);
            }

            private boolean matchSessionId(
                McpPromptsListBeginExFW promptsList)
            {
                return sessionId == null || sessionId.equals(promptsList.sessionId());
            }

            private boolean matchTimeout(
                McpPromptsListBeginExFW promptsList)
            {
                return timeout == null || timeout == promptsList.timeout();
            }
        }

        public final class McpPromptsGetBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW name;
            private Integer contentLength;
            private Long timeout;

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

            public McpPromptsGetBeginExMatcherBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public McpPromptsGetBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
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
                return matchSessionId(promptsGet) && matchName(promptsGet) && matchContentLength(promptsGet) &&
                    matchTimeout(promptsGet);
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

            private boolean matchContentLength(
                McpPromptsGetBeginExFW promptsGet)
            {
                return contentLength == null || contentLength == promptsGet.contentLength();
            }

            private boolean matchTimeout(
                McpPromptsGetBeginExFW promptsGet)
            {
                return timeout == null || timeout == promptsGet.timeout();
            }
        }

        public final class McpResourcesListBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private Long timeout;

            public McpResourcesListBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpResourcesListBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpResourcesListBeginExFW resourcesList = beginEx.resourcesList();
                return matchSessionId(resourcesList) && matchTimeout(resourcesList);
            }

            private boolean matchSessionId(
                McpResourcesListBeginExFW resourcesList)
            {
                return sessionId == null || sessionId.equals(resourcesList.sessionId());
            }

            private boolean matchTimeout(
                McpResourcesListBeginExFW resourcesList)
            {
                return timeout == null || timeout == resourcesList.timeout();
            }
        }

        public final class McpResourcesReadBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW uri;
            private Integer contentLength;
            private Long timeout;

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

            public McpResourcesReadBeginExMatcherBuilder contentLength(
                int contentLength)
            {
                this.contentLength = contentLength;
                return this;
            }

            public McpResourcesReadBeginExMatcherBuilder timeout(
                long timeout)
            {
                this.timeout = timeout;
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
                return matchSessionId(resourcesRead) && matchUri(resourcesRead) && matchContentLength(resourcesRead) &&
                    matchTimeout(resourcesRead);
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

            private boolean matchContentLength(
                McpResourcesReadBeginExFW resourcesRead)
            {
                return contentLength == null || contentLength == resourcesRead.contentLength();
            }

            private boolean matchTimeout(
                McpResourcesReadBeginExFW resourcesRead)
            {
                return timeout == null || timeout == resourcesRead.timeout();
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
    public static McpEndExBuilder endEx()
    {
        return new McpEndExBuilder();
    }

    @Function
    public static McpEndExMatcherBuilder matchEndEx()
    {
        return new McpEndExMatcherBuilder();
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

        public McpElicitCallbackFlushExBuilder elicitCallback()
        {
            return new McpElicitCallbackFlushExBuilder();
        }

        public McpElicitCompleteFlushExBuilder elicitComplete()
        {
            return new McpElicitCompleteFlushExBuilder();
        }

        public McpElicitResponseFlushExBuilder elicitResponse()
        {
            return new McpElicitResponseFlushExBuilder();
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

        public final class McpElicitCallbackFlushExBuilder
        {
            private String url;
            private String context;

            public McpElicitCallbackFlushExBuilder url(
                String url)
            {
                this.url = url;
                return this;
            }

            public McpElicitCallbackFlushExBuilder context(
                String context)
            {
                this.context = context;
                return this;
            }

            public McpFlushExBuilder build()
            {
                flushExRW.elicitCallback(b -> b.url(url).context(context));
                return McpFlushExBuilder.this;
            }
        }

        public final class McpElicitCompleteFlushExBuilder
        {
            private String id;
            private McpElicitStatus status;

            public McpElicitCompleteFlushExBuilder id(
                String id)
            {
                this.id = id;
                return this;
            }

            public McpElicitCompleteFlushExBuilder status(
                String status)
            {
                this.status = McpElicitStatus.valueOf(status);
                return this;
            }

            public McpFlushExBuilder build()
            {
                flushExRW.elicitComplete(b -> b.id(id).status(s -> s.set(status)));
                return McpFlushExBuilder.this;
            }
        }

        public final class McpElicitResponseFlushExBuilder
        {
            private String correlationId;
            private McpElicitAction action;

            public McpElicitResponseFlushExBuilder correlationId(
                String correlationId)
            {
                this.correlationId = correlationId;
                return this;
            }

            public McpElicitResponseFlushExBuilder action(
                String action)
            {
                this.action = McpElicitAction.valueOf(action);
                return this;
            }

            public McpFlushExBuilder build()
            {
                flushExRW.elicitResponse(b -> b.correlationId(correlationId).action(a -> a.set(action)));
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

        public McpElicitCallbackFlushExMatcherBuilder elicitCallback()
        {
            this.kind = McpFlushExFW.KIND_ELICIT_CALLBACK;
            final McpElicitCallbackFlushExMatcherBuilder matcher = new McpElicitCallbackFlushExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpElicitCompleteFlushExMatcherBuilder elicitComplete()
        {
            this.kind = McpFlushExFW.KIND_ELICIT_COMPLETE;
            final McpElicitCompleteFlushExMatcherBuilder matcher = new McpElicitCompleteFlushExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpElicitResponseFlushExMatcherBuilder elicitResponse()
        {
            this.kind = McpFlushExFW.KIND_ELICIT_RESPONSE;
            final McpElicitResponseFlushExMatcherBuilder matcher = new McpElicitResponseFlushExMatcherBuilder();
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
                this.id = new String16FW(id, UTF_8);
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
                this.id = new String16FW(id, UTF_8);
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
                this.id = new String16FW(id, UTF_8);
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

        public final class McpElicitCallbackFlushExMatcherBuilder
        {
            private String16FW url;
            private String8FW context;

            public McpElicitCallbackFlushExMatcherBuilder url(
                String url)
            {
                this.url = new String16FW(url);
                return this;
            }

            public McpElicitCallbackFlushExMatcherBuilder context(
                String context)
            {
                this.context = new String8FW(context);
                return this;
            }

            public McpFlushExMatcherBuilder build()
            {
                return McpFlushExMatcherBuilder.this;
            }

            private boolean match(
                McpFlushExFW flushEx)
            {
                final McpElicitCallbackFlushExFW elicitCallback = flushEx.elicitCallback();
                return matchUrl(elicitCallback) && matchContext(elicitCallback);
            }

            private boolean matchUrl(
                McpElicitCallbackFlushExFW elicitCallback)
            {
                return url == null || url.equals(elicitCallback.url());
            }

            private boolean matchContext(
                McpElicitCallbackFlushExFW elicitCallback)
            {
                return context == null || context.equals(elicitCallback.context());
            }
        }

        public final class McpElicitCompleteFlushExMatcherBuilder
        {
            private String16FW id;
            private McpElicitStatus status;

            public McpElicitCompleteFlushExMatcherBuilder id(
                String id)
            {
                this.id = new String16FW(id);
                return this;
            }

            public McpElicitCompleteFlushExMatcherBuilder status(
                String status)
            {
                this.status = McpElicitStatus.valueOf(status);
                return this;
            }

            public McpFlushExMatcherBuilder build()
            {
                return McpFlushExMatcherBuilder.this;
            }

            private boolean match(
                McpFlushExFW flushEx)
            {
                final McpElicitCompleteFlushExFW elicitComplete = flushEx.elicitComplete();
                return matchId(elicitComplete) && matchStatus(elicitComplete);
            }

            private boolean matchId(
                McpElicitCompleteFlushExFW elicitComplete)
            {
                return id == null || id.equals(elicitComplete.id());
            }

            private boolean matchStatus(
                McpElicitCompleteFlushExFW elicitComplete)
            {
                return status == null || status == elicitComplete.status().get();
            }
        }

        public final class McpElicitResponseFlushExMatcherBuilder
        {
            private String16FW correlationId;
            private McpElicitAction action;

            public McpElicitResponseFlushExMatcherBuilder correlationId(
                String correlationId)
            {
                this.correlationId = new String16FW(correlationId);
                return this;
            }

            public McpElicitResponseFlushExMatcherBuilder action(
                String action)
            {
                this.action = McpElicitAction.valueOf(action);
                return this;
            }

            public McpFlushExMatcherBuilder build()
            {
                return McpFlushExMatcherBuilder.this;
            }

            private boolean match(
                McpFlushExFW flushEx)
            {
                final McpElicitResponseFlushExFW elicitResponse = flushEx.elicitResponse();
                return matchCorrelationId(elicitResponse) && matchAction(elicitResponse);
            }

            private boolean matchCorrelationId(
                McpElicitResponseFlushExFW elicitResponse)
            {
                return correlationId == null || correlationId.equals(elicitResponse.correlationId());
            }

            private boolean matchAction(
                McpElicitResponseFlushExFW elicitResponse)
            {
                return action == null || action == elicitResponse.action().get();
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

    @Function
    public static McpResetExBuilder resetEx()
    {
        return new McpResetExBuilder();
    }

    @Function
    public static McpResetExMatcherBuilder matchResetEx()
    {
        return new McpResetExMatcherBuilder();
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

        public McpElicitCreateChallengeExBuilder elicitCreate()
        {
            return new McpElicitCreateChallengeExBuilder();
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

        public final class McpElicitCreateChallengeExBuilder
        {
            private String id;
            private String url;
            private String context;
            private String message;
            private String correlationId;

            public McpElicitCreateChallengeExBuilder id(
                String id)
            {
                this.id = id;
                return this;
            }

            public McpElicitCreateChallengeExBuilder url(
                String url)
            {
                this.url = url;
                return this;
            }

            public McpElicitCreateChallengeExBuilder context(
                String context)
            {
                this.context = context;
                return this;
            }

            public McpElicitCreateChallengeExBuilder message(
                String message)
            {
                this.message = message;
                return this;
            }

            public McpElicitCreateChallengeExBuilder correlationId(
                String correlationId)
            {
                this.correlationId = correlationId;
                return this;
            }

            public McpChallengeExBuilder build()
            {
                challengeExRW.elicitCreate(b -> b.id(id).url(url).context(context).message(message).correlationId(correlationId));
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

        public McpElicitCreateChallengeExMatcherBuilder elicitCreate()
        {
            this.kind = McpChallengeExFW.KIND_ELICIT_CREATE;
            final McpElicitCreateChallengeExMatcherBuilder matcher = new McpElicitCreateChallengeExMatcherBuilder();
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

        public final class McpElicitCreateChallengeExMatcherBuilder
        {
            private String16FW id;
            private String16FW url;
            private String8FW context;
            private String16FW message;
            private String16FW correlationId;

            public McpElicitCreateChallengeExMatcherBuilder id(
                String id)
            {
                this.id = new String16FW(id);
                return this;
            }

            public McpElicitCreateChallengeExMatcherBuilder url(
                String url)
            {
                this.url = new String16FW(url);
                return this;
            }

            public McpElicitCreateChallengeExMatcherBuilder context(
                String context)
            {
                this.context = new String8FW(context);
                return this;
            }

            public McpElicitCreateChallengeExMatcherBuilder message(
                String message)
            {
                this.message = new String16FW(message);
                return this;
            }

            public McpElicitCreateChallengeExMatcherBuilder correlationId(
                String correlationId)
            {
                this.correlationId = new String16FW(correlationId);
                return this;
            }

            public McpChallengeExMatcherBuilder build()
            {
                return McpChallengeExMatcherBuilder.this;
            }

            private boolean match(
                McpChallengeExFW challengeEx)
            {
                final McpElicitCreateChallengeExFW elicitCreate = challengeEx.elicitCreate();
                return matchId(elicitCreate) && matchUrl(elicitCreate) &&
                    matchContext(elicitCreate) && matchMessage(elicitCreate) && matchCorrelationId(elicitCreate);
            }

            private boolean matchId(
                McpElicitCreateChallengeExFW elicitCreate)
            {
                return id == null || id.equals(elicitCreate.id());
            }

            private boolean matchUrl(
                McpElicitCreateChallengeExFW elicitCreate)
            {
                return url == null || url.equals(elicitCreate.url());
            }

            private boolean matchContext(
                McpElicitCreateChallengeExFW elicitCreate)
            {
                return context == null || context.equals(elicitCreate.context());
            }

            private boolean matchMessage(
                McpElicitCreateChallengeExFW elicitCreate)
            {
                return message == null || message.equals(elicitCreate.message());
            }

            private boolean matchCorrelationId(
                McpElicitCreateChallengeExFW elicitCreate)
            {
                return correlationId == null || correlationId.equals(elicitCreate.correlationId());
            }
        }
    }

    public static final class McpResetExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024]);
        private final McpResetExFW.Builder resetExRW = new McpResetExFW.Builder();

        private McpResetExBuilder()
        {
            resetExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public McpResetExBuilder typeId(
            int typeId)
        {
            resetExRW.typeId(typeId);
            return this;
        }

        public McpBearerResetExBuilder bearer()
        {
            return new McpBearerResetExBuilder();
        }

        public byte[] build()
        {
            final byte[] array = new byte[resetExRW.limit()];
            writeBuffer.getBytes(0, array);
            return array;
        }

        public final class McpBearerResetExBuilder
        {
            private String realm;
            private String scopes;
            private String resourceMetadata;
            private McpBearerError error;

            public McpBearerResetExBuilder realm(
                String realm)
            {
                this.realm = realm;
                return this;
            }

            public McpBearerResetExBuilder scopes(
                String scopes)
            {
                this.scopes = scopes;
                return this;
            }

            public McpBearerResetExBuilder resourceMetadata(
                String resourceMetadata)
            {
                this.resourceMetadata = resourceMetadata;
                return this;
            }

            public McpBearerResetExBuilder error(
                String error)
            {
                this.error = McpBearerError.valueOf(error);
                return this;
            }

            public McpResetExBuilder build()
            {
                resetExRW.bearer(b -> b.realm(realm).scopes(scopes).resourceMetadata(resourceMetadata).error(s -> s.set(error)));
                return McpResetExBuilder.this;
            }
        }
    }

    public static final class McpResetExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();
        private final McpResetExFW resetExRO = new McpResetExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<McpResetExFW> caseMatcher;

        public McpResetExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public McpBearerResetExMatcherBuilder bearer()
        {
            this.kind = McpResetExFW.KIND_BEARER;
            final McpBearerResetExMatcherBuilder matcher = new McpBearerResetExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private McpResetExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final McpResetExFW resetEx = resetExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

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
            McpResetExFW resetEx)
        {
            return typeId == null || typeId == resetEx.typeId();
        }

        private boolean matchKind(
            McpResetExFW resetEx)
        {
            return kind == null || kind == resetEx.kind();
        }

        private boolean matchCase(
            McpResetExFW resetEx)
        {
            return caseMatcher == null || caseMatcher.test(resetEx);
        }

        public final class McpBearerResetExMatcherBuilder
        {
            private String16FW realm;
            private String16FW scopes;
            private String16FW resourceMetadata;
            private McpBearerError error;

            public McpBearerResetExMatcherBuilder realm(
                String realm)
            {
                this.realm = new String16FW(realm);
                return this;
            }

            public McpBearerResetExMatcherBuilder scopes(
                String scopes)
            {
                this.scopes = new String16FW(scopes);
                return this;
            }

            public McpBearerResetExMatcherBuilder resourceMetadata(
                String resourceMetadata)
            {
                this.resourceMetadata = new String16FW(resourceMetadata);
                return this;
            }

            public McpBearerResetExMatcherBuilder error(
                String error)
            {
                this.error = McpBearerError.valueOf(error);
                return this;
            }

            public McpResetExMatcherBuilder build()
            {
                return McpResetExMatcherBuilder.this;
            }

            private boolean match(
                McpResetExFW resetEx)
            {
                final McpBearerResetExFW bearer = resetEx.bearer();
                return matchRealm(bearer) && matchScopes(bearer) && matchResourceMetadata(bearer) && matchError(bearer);
            }

            private boolean matchRealm(
                McpBearerResetExFW bearer)
            {
                return realm == null || realm.equals(bearer.realm());
            }

            private boolean matchScopes(
                McpBearerResetExFW bearer)
            {
                return scopes == null || scopes.equals(bearer.scopes());
            }

            private boolean matchResourceMetadata(
                McpBearerResetExFW bearer)
            {
                return resourceMetadata == null || resourceMetadata.equals(bearer.resourceMetadata());
            }

            private boolean matchError(
                McpBearerResetExFW bearer)
            {
                return error == null || error == bearer.error().get();
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

    public static final class McpEndExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[256]);
        private final McpEndExFW.Builder endExRW = new McpEndExFW.Builder();

        private McpEndExBuilder()
        {
            endExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public McpEndExBuilder typeId(
            int typeId)
        {
            endExRW.typeId(typeId);
            return this;
        }

        public McpEndExBuilder outcome(
            String outcome)
        {
            final McpOutcome resolved = McpOutcome.valueOf(outcome);
            endExRW.outcome(o -> o.set(resolved));
            return this;
        }

        public byte[] build()
        {
            final McpEndExFW endEx = endExRW.build();
            final byte[] array = new byte[endEx.sizeof()];
            writeBuffer.getBytes(endEx.offset(), array);
            return array;
        }
    }

    public static final class McpEndExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();
        private final McpEndExFW endExRO = new McpEndExFW();

        private Integer typeId;
        private McpOutcome outcome;

        public McpEndExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public McpEndExMatcherBuilder outcome(
            String outcome)
        {
            this.outcome = McpOutcome.valueOf(outcome);
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null || outcome != null ? this::match : buf -> null;
        }

        private McpEndExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final McpEndExFW endEx = endExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

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
            McpEndExFW endEx)
        {
            return typeId == null || typeId == endEx.typeId();
        }

        private boolean matchOutcome(
            McpEndExFW endEx)
        {
            return outcome == null || outcome == endEx.outcome().get();
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
