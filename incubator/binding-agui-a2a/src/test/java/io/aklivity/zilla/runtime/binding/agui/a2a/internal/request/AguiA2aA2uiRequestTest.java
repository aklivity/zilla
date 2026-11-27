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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.request;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.Set;

import org.junit.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.internal.buffer.DefaultBufferPool;

public class AguiA2aA2uiRequestTest
{
    private static final int[] CHUNKS = {1, 7, 16, 64, 1000};
    private static final int SLOT = 4096;
    private static final String MESSAGES = "\"messages\":[{\"id\":\"m\",\"role\":\"user\",\"content\":\"x\"}]";

    @Test
    public void shouldReadVersionKeysOfClientCapabilities()
    {
        assertVersions("{\"threadId\":\"t\",\"runId\":\"r\"," + MESSAGES +
            ",\"forwardedProps\":{\"a2uiClientCapabilities\":{\"v0.9\":{\"supportedCatalogIds\":[\"c\"]}}}}", Set.of("v0.9"));
    }

    @Test
    public void shouldReadSeveralVersionKeys()
    {
        assertVersions("{\"threadId\":\"t\",\"runId\":\"r\"," + MESSAGES +
            ",\"forwardedProps\":{\"tenant\":\"acme\",\"a2uiClientCapabilities\":{\"v0.8\":{},\"v0.9\":{}}}}",
            Set.of("v0.8", "v0.9"));
    }

    @Test
    public void shouldNotReadVersionFromClientDataModel()
    {
        assertVersions("{\"threadId\":\"t\",\"runId\":\"r\"," + MESSAGES +
            ",\"forwardedProps\":{\"a2uiClientDataModel\":{\"v0.9\":{},\"version\":\"v0.9\"}}}", Set.of());
    }

    @Test
    public void shouldNotReadVersionWhenCapabilitiesHaveNoVersionKey()
    {
        assertVersions("{\"threadId\":\"t\",\"runId\":\"r\"," + MESSAGES +
            ",\"forwardedProps\":{\"a2uiClientCapabilities\":{\"supportedCatalogIds\":[\"c\"]}}}", Set.of());
    }

    @Test
    public void shouldNotReadVersionKeysOfOtherMembers()
    {
        assertVersions("{\"threadId\":\"t\",\"runId\":\"r\"," + MESSAGES +
            ",\"forwardedProps\":{\"a2uiClientCapabilities\":{},\"other\":{\"v0.9\":{}}," +
            "\"inner\":{\"a2uiClientCapabilities\":{\"v0.8\":{}}}}}", Set.of());
    }

    @Test
    public void shouldNotReadWithoutCapabilities()
    {
        assertVersions("{\"threadId\":\"t\",\"runId\":\"r\"," + MESSAGES + ",\"forwardedProps\":{\"tenant\":\"acme\"}}",
            Set.of());
    }

    private static void assertVersions(
        String input,
        Set<String> expected)
    {
        for (int chunk : CHUNKS)
        {
            final DefaultBufferPool pool = new DefaultBufferPool(SLOT, SLOT);
            final AguiA2aRequestDecoder decoder =
                new AguiA2aRequestDecoder(1L, "t", "r", null, new AguiA2aRetention(pool));

            final byte[] bytes = input.getBytes(UTF_8);
            byte[] window = new byte[0];
            int carried = 0;
            int index = 0;

            while (index < bytes.length)
            {
                final int length = Math.min(chunk, bytes.length - index);
                final boolean last = index + length == bytes.length;
                if (window.length < carried + length)
                {
                    final byte[] resized = new byte[carried + length];
                    System.arraycopy(window, 0, resized, 0, carried);
                    window = resized;
                }
                System.arraycopy(bytes, index, window, carried, length);
                index += length;

                final int limit = carried + length;
                final int progress = decoder.decode(new UnsafeBufferEx(window), 0, limit, last);
                carried = limit - progress;
                System.arraycopy(window, progress, window, 0, carried);
            }

            assertThat("chunk " + chunk, decoder.a2uiVersions(), equalTo(expected));
            decoder.retained().release();
        }
    }
}
