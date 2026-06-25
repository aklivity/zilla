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
package io.aklivity.zilla.runtime.common.avro;

/**
 * The base of the Avro pipeline's failure hierarchy. A pipeline catches this single type so that both a
 * parse failure (the input bytes cannot be parsed) and a validation failure (the input parses but violates
 * the schema) map to a clean reject. Mirrors the {@code jakarta.json} convention where
 * {@code JsonParsingException} is a {@code JsonException}.
 */
public class AvroException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public AvroException(
        String message)
    {
        super(message);
    }

    public AvroException(
        String message,
        Throwable cause)
    {
        super(message, cause);
    }
}
