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
package io.aklivity.zilla.runtime.engine.catalog;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

/**
 * Resolves schemas and transforms data payloads for a specific catalog configuration.
 * <p>
 * A {@code CatalogHandler} is obtained from {@link CatalogContext#attach(CatalogConfig)} and is
 * confined to a single I/O thread. It exposes three pipeline operations — decode, validate, and
 * encode — each of which accepts a {@link DirectBuffer} slice and a downstream
 * {@link ValueConsumer}, allowing zero-copy transformation without intermediate object allocation.
 * </p>
 * <p>
 * Schemas are identified by integer schema ids, and subjects are identified by a name and version
 * string. The sentinel {@link #NO_SCHEMA_ID} ({@code 0}) indicates that no schema has been
 * resolved.
 * </p>
 *
 * @see CatalogContext
 */
public interface CatalogHandler
{
    /** Sentinel schema id indicating that no schema is associated with a payload. */
    int NO_SCHEMA_ID = 0;

    /** Sentinel version id indicating that no specific schema version has been resolved. */
    int NO_VERSION_ID = 0;

    /**
     * Functional interface for decoding a framed payload into its raw content.
     * <p>
     * Implementations strip any schema framing (e.g., a Confluent magic byte + schema id prefix)
     * and forward the raw payload bytes to the {@code next} consumer.
     * </p>
     */
    @FunctionalInterface
    interface Decoder
    {
        /**
         * Identity decoder that forwards the payload unchanged with no schema id.
         */
        Decoder IDENTITY = (traceId, bindingId, schemaId, data, index, length, next) ->
        {
            next.accept(data, index, length);
            return length;
        };

        /**
         * Decodes {@code data[index..index+length)} using the given schema id and forwards
         * the decoded bytes to {@code next}.
         *
         * @param traceId    the trace identifier for diagnostics
         * @param bindingId  the binding identifier
         * @param schemaId   the schema id to use for decoding
         * @param data       the source buffer
         * @param index      the offset of the payload in the buffer
         * @param length     the length of the payload
         * @param next       the consumer to receive decoded bytes
         * @return the number of bytes consumed, or a negative value on failure
         */
        int accept(
            long traceId,
            long bindingId,
            int schemaId,
            DirectBuffer data,
            int index,
            int length,
            ValueConsumer next);
    }

    /**
     * Functional interface for encoding a raw payload with schema framing.
     * <p>
     * Implementations prepend any required schema framing (e.g., a Confluent magic byte
     * + schema id) and forward the framed bytes to the {@code next} consumer.
     * </p>
     */
    @FunctionalInterface
    interface Encoder
    {
        /**
         * Identity encoder that forwards the payload unchanged.
         */
        Encoder IDENTITY = (traceId, bindingId, schemaId, data, index, length, next) ->
        {
            next.accept(data, index, length);
            return length;
        };

        /**
         * Encodes {@code data[index..index+length)} using the given schema id and forwards
         * the framed bytes to {@code next}.
         *
         * @param traceId    the trace identifier for diagnostics
         * @param bindingId  the binding identifier
         * @param schemaId   the schema id to embed in the framing
         * @param data       the source buffer containing raw payload bytes
         * @param index      the offset of the payload in the buffer
         * @param length     the length of the payload
         * @param next       the consumer to receive framed bytes
         * @return the number of bytes produced, or a negative value on failure
         */
        int accept(
            long traceId,
            long bindingId,
            int schemaId,
            DirectBuffer data,
            int index,
            int length,
            ValueConsumer next);
    }

    /**
     * Functional interface for validating a payload against a schema.
     */
    @FunctionalInterface
    interface Validator
    {
        /**
         * Identity validator that accepts all payloads without inspection.
         */
        Validator IDENTITY = (traceId, bindingId, schemaId, data, index, length, next) -> true;

        /**
         * Validates {@code data[index..index+length)} against the given schema id.
         * If valid, the payload is forwarded to {@code next}.
         *
         * @param traceId    the trace identifier for diagnostics
         * @param bindingId  the binding identifier
         * @param schemaId   the schema id to validate against
         * @param data       the source buffer
         * @param index      the offset of the payload
         * @param length     the length of the payload
         * @param next       the consumer to receive the payload if validation passes
         * @return {@code true} if the payload is valid, {@code false} otherwise
         */
        boolean accept(
            long traceId,
            long bindingId,
            int schemaId,
            DirectBuffer data,
            int index,
            int length,
            ValueConsumer next);
    }

