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
package io.aklivity.zilla.runtime.common.protobuf;

import java.util.Map;
import java.util.Objects;

import jakarta.json.JsonObject;

/**
 * An immutable Protobuf {@code rpc} descriptor: its bare name, the full names of its input and output
 * message types, its streaming cardinality, and any {@code MethodOptions} declared in its {@code .proto}
 * source's {@code option ...;} statements.
 */
public final class ProtobufMethod
{
    private final String name;
    private final String inputType;
    private final String outputType;
    private final boolean clientStreaming;
    private final boolean serverStreaming;
    private final Map<String, ProtobufConstant> options;
    private final JsonObject overlay;

    private ProtobufMethod(
        String name,
        String inputType,
        String outputType,
        boolean clientStreaming,
        boolean serverStreaming,
        Map<String, ProtobufConstant> options,
        JsonObject overlay)
    {
        this.name = name;
        this.inputType = inputType;
        this.outputType = outputType;
        this.clientStreaming = clientStreaming;
        this.serverStreaming = serverStreaming;
        this.options = options;
        this.overlay = overlay;
    }

    public String name()
    {
        return name;
    }

    /**
     * The full name of this method's request message type, resolved by the proto scoping rules.
     */
    public String inputType()
    {
        return inputType;
    }

    /**
     * The full name of this method's response message type, resolved by the proto scoping rules.
     */
    public String outputType()
    {
        return outputType;
    }

    public boolean clientStreaming()
    {
        return clientStreaming;
    }

    public boolean serverStreaming()
    {
        return serverStreaming;
    }

    /**
     * The value of method option {@code name}, parsed from the {@code .proto} source's {@code rpc { ... }}
     * body — read from this method's own options only, with no descriptor resolution, so an option need
     * not be declared by any imported {@code .proto} to be retained. {@code null} when this method
     * declares no such option.
     */
    public ProtobufConstant option(
        String name)
    {
        return options != null ? options.get(name) : null;
    }

    /**
     * The effective options for this method as a {@link JsonObject}: the overlay-merged view once a
     * {@link ProtobufOverlay} has been applied, or the inline {@code .proto} source options converted
     * to JSON otherwise. Unlike {@link #option(String)}, which always surfaces only the raw inline
     * value, a consumer that must not care whether an option came from the {@code .proto} source or an
     * overlay reads this instead.
     */
    public JsonObject options()
    {
        return overlay != null ? overlay : ProtobufConstant.toJsonObject(options);
    }

    Map<String, ProtobufConstant> rawOptions()
    {
        return options;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private String name;
        private String inputType;
        private String outputType;
        private boolean clientStreaming;
        private boolean serverStreaming;
        private Map<String, ProtobufConstant> options;
        private JsonObject overlay;

        public Builder name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Builder inputType(
            String inputType)
        {
            this.inputType = inputType;
            return this;
        }

        public Builder outputType(
            String outputType)
        {
            this.outputType = outputType;
            return this;
        }

        public Builder clientStreaming(
            boolean clientStreaming)
        {
            this.clientStreaming = clientStreaming;
            return this;
        }

        public Builder serverStreaming(
            boolean serverStreaming)
        {
            this.serverStreaming = serverStreaming;
            return this;
        }

        public Builder options(
            Map<String, ProtobufConstant> options)
        {
            this.options = options;
            return this;
        }

        public Builder overlay(
            JsonObject overlay)
        {
            this.overlay = overlay;
            return this;
        }

        public ProtobufMethod build()
        {
            Objects.requireNonNull(name, "method name");
            Objects.requireNonNull(inputType, "method input type");
            Objects.requireNonNull(outputType, "method output type");
            return new ProtobufMethod(name, inputType, outputType, clientStreaming, serverStreaming, options, overlay);
        }
    }
}
