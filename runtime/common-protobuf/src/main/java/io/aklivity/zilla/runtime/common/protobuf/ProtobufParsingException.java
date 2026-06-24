/*
 * Copyright 2021-2024 Aklivity Inc
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
 * Thrown when the input bytes cannot be parsed into a structured form at all — for example malformed or
 * non-JSON input under {@code view: json}, or a truncated wire frame. Distinct from
 * {@link ProtobufValidationException}, which signals input that parsed successfully but violates the
 * descriptor. Both share the {@link ProtobufException} base so a pipeline rejects either with a single
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
