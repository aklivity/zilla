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
 * Reserved for a structurally-valid value that violates a semantic rule beyond the descriptor's structure —
 * for example a data contract or a constraint not expressible in the descriptor itself. A failure to produce
 * a valid value at all — malformed bytes or structural descriptor non-conformance — is a
 * {@link ProtobufParsingException} instead; both share the {@link ProtobufException} base so a pipeline
 * rejects either with a single catch. Currently unused: there is no semantic-validation stage yet, so
 * nothing throws this; it is the seam where data-contract enforcement will live.
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
