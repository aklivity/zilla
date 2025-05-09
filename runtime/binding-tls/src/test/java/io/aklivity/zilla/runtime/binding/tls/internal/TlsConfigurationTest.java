/*
 * Copyright 2021-2024 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.tls.internal;

import static io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration.TLS_HANDSHAKE_TIMEOUT;
import static io.aklivity.zilla.runtime.binding.tls.internal.TlsConfiguration.TLS_HANDSHAKE_WINDOW_BYTES;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_TASK_PARALLELISM;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TlsConfigurationTest
{
    public static final String TLS_HANDSHAKE_WINDOW_BYTES_NAME = "zilla.binding.tls.handshake.window.bytes";
    public static final String TLS_HANDSHAKE_TIMEOUT_NAME = "zilla.binding.tls.handshake.timeout";
    public static final String ENGINE_TASK_PARALLELISM_NAME = "zilla.engine.task.parallelism";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(TLS_HANDSHAKE_WINDOW_BYTES.name(), TLS_HANDSHAKE_WINDOW_BYTES_NAME);
        assertEquals(TLS_HANDSHAKE_TIMEOUT.name(), TLS_HANDSHAKE_TIMEOUT_NAME);
        assertEquals(ENGINE_TASK_PARALLELISM.name(), ENGINE_TASK_PARALLELISM_NAME);
    }
}
