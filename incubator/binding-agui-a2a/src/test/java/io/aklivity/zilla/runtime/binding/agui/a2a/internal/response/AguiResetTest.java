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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aBearerError;
import io.aklivity.zilla.runtime.binding.agui.a2a.internal.types.stream.A2aResetExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class AguiResetTest
{
    private final A2aResetExFW.Builder resetExRW = new A2aResetExFW.Builder();

    @Test
    public void shouldMapInvalidTokenToUnauthorized()
    {
        assertEquals(new AguiReset(502, "agent_unauthorized", null), AguiReset.of(bearer(A2aBearerError.INVALID_TOKEN)));
    }

    @Test
    public void shouldMapInvalidRequestToUnauthorized()
    {
        assertEquals(new AguiReset(502, "agent_unauthorized", null), AguiReset.of(bearer(A2aBearerError.INVALID_REQUEST)));
    }

    @Test
    public void shouldMapInsufficientScopeToForbidden()
    {
        assertEquals(new AguiReset(403, "forbidden", null), AguiReset.of(bearer(A2aBearerError.INSUFFICIENT_SCOPE)));
    }

    @Test
    public void shouldMapNotFound()
    {
        assertEquals(new AguiReset(404, "agent_not_found", null), AguiReset.of(error(404, "Agent not found")));
    }

    @Test
    public void shouldMapInvalidParamsToInvalidRunWithMessage()
    {
        assertEquals(new AguiReset(422, "invalid_run", "Invalid params"), AguiReset.of(error(-32602, "Invalid params")));
        assertEquals(new AguiReset(422, "invalid_run", "Bad"), AguiReset.of(error(400, "Bad")));
    }

    @Test
    public void shouldMapServerErrorToBadGateway()
    {
        assertEquals(new AguiReset(502, null, null), AguiReset.of(error(500, "Internal error")));
        assertEquals(new AguiReset(502, null, null), AguiReset.of(error(-32603, "Internal error")));
    }

    @Test
    public void shouldMapMissingExtensionToBadGateway()
    {
        assertEquals(new AguiReset(502, null, null), AguiReset.of(null));
        assertNull(AguiReset.of(null).code());
    }

    private A2aResetExFW bearer(
        A2aBearerError error)
    {
        return resetExRW.wrap(new UnsafeBufferEx(new byte[256]), 0, 256)
            .typeId(1)
            .bearer(b -> b.realm("a2a").error(e -> e.set(error)))
            .build();
    }

    private A2aResetExFW error(
        int code,
        String message)
    {
        return resetExRW.wrap(new UnsafeBufferEx(new byte[256]), 0, 256)
            .typeId(1)
            .error(e -> e.code(code).message(message))
            .build();
    }
}
