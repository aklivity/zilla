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
 * Thrown when the input cannot be parsed into a valid value — whether because the bytes are malformed (for
 * example non-JSON input under {@code view: json}, a truncated Avro binary datum, or a malformed schema
 * document) or because they are structurally non-conformant to the schema (for example a wrong scalar type,
 * an unexpected field key, an unknown union branch, or a {@code fixed} of the wrong size). In every case no
 * valid value could be produced. Distinct from {@link AvroValidationException}, which signals a
 * structurally-valid value that violates a semantic rule. Both share the {@link AvroException} base so a
 * pipeline rejects either with a single catch.
 */
public class AvroParsingException extends AvroException
{
    private static final long serialVersionUID = 1L;

    public AvroParsingException(
        String message)
    {
        super(message);
    }

    public AvroParsingException(
        String message,
        Throwable cause)
    {
        super(message, cause);
    }
}
