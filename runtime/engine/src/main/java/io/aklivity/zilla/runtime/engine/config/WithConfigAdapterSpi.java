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
 * Service provider interface for serializing and deserializing the {@code with} block
 * of a route entry in {@code zilla.yaml}.
 * <p>
 * The {@code with} block on a route supplies binding-specific parameters that govern how
 * a matched request is forwarded — for example, topic name and partition key overrides on
 * a Kafka route, or method and path rewriting on an HTTP route. Each binding that supports
 * a {@code with} block provides an implementation, registered via
 * {@link java.util.ServiceLoader} in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi}.
 * The engine selects the correct adapter by matching {@link #type()} against the enclosing
 * binding's {@code type} field.
 * </p>
 * <p>
 * For example, a Kafka route's {@code with} block:
 * </p>
 * <pre>{@code
 * routes:
 *   - when:
 *       - method: POST
 *         path: /orders
 *     with:
 *       topic: orders
 *       key: ${params.orderId}
 * }</pre>
 * <p>
 * Implementations extend {@link jakarta.json.bind.adapter.JsonbAdapter} to convert between
 * the binding's concrete {@link WithConfig} subclass and a {@link jakarta.json.JsonObject}
 * representing the raw YAML/JSON {@code with} object.
 * </p>
 *
 * @see ConditionConfigAdapterSpi
 * @see OptionsConfigAdapterSpi
 */
public interface WithConfigAdapterSpi extends JsonbAdapter<WithConfig, JsonObject>
{
    /**
     * Returns the binding type name this adapter handles, e.g. {@code "http"}, {@code "kafka"}.
     * <p>
     * Must match the {@code type} field of the binding whose route {@code with} blocks
     * this adapter serializes and deserializes.
     * </p>
     *
     * @return the binding type name
     */
    String type();

    /**
     * Serializes a binding-specific {@link WithConfig} instance to a
     * {@link jakarta.json.JsonObject} for writing back to YAML/JSON.
     *
     * @param with  the {@code with} configuration to serialize
     * @return a {@link jakarta.json.JsonObject} representation of the {@code with} block
     */
    @Override
    JsonObject adaptToJson(
        WithConfig with);

    /**
     * Deserializes a {@link jakarta.json.JsonObject} from the {@code with} block in
     * {@code zilla.yaml} into a binding-specific {@link WithConfig} instance.
     *
     * @param object  the raw JSON object from the {@code with} block
     * @return the deserialized {@link WithConfig}
     */
    @Override
    WithConfig adaptFromJson(
        JsonObject object);
}
