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
 * Thrown when Avro binary cannot be decoded against its schema, or an event stream cannot be
 * encoded to valid Avro binary — for example a truncated variable-length integer, an out-of-range
 * union branch or enum ordinal, or a negative block count.
 */
public class AvroValidationException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public AvroValidationException(
        String message)
    {
        super(message);
    }

    public AvroValidationException(
        String message,
        Throwable cause)
    {
        super(message, cause);
    }
}
