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

/**
 * A description of a terminal {@link ProtobufPipeline.Status#REJECTED} failure, populated at the point
 * of detection — by the parser for a malformed-wire failure, or by the validator for a structural one
 * (e.g. a missing proto2 {@code required} field) — and handed to a {@link ProtobufReporter}.
 * <p>
 * The instance is a reused, call-scoped view: it is valid only for the duration of the
 * {@link ProtobufReporter#rejected(ProtobufDiagnostic)} callback. A reporter that needs to retain any of
 * it must copy the value out immediately, before returning.
 * <p>
 * Starts with {@link #message()}; structured accessors (byte offset, field number) may be added without
 * changing the pipeline's {@code Status} contract.
 */
public interface ProtobufDiagnostic
{
    /**
     * A short, human-readable reason for the rejection — e.g. an unknown field, an unknown enum value, a
     * missing required field, or truncated input — or {@code null} when the rejecting component supplied
     * no message.
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
         * No valid value could be produced at all — whether because the bytes are malformed on the wire,
         * or because they are structurally non-conformant to the descriptor (e.g. an unknown message, an
         * unknown field, an unknown enum value, or an unsupported scalar type). See
         * {@link ProtobufParsingException}.
         */
        PARSING,

        /**
         * A structurally-valid value that violates a semantic rule beyond the descriptor's structure — e.g.
         * a data contract or a constraint not expressible in the descriptor itself. See
         * {@link ProtobufValidationException}.
         */
        VALIDATION,

        /**
         * The rejection stems from some other cause — e.g. an exception thrown by an extension's own
         * transform logic — rather than the value itself being invalid.
         */
        TRANSFORM
    }
}
