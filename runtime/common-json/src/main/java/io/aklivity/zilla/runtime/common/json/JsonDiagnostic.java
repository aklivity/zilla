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
package io.aklivity.zilla.runtime.common.json;

/**
 * A description of a terminal {@link JsonPipeline.Status#REJECTED} failure, populated at the point of
 * detection — by the parser for malformed or schema-invalid input — and handed to a {@link JsonReporter}.
 * <p>
 * The instance is a reused, call-scoped view: it is valid only for the duration of the
 * {@link JsonReporter#rejected(JsonDiagnostic)} callback. A reporter that needs to retain any of it must
 * copy the value out immediately, before returning.
 * <p>
 * Starts with {@link #message()}; structured accessors (byte offset, JSON pointer, category) may be added
 * without changing the pipeline's {@code Status} contract.
 */
public interface JsonDiagnostic
{
    /**
     * A short, human-readable reason for the rejection — e.g. an invalid token, a schema-constraint
     * violation, or truncated input — or {@code null} when the rejecting component supplied no message.
     */
    String message();
}
