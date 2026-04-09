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

import java.nio.ByteBuffer;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.specs.binding.mcp.internal.types.String16FW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpCancelBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpCompletionBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpDisconnectBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpInitializeBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpLoggingBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpPingBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpPromptBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpPromptsBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpResourceBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpResourcesBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpToolBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpToolsBeginExFW;

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

        public McpInitializeBeginExBuilder initialize()
        {
            return new McpInitializeBeginExBuilder();
        }

        public McpPingBeginExBuilder ping()
        {
            return new McpPingBeginExBuilder();
        }

        public McpToolsBeginExBuilder tools()
        {
            return new McpToolsBeginExBuilder();
        }

        public McpToolBeginExBuilder tool()
        {
            return new McpToolBeginExBuilder();
        }

        public McpPromptsBeginExBuilder prompts()
        {
            return new McpPromptsBeginExBuilder();
        }

        public McpPromptBeginExBuilder prompt()
        {
            return new McpPromptBeginExBuilder();
        }

        public McpResourcesBeginExBuilder resources()
        {
            return new McpResourcesBeginExBuilder();
        }

        public McpResourceBeginExBuilder resource()
        {
            return new McpResourceBeginExBuilder();
        }

        public McpCompletionBeginExBuilder completion()
        {
            return new McpCompletionBeginExBuilder();
        }

        public McpLoggingBeginExBuilder logging()
        {
            return new McpLoggingBeginExBuilder();
        }

        public McpCancelBeginExBuilder cancel()
        {
            return new McpCancelBeginExBuilder();
        }

        public McpDisconnectBeginExBuilder disconnect()
        {
            return new McpDisconnectBeginExBuilder();
        }

        public byte[] build()
        {
            final byte[] array = new byte[beginExRW.limit()];
            writeBuffer.getBytes(0, array);
            return array;
        }

        public final class McpInitializeBeginExBuilder
        {
            private String sessionId;
            private String version;

            public McpInitializeBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpInitializeBeginExBuilder version(
                String version)
            {
                this.version = version;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.initialize(b -> b.sessionId(sessionId).version(version));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpPingBeginExBuilder
        {
            private String sessionId;

            public McpPingBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.ping(b -> b.sessionId(sessionId));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpToolsBeginExBuilder
        {
            private String sessionId;

            public McpToolsBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.tools(b -> b.sessionId(sessionId));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpToolBeginExBuilder
        {
            private String sessionId;
            private String name;

            public McpToolBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpToolBeginExBuilder name(
                String name)
            {
                this.name = name;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.tool(b -> b.sessionId(sessionId).name(name));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpPromptsBeginExBuilder
        {
            private String sessionId;

            public McpPromptsBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.prompts(b -> b.sessionId(sessionId));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpPromptBeginExBuilder
        {
            private String sessionId;
            private String name;

            public McpPromptBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpPromptBeginExBuilder name(
                String name)
            {
                this.name = name;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.prompt(b -> b.sessionId(sessionId).name(name));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpResourcesBeginExBuilder
        {
            private String sessionId;

            public McpResourcesBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.resources(b -> b.sessionId(sessionId));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpResourceBeginExBuilder
        {
            private String sessionId;
            private String uri;

            public McpResourceBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpResourceBeginExBuilder uri(
                String uri)
            {
                this.uri = uri;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.resource(b -> b.sessionId(sessionId).uri(uri));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpCompletionBeginExBuilder
        {
            private String sessionId;

            public McpCompletionBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.completion(b -> b.sessionId(sessionId));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpLoggingBeginExBuilder
        {
            private String sessionId;
            private String level;

            public McpLoggingBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpLoggingBeginExBuilder level(
                String level)
            {
                this.level = level;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.logging(b -> b.sessionId(sessionId).level(level));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpCancelBeginExBuilder
        {
            private String sessionId;
            private String reason;

            public McpCancelBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpCancelBeginExBuilder reason(
                String reason)
            {
                this.reason = reason;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.cancel(b -> b.sessionId(sessionId).reason(reason));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpDisconnectBeginExBuilder
        {
            private String sessionId;

            public McpDisconnectBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionId = sessionId;
                return this;
            }

            public McpBeginExBuilder build()
            {
                beginExRW.disconnect(b -> b.sessionId(sessionId));
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

        public McpInitializeBeginExMatcherBuilder initialize()
        {
            this.kind = McpBeginExFW.KIND_INITIALIZE;
            final McpInitializeBeginExMatcherBuilder matcher = new McpInitializeBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpPingBeginExMatcherBuilder ping()
        {
            this.kind = McpBeginExFW.KIND_PING;
            final McpPingBeginExMatcherBuilder matcher = new McpPingBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpToolsBeginExMatcherBuilder tools()
        {
            this.kind = McpBeginExFW.KIND_TOOLS;
            final McpToolsBeginExMatcherBuilder matcher = new McpToolsBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpToolBeginExMatcherBuilder tool()
        {
            this.kind = McpBeginExFW.KIND_TOOL;
            final McpToolBeginExMatcherBuilder matcher = new McpToolBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpPromptsBeginExMatcherBuilder prompts()
        {
            this.kind = McpBeginExFW.KIND_PROMPTS;
            final McpPromptsBeginExMatcherBuilder matcher = new McpPromptsBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpPromptBeginExMatcherBuilder prompt()
        {
            this.kind = McpBeginExFW.KIND_PROMPT;
            final McpPromptBeginExMatcherBuilder matcher = new McpPromptBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpResourcesBeginExMatcherBuilder resources()
        {
            this.kind = McpBeginExFW.KIND_RESOURCES;
            final McpResourcesBeginExMatcherBuilder matcher = new McpResourcesBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpResourceBeginExMatcherBuilder resource()
        {
            this.kind = McpBeginExFW.KIND_RESOURCE;
            final McpResourceBeginExMatcherBuilder matcher = new McpResourceBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpCompletionBeginExMatcherBuilder completion()
        {
            this.kind = McpBeginExFW.KIND_COMPLETION;
            final McpCompletionBeginExMatcherBuilder matcher = new McpCompletionBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpLoggingBeginExMatcherBuilder logging()
        {
            this.kind = McpBeginExFW.KIND_LOGGING;
            final McpLoggingBeginExMatcherBuilder matcher = new McpLoggingBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpCancelBeginExMatcherBuilder cancel()
        {
            this.kind = McpBeginExFW.KIND_CANCEL;
            final McpCancelBeginExMatcherBuilder matcher = new McpCancelBeginExMatcherBuilder();
            this.caseMatcher = matcher::match;
            return matcher;
        }

        public McpDisconnectBeginExMatcherBuilder disconnect()
        {
            this.kind = McpBeginExFW.KIND_DISCONNECT;
            final McpDisconnectBeginExMatcherBuilder matcher = new McpDisconnectBeginExMatcherBuilder();
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

        public final class McpInitializeBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW version;

            public McpInitializeBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpInitializeBeginExMatcherBuilder version(
                String version)
            {
                this.version = new String16FW(version);
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpInitializeBeginExFW initialize = beginEx.initialize();
                return matchSessionId(initialize) && matchVersion(initialize);
            }

            private boolean matchSessionId(
                McpInitializeBeginExFW initialize)
            {
                return sessionId == null || sessionId.equals(initialize.sessionId());
            }

            private boolean matchVersion(
                McpInitializeBeginExFW initialize)
            {
                return version == null || version.equals(initialize.version());
            }
        }

        public final class McpPingBeginExMatcherBuilder
        {
            private String16FW sessionId;

            public McpPingBeginExMatcherBuilder sessionId(
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
                return matchSessionId(beginEx.ping());
            }

            private boolean matchSessionId(
                McpPingBeginExFW ping)
            {
                return sessionId == null || sessionId.equals(ping.sessionId());
            }
        }

        public final class McpToolsBeginExMatcherBuilder
        {
            private String16FW sessionId;

            public McpToolsBeginExMatcherBuilder sessionId(
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
                return matchSessionId(beginEx.tools());
            }

            private boolean matchSessionId(
                McpToolsBeginExFW tools)
            {
                return sessionId == null || sessionId.equals(tools.sessionId());
            }
        }

        public final class McpToolBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW name;

            public McpToolBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpToolBeginExMatcherBuilder name(
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
                final McpToolBeginExFW tool = beginEx.tool();
                return matchSessionId(tool) && matchName(tool);
            }

            private boolean matchSessionId(
                McpToolBeginExFW tool)
            {
                return sessionId == null || sessionId.equals(tool.sessionId());
            }

            private boolean matchName(
                McpToolBeginExFW tool)
            {
                return name == null || name.equals(tool.name());
            }
        }

        public final class McpPromptsBeginExMatcherBuilder
        {
            private String16FW sessionId;

            public McpPromptsBeginExMatcherBuilder sessionId(
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
                return matchSessionId(beginEx.prompts());
            }

            private boolean matchSessionId(
                McpPromptsBeginExFW prompts)
            {
                return sessionId == null || sessionId.equals(prompts.sessionId());
            }
        }

        public final class McpPromptBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW name;

            public McpPromptBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpPromptBeginExMatcherBuilder name(
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
                final McpPromptBeginExFW prompt = beginEx.prompt();
                return matchSessionId(prompt) && matchName(prompt);
            }

            private boolean matchSessionId(
                McpPromptBeginExFW prompt)
            {
                return sessionId == null || sessionId.equals(prompt.sessionId());
            }

            private boolean matchName(
                McpPromptBeginExFW prompt)
            {
                return name == null || name.equals(prompt.name());
            }
        }

        public final class McpResourcesBeginExMatcherBuilder
        {
            private String16FW sessionId;

            public McpResourcesBeginExMatcherBuilder sessionId(
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
                return matchSessionId(beginEx.resources());
            }

            private boolean matchSessionId(
                McpResourcesBeginExFW resources)
            {
                return sessionId == null || sessionId.equals(resources.sessionId());
            }
        }

        public final class McpResourceBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW uri;

            public McpResourceBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpResourceBeginExMatcherBuilder uri(
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
                final McpResourceBeginExFW resource = beginEx.resource();
                return matchSessionId(resource) && matchUri(resource);
            }

            private boolean matchSessionId(
                McpResourceBeginExFW resource)
            {
                return sessionId == null || sessionId.equals(resource.sessionId());
            }

            private boolean matchUri(
                McpResourceBeginExFW resource)
            {
                return uri == null || uri.equals(resource.uri());
            }
        }

        public final class McpCompletionBeginExMatcherBuilder
        {
            private String16FW sessionId;

            public McpCompletionBeginExMatcherBuilder sessionId(
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
                return matchSessionId(beginEx.completion());
            }

            private boolean matchSessionId(
                McpCompletionBeginExFW completion)
            {
                return sessionId == null || sessionId.equals(completion.sessionId());
            }
        }

        public final class McpLoggingBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW level;

            public McpLoggingBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpLoggingBeginExMatcherBuilder level(
                String level)
            {
                this.level = new String16FW(level);
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpLoggingBeginExFW logging = beginEx.logging();
                return matchSessionId(logging) && matchLevel(logging);
            }

            private boolean matchSessionId(
                McpLoggingBeginExFW logging)
            {
                return sessionId == null || sessionId.equals(logging.sessionId());
            }

            private boolean matchLevel(
                McpLoggingBeginExFW logging)
            {
                return level == null || level.equals(logging.level());
            }
        }

        public final class McpCancelBeginExMatcherBuilder
        {
            private String16FW sessionId;
            private String16FW reason;

            public McpCancelBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                this.sessionId = new String16FW(sessionId);
                return this;
            }

            public McpCancelBeginExMatcherBuilder reason(
                String reason)
            {
                this.reason = new String16FW(reason);
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpCancelBeginExFW cancel = beginEx.cancel();
                return matchSessionId(cancel) && matchReason(cancel);
            }

            private boolean matchSessionId(
                McpCancelBeginExFW cancel)
            {
                return sessionId == null || sessionId.equals(cancel.sessionId());
            }

            private boolean matchReason(
                McpCancelBeginExFW cancel)
            {
                return reason == null || reason.equals(cancel.reason());
            }
        }

        public final class McpDisconnectBeginExMatcherBuilder
        {
            private String16FW sessionId;

            public McpDisconnectBeginExMatcherBuilder sessionId(
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
                return matchSessionId(beginEx.disconnect());
            }

            private boolean matchSessionId(
                McpDisconnectBeginExFW disconnect)
            {
                return sessionId == null || sessionId.equals(disconnect.sessionId());
            }
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
