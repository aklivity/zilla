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

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.specs.binding.mcp.internal.types.String16FW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpAbortExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpLifecycleBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpPromptsGetBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpPromptsListBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpResourcesListBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpResourcesReadBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpToolsCallBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpToolsListBeginExFW;

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
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[1024]);
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
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
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

    public static final class McpAbortExBuilder
    {
        private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[256]);
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
        private final DirectBufferEx bufferRO = new UnsafeBufferEx();
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
