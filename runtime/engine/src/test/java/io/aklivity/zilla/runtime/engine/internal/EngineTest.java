/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_CONFIG_URL;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_DIRECTORY;
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;

public class EngineTest
{
    private Properties properties;

    @Before
    public void initProperties()
    {
        properties = new Properties();
        properties.put(ENGINE_DIRECTORY.name(), "target/zilla-itests");
        properties.put(ENGINE_WORKERS.name(), "1");
    }

    @Test
    public void shouldConfigureEmpty()
    {
        EngineConfiguration config = new EngineConfiguration(properties);
        List<Throwable> errors = new LinkedList<>();
        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }
        finally
        {
            assertThat(errors, empty());
        }
    }

    @Test
    public void shouldConfigure()
    {
        String resource = String.format("%s-%s.json", getClass().getSimpleName(), "configure");
        URL configURL = getClass().getResource(resource);
        assert configURL != null;
        properties.put(ENGINE_CONFIG_URL.name(), configURL.toString());
        EngineConfiguration config = new EngineConfiguration(properties);
        List<Throwable> errors = new LinkedList<>();
        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }
        finally
        {
            assertThat(errors, empty());
        }
    }

    @Test
    public void shouldConfigureWithExpression()
    {
        String resource = String.format("%s-%s.yaml", getClass().getSimpleName(), "configure-expression");
        URL configURL = getClass().getResource(resource);
        assert configURL != null;
        properties.put(ENGINE_CONFIG_URL.name(), configURL.toString());
        EngineConfiguration config = new EngineConfiguration(properties);
        List<Throwable> errors = new LinkedList<>();
        try (Engine engine = Engine.builder()
            .config(config)
            .errorHandler(errors::add)
            .build())
        {
            engine.start();
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }
        finally
        {
            assertThat(errors, empty());
        }
    }

    @Test
    public void shouldConfigureComposite()
    {
        String resource = String.format("%s-%s.json", getClass().getSimpleName(), "configure-composite");
        URL configURL = getClass().getResource(resource);
        assert configURL != null;
        properties.put(ENGINE_CONFIG_URL.name(), configURL.toString());
        EngineConfiguration config = new EngineConfiguration(properties);
        List<Throwable> errors = new LinkedList<>();
        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }
        finally
        {
            assertThat(errors, empty());
        }
    }

    @Test
    public void shouldConfigureMultiple()
    {
        String resource = String.format("%s-%s.yaml", getClass().getSimpleName(), "configure-multiple");
        URL configURL = getClass().getResource(resource);
        assert configURL != null;
        properties.put(ENGINE_CONFIG_URL.name(), configURL.toString());
        EngineConfiguration config = new EngineConfiguration(properties);
        List<Throwable> errors = new LinkedList<>();
        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }
        finally
        {
            assertThat(errors, empty());
        }
    }

    @Test
    public void shouldNotConfigureDuplicateKey()
    {
        String resource = String.format("%s-%s.broken.json", getClass().getSimpleName(), "duplicate-key");
        URL configURL = getClass().getResource(resource);
        assert configURL != null;
        properties.put(ENGINE_CONFIG_URL.name(), configURL.toString());
        EngineConfiguration config = new EngineConfiguration(properties);
        List<Throwable> errors = new LinkedList<>();
        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }
        finally
        {
            assertThat(errors, not(empty()));
        }
    }

    @Test
    public void shouldNotConfigureUnknownScheme() throws Exception
    {
        List<Throwable> errors = new LinkedList<>();
        properties.put(ENGINE_CONFIG_URL.name(), "unknown://path");
        EngineConfiguration config = new EngineConfiguration(properties);
        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }
        finally
        {
            assertThat(errors, not(empty()));
        }
    }

    public static final class TestEngineExt implements EngineExtSpi
    {
        public static volatile CountDownLatch registerLatch = new CountDownLatch(1);
        public static volatile CountDownLatch unregisterLatch = new CountDownLatch(1);

        @Override
        public void onRegistered(
            EngineExtContext context)
        {
            if (registerLatch != null)
            {
                registerLatch.countDown();
            }
        }

        @Override
        public void onUnregistered(
            EngineExtContext context)
        {
            if (unregisterLatch != null)
            {
                unregisterLatch.countDown();
            }
        }
    }
}
