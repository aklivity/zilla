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
 * Thrown when an event stream that parsed successfully nevertheless violates the descriptor it is decoded
 * or encoded against — for example an unknown field, an unknown enum value, or a wrong scalar type. A
 * parse failure (bytes that cannot be parsed at all) is a {@link ProtobufParsingException} instead; both
 * share the {@link ProtobufException} base so a pipeline rejects either with a single catch.
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
