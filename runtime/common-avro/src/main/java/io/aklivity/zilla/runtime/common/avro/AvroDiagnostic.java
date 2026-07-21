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
 * Starts with {@link #message()}; structured accessors (byte offset, field, category) may be added without
 * changing the pipeline's {@code Status} contract.
 */
public interface AvroDiagnostic
{
    /**
     * A short, human-readable reason for the rejection — e.g. an out-of-range union branch or enum ordinal,
     * a negative block count, or a truncated variable-length integer — or {@code null} when the rejecting
     * component supplied no message.
     */
    String message();
}
