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
package io.aklivity.zilla.runtime.classifier.presidio.internal;


import static java.nio.charset.StandardCharsets.UTF_8;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

final class PresidioSurrogate
{
    final String alias;
    final String original;
    final String token;
    final DirectBuffer originalBuffer;
    final DirectBuffer tokenBuffer;

    PresidioSurrogate(
        String alias,
        String original,
        String token)
    {
        this.alias = alias;
        this.original = original;
        this.token = token;
        this.originalBuffer = new UnsafeBuffer(original.getBytes(UTF_8));
        this.tokenBuffer = new UnsafeBuffer(token.getBytes(UTF_8));
    }
}
