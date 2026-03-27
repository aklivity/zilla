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

import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

/**
 * Service provider interface for serializing and deserializing model references within
 * a binding's {@code options} block in {@code zilla.yaml}.
 * <p>
 * Bindings that accept inline model configuration (e.g., an Avro schema reference on a
 * Kafka binding's {@code value} field) use this adapter to parse the model entry. Each
 * model plugin provides an implementation, registered via {@link java.util.ServiceLoader} in
 * {@code META-INF/services/io.aklivity.zilla.runtime.engine.config.ModelConfigAdapterSpi}.
 * The engine selects the correct adapter by matching {@link #type()} against the model
 * entry's {@code model} field value.
 * </p>
 * <p>
 * For example, an Avro model reference in YAML:
 * </p>
 * <pre>{@code
 * options:
 *   value:
 *     model: avro
 *     catalog:
 *       my-catalog:
 *         - subject: orders-value
 *           version: latest
 * }</pre>
 * <p>
 * Unlike the other config adapter SPIs, the JSON representation uses a
 * {@link jakarta.json.JsonValue} rather than a {@link jakarta.json.JsonObject}, allowing
 * model configurations that may be expressed as either a string shorthand or a full object.
 * </p>
 *
 * @see OptionsConfigAdapterSpi
 */
public interface ModelConfigAdapterSpi extends JsonbAdapter<ModelConfig, JsonValue>
{
    /**
     * Returns the model type name this adapter handles, e.g. {@code "avro"},
     * {@code "protobuf"}, {@code "json"}.
     * <p>
     * Must match the {@code model} field value in the inline model configuration entry.
     * </p>
     *
     * @return the model type name
     */
    String type();

    /**
     * Serializes a model-specific {@link ModelConfig} instance to a
     * {@link jakarta.json.JsonValue} for writing back to YAML/JSON.
     *
     * @param options  the model configuration to serialize
     * @return a {@link jakarta.json.JsonValue} representation of the model configuration
     */
    @Override
    JsonValue adaptToJson(
        ModelConfig options);

    /**
     * Deserializes a {@link jakarta.json.JsonValue} from a model reference in
     * {@code zilla.yaml} into a model-specific {@link ModelConfig} instance.
     *
     * @param object  the raw JSON value from the model entry (may be a string or object)
     * @return the deserialized {@link ModelConfig}
     */
    @Override
    ModelConfig adaptFromJson(
        JsonValue object);
}
