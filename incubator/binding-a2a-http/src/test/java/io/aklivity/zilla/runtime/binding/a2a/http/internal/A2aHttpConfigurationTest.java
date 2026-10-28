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
package io.aklivity.zilla.runtime.binding.a2a.http.internal;

import static io.aklivity.zilla.runtime.binding.a2a.http.internal.A2aHttpConfiguration.A2A_HTTP_LOCK_TTL_SECONDS;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.A2aHttpConfiguration.A2A_HTTP_TASK_ID;
import static io.aklivity.zilla.runtime.binding.a2a.http.internal.A2aHttpConfiguration.A2A_HTTP_TIMEOUT_SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Duration;
import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class A2aHttpConfigurationTest
{
    public static final String A2A_HTTP_TASK_ID_NAME = "zilla.binding.a2a.http.task.id";
    public static final String A2A_HTTP_LOCK_TTL_SECONDS_NAME = "zilla.binding.a2a.http.lock.ttl.seconds";
    public static final String A2A_HTTP_TIMEOUT_SECONDS_NAME = "zilla.binding.a2a.http.timeout.seconds";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(A2A_HTTP_TASK_ID.name(), A2A_HTTP_TASK_ID_NAME);
        assertEquals(A2A_HTTP_LOCK_TTL_SECONDS.name(), A2A_HTTP_LOCK_TTL_SECONDS_NAME);
        assertEquals(A2A_HTTP_TIMEOUT_SECONDS.name(), A2A_HTTP_TIMEOUT_SECONDS_NAME);
    }

    public static String taskId(
        long affinity)
    {
        return "task-" + affinity;
    }

    @Test
    public void shouldDefaultDurationsAndTaskIdSupplier() throws Exception
    {
        final A2aHttpConfiguration config = new A2aHttpConfiguration();

        assertEquals(Duration.ofSeconds(30L), config.lockTtl());
        assertEquals(Duration.ofSeconds(30L), config.timeout());
        assertNotNull(config.taskIdSupplier().apply(0x1234L));
    }

    @Test
    public void shouldDecodeTaskIdSupplierReference() throws Exception
    {
        final Properties properties = new Properties();
        properties.setProperty(A2A_HTTP_TASK_ID_NAME, "%s::taskId".formatted(A2aHttpConfigurationTest.class.getName()));
        final A2aHttpConfiguration config = new A2aHttpConfiguration(new Configuration(properties));

        assertEquals("task-7", config.taskIdSupplier().apply(7L));
    }

    @Test(expected = Throwable.class)
    public void shouldRejectUnresolvableTaskIdSupplierReference() throws Exception
    {
        final Properties properties = new Properties();
        properties.setProperty(A2A_HTTP_TASK_ID_NAME, "not-a-valid-reference");

        new A2aHttpConfiguration(new Configuration(properties)).taskIdSupplier();
    }
}
