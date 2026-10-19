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
 * Thrown by the descriptor-level semantic validation stage — currently, proto2 {@code required}-field
 * presence per message scope — for a value the core parser already decoded structurally but that violates
 * a rule beyond the wire-level descriptor structure. A failure to produce a valid value at all — malformed
 * bytes or a wire-type/declared-type mismatch — is a {@link ProtobufParsingException} instead; both share
 * the {@link ProtobufException} base so a pipeline rejects either with a single catch. This is also the
 * seam where broader data-contract enforcement will live.
 */
public class ProtobufValidationException extends ProtobufException
{
    private static final long serialVersionUID = 1L;

    public ProtobufValidationException(
        String message)
    {
        super(message);
    }

    public ProtobufValidationException(
        String message,
        Throwable cause)
    {
        super(message, cause);
    }
}
