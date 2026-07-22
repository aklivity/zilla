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
 * Thrown when the input cannot be parsed into a valid value — whether because the bytes are malformed (for
 * example non-JSON input under {@code view: json} or a truncated wire frame) or because they are
 * structurally non-conformant to the descriptor (for example an unknown message, an unknown field, an
 * unknown enum value, or an unsupported scalar type). In every case no valid value could be produced.
 * Distinct from {@link ProtobufValidationException}, which signals a structurally-valid value that violates
 * a semantic rule. Both share the {@link ProtobufException} base so a pipeline rejects either with a single
 * catch.
 */
public class ProtobufParsingException extends ProtobufException
{
    private static final long serialVersionUID = 1L;

    public ProtobufParsingException(
        String message)
    {
        super(message);
    }

    public ProtobufParsingException(
        String message,
        Throwable cause)
    {
        super(message, cause);
    }
}
