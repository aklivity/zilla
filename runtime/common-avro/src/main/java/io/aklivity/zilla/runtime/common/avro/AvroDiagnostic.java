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
package io.aklivity.zilla.runtime.common.avro;

/**
 * A description of a terminal {@link AvroPipeline.Status#REJECTED} failure, populated at the point of
 * detection — by the parser for binary that cannot be decoded against its schema — and handed to an
 * {@link AvroReporter}.
 * <p>
 * The instance is a reused, call-scoped view: it is valid only for the duration of the
 * {@link AvroReporter#rejected(AvroDiagnostic)} callback. A reporter that needs to retain any of it must
 * copy the value out immediately, before returning.
 * <p>
 * Starts with {@link #message()}; structured accessors (byte offset, field) may be added without changing
 * the pipeline's {@code Status} contract.
 */
public interface AvroDiagnostic
{
    /**
     * A short, human-readable reason for the rejection — e.g. an out-of-range union branch or enum ordinal,
     * a negative block count, or a truncated variable-length integer — or {@code null} when the rejecting
     * component supplied no message.
     */
    String message();

    /**
     * The category of failure this rejection stems from.
     */
    Category category();

    /**
     * Distinguishes a genuinely invalid or malformed value from any other rejection reason, so a reporter
     * need not assume every rejection is a validation failure.
     */
    enum Category
    {
        /**
         * No valid value could be produced at all — whether because the bytes are malformed (e.g. a
         * truncated variable-length integer or negative block count) or because they are structurally
         * non-conformant to the schema (e.g. a wrong scalar type, an unexpected field key, an out-of-range
         * union branch or enum ordinal, or a {@code fixed} of the wrong size). See {@link AvroParsingException}.
         */
        PARSING,

        /**
         * A structurally-valid value that violates a semantic rule beyond the schema's structure — e.g. a
         * logical-type constraint or a data contract. See {@link AvroValidationException}.
         */
        VALIDATION,

        /**
         * The rejection stems from some other cause — e.g. an exception thrown by an extension's own
         * transform logic — rather than the value itself being invalid.
         */
        TRANSFORM
    }
}
