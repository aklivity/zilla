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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable Protobuf {@code service} descriptor: its full name and the {@link ProtobufMethod}s it
 * declares, keyed by their bare {@code rpc} name.
 */
public final class ProtobufService
{
    private final String name;
    private final Map<String, ProtobufMethod> methods;

    private ProtobufService(
        String name,
        Map<String, ProtobufMethod> methods)
    {
        this.name = name;
        this.methods = methods;
    }

    public String name()
    {
        return name;
    }

    public Collection<ProtobufMethod> methods()
    {
        return methods.values();
    }

    public ProtobufMethod method(
        String name)
    {
        return methods.get(name);
    }

    public static Builder builder(
        String name)
    {
        return new Builder(name);
    }

    public static final class Builder
    {
        private final String name;
        private final Map<String, ProtobufMethod> methods;

        private Builder(
            String name)
        {
            this.name = name;
            this.methods = new LinkedHashMap<>();
        }

        public Builder method(
            ProtobufMethod method)
        {
            methods.put(method.name(), method);
            return this;
        }

        public ProtobufService build()
        {
            return new ProtobufService(name, methods);
        }
    }
}
