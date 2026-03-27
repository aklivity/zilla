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
package io.aklivity.zilla.runtime.engine.config;

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

/**
 * Service provider interface for serializing and deserializing the {@code when} condition
 * block of a route entry in {@code zilla.yaml}.
 * <p>
 * Each binding that supports conditional routing provides an implementation of this interface,
 * registered via {@link java.util.ServiceLoader} in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.config.ConditionConfigAdapterSpi}.
 * The engine selects the correct adapter by matching {@link #type()} against the enclosing
 * binding's {@code type} field.
 * </p>
 * <p>
 * For example, the HTTP binding's condition adapter handles entries such as:
 * </p>
 * <pre>{@code
 * routes:
 *   - when:
 *       - method: GET
 *         path: /api/items
 * }</pre>
 * <p>
 * Implementations extend {@link jakarta.json.bind.adapter.JsonbAdapter} to convert between
 * the binding's concrete {@link ConditionConfig} subclass and a
 * {@link jakarta.json.JsonObject} representing the raw YAML/JSON condition object.
 * </p>
 *
 * @see OptionsConfigAdapterSpi
 * @see WithConfigAdapterSpi
 */
public interface ConditionConfigAdapterSpi extends JsonbAdapter<ConditionConfig, JsonObject>
{
    /**
     * Returns the binding type name this adapter handles, e.g. {@code "http"}, {@code "kafka"}.
     * <p>
     * Must match the {@code type} field of the binding whose route {@code when} conditions
     * this adapter serializes and deserializes.
     * </p>
     *
     * @return the binding type name
     */
    String type();

    /**
     * Serializes a binding-specific {@link ConditionConfig} to a
     * {@link jakarta.json.JsonObject} for writing back to YAML/JSON.
     *
     * @param condition  the condition configuration to serialize
     * @return a {@link jakarta.json.JsonObject} representation of the condition
     */
    @Override
    JsonObject adaptToJson(
        ConditionConfig condition);

    /**
     * Deserializes a {@link jakarta.json.JsonObject} from a {@code when} entry in
     * {@code zilla.yaml} into a binding-specific {@link ConditionConfig} instance.
     *
     * @param object  the raw JSON object from the {@code when} block
     * @return the deserialized {@link ConditionConfig}
     */
    @Override
    ConditionConfig adaptFromJson(
        JsonObject object);
}
