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
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.k3po.runtime.lang.el.BytesMatcher;
import io.aklivity.k3po.runtime.lang.el.Function;
import io.aklivity.k3po.runtime.lang.el.spi.FunctionMapperSpi;
import io.aklivity.zilla.specs.binding.mcp.internal.types.McpSessionIdFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.String16FW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpAbortExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpCanceledBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpCompletionBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpDisconnectBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpInitializeBeginExFW;
import io.aklivity.zilla.specs.binding.mcp.internal.types.stream.McpInitializedBeginExFW;
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

        public McpInitializedBeginExBuilder initialized()
        {
            return new McpInitializedBeginExBuilder();
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

        public McpCanceledBeginExBuilder canceled()
        {
            return new McpCanceledBeginExBuilder();
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
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);

            public McpInitializeBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpInitializeBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
                return this;
            }

            public McpBeginExBuilder build()
            {
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.initialize(b -> b.sessionId(setter));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpInitializedBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);

            public McpInitializedBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpInitializedBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
                return this;
            }

            public McpBeginExBuilder build()
            {
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.initialized(b -> b.sessionId(setter));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpPingBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);

            public McpPingBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpPingBeginExBuilder sessionIdLong(long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
                return this;
            }

            public McpBeginExBuilder build()
            {
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.ping(b -> b.sessionId(setter));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpToolsBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);

            public McpToolsBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpToolsBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
                return this;
            }

            public McpBeginExBuilder build()
            {
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.tools(b -> b.sessionId(setter));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpToolBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);
            private String name;

            public McpToolBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpToolBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
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
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.tool(b -> b.sessionId(setter).name(name));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpPromptsBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);

            public McpPromptsBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpPromptsBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
                return this;
            }

            public McpBeginExBuilder build()
            {
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.prompts(b -> b.sessionId(setter));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpPromptBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);
            private String name;

            public McpPromptBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpPromptBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
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
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.prompt(b -> b.sessionId(setter).name(name));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpResourcesBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);

            public McpResourcesBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpResourcesBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
                return this;
            }

            public McpBeginExBuilder build()
            {
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.resources(b -> b.sessionId(setter));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpResourceBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);
            private String uri;

            public McpResourceBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpResourceBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
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
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.resource(b -> b.sessionId(setter).uri(uri));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpCompletionBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);

            public McpCompletionBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpCompletionBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
                return this;
            }

            public McpBeginExBuilder build()
            {
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.completion(b -> b.sessionId(setter));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpLoggingBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);

            public McpLoggingBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpLoggingBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
                return this;
            }

            public McpBeginExBuilder build()
            {
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.logging(b -> b.sessionId(setter));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpCanceledBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);

            public McpCanceledBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpCanceledBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
                return this;
            }

            public McpBeginExBuilder build()
            {
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.canceled(b -> b.sessionId(setter));
                return McpBeginExBuilder.this;
            }
        }

        public final class McpDisconnectBeginExBuilder
        {
            private Consumer<McpSessionIdFW.Builder> sessionIdSetter = sid -> sid.text((String) null);

            public McpDisconnectBeginExBuilder sessionId(
                String sessionId)
            {
                this.sessionIdSetter = sid -> sid.text(sessionId);
                return this;
            }

            public McpDisconnectBeginExBuilder sessionIdLong(
                long sessionId)
            {
                this.sessionIdSetter = sid -> sid.id(sessionId);
                return this;
            }

            public McpBeginExBuilder build()
            {
                final Consumer<McpSessionIdFW.Builder> setter = sessionIdSetter;
                beginExRW.disconnect(b -> b.sessionId(setter));
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

        public McpInitializedBeginExMatcherBuilder initialized()
        {
            this.kind = McpBeginExFW.KIND_INITIALIZED;
            final McpInitializedBeginExMatcherBuilder matcher = new McpInitializedBeginExMatcherBuilder();
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

        public McpCanceledBeginExMatcherBuilder canceled()
        {
            this.kind = McpBeginExFW.KIND_CANCELED;
            final McpCanceledBeginExMatcherBuilder matcher = new McpCanceledBeginExMatcherBuilder();
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
            private Predicate<McpSessionIdFW> sessionIdMatcher;

            public McpInitializeBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpInitializeBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
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
                return matchSessionId(initialize);
            }

            private boolean matchSessionId(
                McpInitializeBeginExFW initialize)
            {
                return sessionIdMatcher == null || sessionIdMatcher.test(initialize.sessionId());
            }
        }

        public final class McpInitializedBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;

            public McpInitializedBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpInitializedBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpInitializedBeginExFW initialized = beginEx.initialized();
                return matchSessionId(initialized);
            }

            private boolean matchSessionId(
                McpInitializedBeginExFW initialize)
            {
                return sessionIdMatcher == null || sessionIdMatcher.test(initialize.sessionId());
            }
        }

        public final class McpPingBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;

            public McpPingBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpPingBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
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
                return sessionIdMatcher == null || sessionIdMatcher.test(ping.sessionId());
            }
        }

        public final class McpToolsBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;

            public McpToolsBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpToolsBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
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
                return sessionIdMatcher == null || sessionIdMatcher.test(tools.sessionId());
            }
        }

        public final class McpToolBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;
            private String16FW name;

            public McpToolBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpToolBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
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
                return sessionIdMatcher == null || sessionIdMatcher.test(tool.sessionId());
            }

            private boolean matchName(
                McpToolBeginExFW tool)
            {
                return name == null || name.equals(tool.name());
            }
        }

        public final class McpPromptsBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;

            public McpPromptsBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpPromptsBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
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
                return sessionIdMatcher == null || sessionIdMatcher.test(prompts.sessionId());
            }
        }

        public final class McpPromptBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;
            private String16FW name;

            public McpPromptBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpPromptBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
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
                return sessionIdMatcher == null || sessionIdMatcher.test(prompt.sessionId());
            }

            private boolean matchName(
                McpPromptBeginExFW prompt)
            {
                return name == null || name.equals(prompt.name());
            }
        }

        public final class McpResourcesBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;

            public McpResourcesBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpResourcesBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
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
                return sessionIdMatcher == null || sessionIdMatcher.test(resources.sessionId());
            }
        }

        public final class McpResourceBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;
            private String16FW uri;

            public McpResourceBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpResourceBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
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
                return sessionIdMatcher == null || sessionIdMatcher.test(resource.sessionId());
            }

            private boolean matchUri(
                McpResourceBeginExFW resource)
            {
                return uri == null || uri.equals(resource.uri());
            }
        }

        public final class McpCompletionBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;

            public McpCompletionBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpCompletionBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
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
                return sessionIdMatcher == null || sessionIdMatcher.test(completion.sessionId());
            }
        }

        public final class McpLoggingBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;

            public McpLoggingBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpLoggingBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
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
                return matchSessionId(logging);
            }

            private boolean matchSessionId(
                McpLoggingBeginExFW logging)
            {
                return sessionIdMatcher == null || sessionIdMatcher.test(logging.sessionId());
            }
        }

        public final class McpCanceledBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;

            public McpCanceledBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpCanceledBeginExMatcherBuilder sessionIdLong(
                long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
                return this;
            }

            public McpBeginExMatcherBuilder build()
            {
                return McpBeginExMatcherBuilder.this;
            }

            private boolean match(
                McpBeginExFW beginEx)
            {
                final McpCanceledBeginExFW canceled = beginEx.canceled();
                return matchSessionId(canceled);
            }

            private boolean matchSessionId(
                McpCanceledBeginExFW canceled)
            {
                return sessionIdMatcher == null || sessionIdMatcher.test(canceled.sessionId());
            }
        }

        public final class McpDisconnectBeginExMatcherBuilder
        {
            private Predicate<McpSessionIdFW> sessionIdMatcher;

            public McpDisconnectBeginExMatcherBuilder sessionId(
                String sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_TEXT &&
                    Objects.equals(sessionId, sid.text().asString());
                return this;
            }

            public McpDisconnectBeginExMatcherBuilder sessionIdLong(long sessionId)
            {
                sessionIdMatcher = sid -> sid.kind() == McpSessionIdFW.KIND_ID && sessionId == sid.id();
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
                return sessionIdMatcher == null || sessionIdMatcher.test(disconnect.sessionId());
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