    /**
     * Registers a schema under the given subject in the catalog and returns its version id.
     * <p>
     * For read-only catalogs (e.g., {@code catalog-filesystem}) the default implementation
     * returns {@link #NO_VERSION_ID} without side effects.
     * </p>
     *
     * @param subject  the subject name under which to register the schema
     * @param schema   the schema definition string
     * @return the assigned version id, or {@link #NO_VERSION_ID} if registration is not supported
     */
    default int register(
        String subject,
        String schema)
    {
        return NO_VERSION_ID;
    }

    /**
     * Removes all versions of a subject from the catalog.
     * <p>
     * Returns an empty array by default for read-only catalogs.
     * </p>
     *
     * @param subject  the subject name to remove
     * @return an array of the version ids that were removed
     */
    default int[] unregister(
        String subject)
    {
        return new int[0];
    }

    /**
     * Resolves the schema definition string for a given schema id.
     *
     * @param schemaId  the schema id to resolve
     * @return the schema definition string, or {@code null} if not found
     */
    String resolve(
        int schemaId);

    /**
     * Resolves the schema id for a given subject name and version string.
     *
     * @param subject  the subject name
     * @param version  the version string (e.g., {@code "latest"} or a numeric version)
     * @return the resolved schema id, or {@link #NO_SCHEMA_ID} if not found
     */
    int resolve(
        String subject,
        String version);

    /**
     * Attempts to extract a schema id embedded in the payload bytes themselves
     * (e.g., a Confluent-framed message with a magic byte + 4-byte schema id prefix).
     * <p>
     * Returns {@link #NO_SCHEMA_ID} by default for catalogs that do not embed schema ids
     * in the payload.
     * </p>
     *
     * @param data   the buffer containing the payload
     * @param index  the offset of the payload
     * @param length the length of the payload
     * @return the embedded schema id, or {@link #NO_SCHEMA_ID} if not present
     */
    default int resolve(
        DirectBuffer data,
        int index,
        int length)
    {
        return NO_SCHEMA_ID;
    }

    /**
     * Decodes a payload using the given {@link Decoder}, resolving the schema id from the
     * payload framing if necessary.
     *
     * @param traceId    the trace identifier
     * @param bindingId  the binding identifier
     * @param data       the buffer containing the framed payload
     * @param index      the offset of the payload
     * @param length     the length of the payload
     * @param next       the consumer to receive decoded bytes
     * @param decoder    the decoder implementation to apply
     * @return the number of bytes consumed
     */
    default int decode(
        long traceId,
        long bindingId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        Decoder decoder)
    {
        return decoder.accept(traceId, bindingId, NO_SCHEMA_ID, data, index, length, next);
    }

    /**
     * Validates a payload using the given {@link Validator}, resolving the schema id from
     * the payload framing if necessary.
     *
     * @param traceId    the trace identifier
     * @param bindingId  the binding identifier
     * @param data       the buffer containing the payload
     * @param index      the offset of the payload
     * @param length     the length of the payload
     * @param next       the consumer to receive the payload if valid
     * @param validator  the validator implementation to apply
     * @return {@code true} if the payload passed validation
     */
    default boolean validate(
        long traceId,
        long bindingId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        Validator validator)
    {
        return validator.accept(traceId, bindingId, NO_SCHEMA_ID, data, index, length, next);
    }

    /**
     * Encodes a payload using the given {@link Encoder}, embedding the given schema id.
     *
     * @param traceId    the trace identifier
     * @param bindingId  the binding identifier
     * @param schemaId   the schema id to embed in the encoded output
     * @param data       the buffer containing the raw payload
     * @param index      the offset of the payload
     * @param length     the length of the payload
     * @param next       the consumer to receive encoded bytes
     * @param encoder    the encoder implementation to apply
     * @return the number of bytes produced
     */
    default int encode(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        Encoder encoder)
    {
        return encoder.accept(traceId, bindingId, schemaId, data, index, length, next);
    }

    /**
     * Returns the number of additional bytes required in a write buffer to accommodate
     * schema framing that the encoder will prepend or append.
     *
     * @param length  the raw payload length
     * @return the padding byte count (0 for catalogs that do not add framing)
     */
    default int encodePadding(
        int length)
    {
        return 0;
    }

    /**
     * Returns the number of schema framing bytes at the start of the given payload that
     * should be skipped when computing the effective payload offset after decoding.
     *
     * @param data   the buffer containing the framed payload
     * @param index  the offset of the framed payload
     * @param length the length of the framed payload
     * @return the framing byte count to skip (0 for catalogs without embedded framing)
     */
    default int decodePadding(
        DirectBuffer data,
        int index,
        int length)
    {
        return 0;
    }

    /**
     * Returns the base URL or connection string used to reach the remote schema registry,
     * if applicable.
     *
     * @return the catalog location string
     * @throws UnsupportedOperationException if this catalog type has no remote location
     */
    default String location()
    {
        throw new UnsupportedOperationException("location");
    }
}
