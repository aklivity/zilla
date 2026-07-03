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
package io.aklivity.zilla.runtime.common.openapi.model;

import static java.util.Collections.emptyMap;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.openapi.config.OpenapiExtension;

public final class OpenapiOAuthFlowsDeserializer implements JsonbDeserializer<OpenapiOAuthFlows>
{
    private final Map<String, Class<?>> extensionTypes;
    private final Map<String, Class<?>> prefixExtensionTypes;
    private final Supplier<Jsonb> plain;

    public OpenapiOAuthFlowsDeserializer(
        Map<OpenapiExtension.Scope, Map<String, Class<?>>> extensionTypes,
        Map<OpenapiExtension.Scope, Map<String, Class<?>>> prefixExtensionTypes)
    {
        this.extensionTypes = extensionTypes.getOrDefault(OpenapiExtension.Scope.OAUTH_FLOWS, emptyMap());
        this.prefixExtensionTypes = prefixExtensionTypes.getOrDefault(OpenapiExtension.Scope.OAUTH_FLOWS, emptyMap());
        this.plain = OpenapiDeserializers.plain(extensionTypes, prefixExtensionTypes, OpenapiOAuthFlowsDeserializer.class);
    }

    @Override
    public OpenapiOAuthFlows deserialize(
        JsonParser parser,
        DeserializationContext ctx,
        Type type)
    {
        JsonObject object = parser.getObject();
        OpenapiOAuthFlows model = plain.get().fromJson(object.toString(), OpenapiOAuthFlows.class);
        model.extensions = OpenapiDeserializers.extensions(object, extensionTypes, prefixExtensionTypes, plain);

        return model;
    }
}
