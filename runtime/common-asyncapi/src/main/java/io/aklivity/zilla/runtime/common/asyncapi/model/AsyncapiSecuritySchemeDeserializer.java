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
package io.aklivity.zilla.runtime.common.asyncapi.model;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.asyncapi.config.AsyncapiExtension;

public final class AsyncapiSecuritySchemeDeserializer implements JsonbDeserializer<AsyncapiSecurityScheme>
{
    private final Map<AsyncapiExtension.Scope, Map<String, Class<?>>> extensionTypes;
    private final Map<AsyncapiExtension.Scope, Map<String, Class<?>>> prefixExtensionTypes;
    private final Supplier<Jsonb> plain;

    public AsyncapiSecuritySchemeDeserializer(
        Map<String, Class<?>> operationBindingTypes,
        Map<String, Class<?>> messageBindingTypes,
        Map<String, Class<?>> serverBindingTypes,
        Map<AsyncapiExtension.Scope, Map<String, Class<?>>> extensionTypes,
        Map<AsyncapiExtension.Scope, Map<String, Class<?>>> prefixExtensionTypes)
    {
        this.extensionTypes = extensionTypes;
        this.prefixExtensionTypes = prefixExtensionTypes;
        this.plain = AsyncapiDeserializers.plain(
            operationBindingTypes, messageBindingTypes, serverBindingTypes, extensionTypes, prefixExtensionTypes,
            AsyncapiSecuritySchemeDeserializer.class);
    }

    @Override
    public AsyncapiSecurityScheme deserialize(
        JsonParser parser,
        DeserializationContext ctx,
        Type type)
    {
        JsonObject object = parser.getObject();
        AsyncapiSecurityScheme model = plain.get().fromJson(object.toString(), AsyncapiSecurityScheme.class);
        model.extensions = AsyncapiDeserializers.extensions(
            object, AsyncapiExtension.Scope.SECURITY_SCHEME, extensionTypes, prefixExtensionTypes, plain);

        return model;
    }
}
