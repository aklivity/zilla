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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import jakarta.json.stream.JsonParser.Event;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

final class A2aTenantInjector extends A2aJsonRewriter
{
    int inject(
        String tenant,
        DirectBufferEx input,
        int inputOffset,
        int inputLimit,
        MutableDirectBufferEx output,
        int outputOffset)
    {
        begin(input, inputOffset, inputLimit, output, outputOffset);

        final boolean declared = stringOfTopLevel("tenant") != null;
        rewind();

        parser.next();
        generator.writeStartObject();

        if (!declared)
        {
            generator.write("tenant", tenant);
        }

        for (Event event = parser.next(); event != Event.END_OBJECT; event = parser.next())
        {
            generator.writeKey(parser.getString());
            copyValue(parser.next());
        }
        generator.writeEnd();

        return end();
    }
}
