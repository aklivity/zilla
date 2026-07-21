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
 * Starts with {@link #message()}; structured accessors (byte offset, field number, category) may be added
 * without changing the pipeline's {@code Status} contract.
 */
public interface ProtobufDiagnostic
{
    /**
     * A short, human-readable reason for the rejection — e.g. an unknown field, an unknown enum value, a
     * missing required field, or truncated input — or {@code null} when the rejecting component supplied
     * no message.
     */
    String message();
}
