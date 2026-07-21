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
package io.aklivity.zilla.runtime.common.asyncapi.config;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AsyncapiParserFactory
{
    private final Map<String, Class<?>> operationBindingTypes;
    private final Map<String, Class<?>> messageBindingTypes;
    private final Map<String, Class<?>> serverBindingTypes;
    private final Map<AsyncapiExtension.Scope, Map<String, Class<?>>> extensionTypes;
    private final Map<AsyncapiExtension.Scope, Map<String, Class<?>>> prefixExtensionTypes;

    public AsyncapiParserFactory()
    {
        this.operationBindingTypes = new LinkedHashMap<>();
        this.messageBindingTypes = new LinkedHashMap<>();
        this.serverBindingTypes = new LinkedHashMap<>();
        this.extensionTypes = new LinkedHashMap<>();
        this.prefixExtensionTypes = new LinkedHashMap<>();
    }

    public <T> AsyncapiParserFactory withOperationBinding(
        String name,
        Class<T> type)
    {
        operationBindingTypes.put(name, type);
        return this;
    }

    public <T> AsyncapiParserFactory withMessageBinding(
        String name,
        Class<T> type)
    {
        messageBindingTypes.put(name, type);
        return this;
    }

    public <T> AsyncapiParserFactory withServerBinding(
        String name,
        Class<T> type)
    {
        serverBindingTypes.put(name, type);
        return this;
    }

    public AsyncapiParserFactory withExtension(
        AsyncapiExtension extension)
    {
        Map<AsyncapiExtension.Scope, Map<String, Class<?>>> target = extension.name().endsWith("*")
            ? prefixExtensionTypes
            : extensionTypes;
        target.computeIfAbsent(extension.scope(), scope -> new LinkedHashMap<>())
            .put(extension.name(), extension.type());
        return this;
    }

    public AsyncapiParser createParser()
    {
        return new AsyncapiParser(
            operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes);
    }
}
