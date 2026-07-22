/*
 * Copyright 2021-2026 Aklivity Inc.
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
import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKER_CAPACITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.binding.function.MessageReader;
import io.aklivity.zilla.runtime.engine.event.EventFormatter;
import io.aklivity.zilla.runtime.engine.ext.EngineExtContext;
import io.aklivity.zilla.runtime.engine.ext.EngineExtSpi;
import io.aklivity.zilla.runtime.engine.internal.event.EngineEventContext;

public class EngineTest
{
    private Properties properties;

    @Before
    public void initProperties()
    {
        properties = new Properties();
        properties.put(ENGINE_DIRECTORY.name(), "target/zilla-itests");
        properties.put(ENGINE_WORKERS.name(), "1");
        properties.put(ENGINE_WORKER_CAPACITY.name(), "64");
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
    public void shouldInitThenStart()
    {
        EngineConfiguration config = new EngineConfiguration(properties);
        List<Throwable> errors = new LinkedList<>();
        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.init();
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
    public void shouldInitIdempotent()
    {
        EngineConfiguration config = new EngineConfiguration(properties);
        List<Throwable> errors = new LinkedList<>();
        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.init();
            engine.init();
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
    public void shouldConfigureWithExpressionInvalid()
    {
        String resource = String.format("%s-%s.yaml", getClass().getSimpleName(), "configure-expression-invalid");
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
            assertTrue(!errors.isEmpty());
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
    public void shouldNotConfigureBindingValidationFailure()
    {
        String resource = String.format("%s-%s.yaml", getClass().getSimpleName(), "configure-validation-invalid");
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

    @Test
    public void shouldReadEvents()
    {
        List<Throwable> errors = new LinkedList<>();
        EngineConfiguration config = new EngineConfiguration(properties);
        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();
            MessageReader events = engine.supplyEventReader();
            events.read((m, b, i, l) -> {}, 1);
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
    public void shouldSupplyQName()
    {
        List<Throwable> errors = new LinkedList<>();
        EngineConfiguration config = new EngineConfiguration(properties);
        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();
            long namespacedId = engine.supplyNamespacedId("namespace1", "binding1");
            String qname = engine.supplyQName(namespacedId);
            assertThat(qname, equalTo("namespace1:binding1"));
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
    public void shouldSupplyEventFormatter()
    {
        List<Throwable> errors = new LinkedList<>();
        EngineConfiguration config = new EngineConfiguration(properties);
        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();
            EventFormatter formatter = engine.supplyEventFormatter();
            assertThat(formatter, notNullValue());
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
    public void shouldPreserveEventsWhenAttachingReadonlyEngine()
    {
        List<Throwable> errors = new LinkedList<>();
        EngineConfiguration config = new EngineConfiguration(properties);

        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();
            new EngineEventContext(engine).started();
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }

        int[] eventCount = { 0 };
        try (Engine readonlyEngine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .readonly()
                .build())
        {
            readonlyEngine.start();
            MessageReader events = readonlyEngine.supplyEventReader();
            events.read((msgTypeId, buffer, index, length) -> eventCount[0]++, 10);
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }

        assertThat(errors, empty());
        assertThat(eventCount[0], greaterThan(0));
    }

    @Test
    public void shouldNotWriteEventWhenClosingReadonlyEngine()
    {
        List<Throwable> errors = new LinkedList<>();
        EngineConfiguration config = new EngineConfiguration(properties);

        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();
            new EngineEventContext(engine).started();
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }

        int[] eventCountBeforeClose = { 0 };
        try (Engine readonlyEngine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .readonly()
                .build())
        {
            readonlyEngine.start();
            MessageReader events = readonlyEngine.supplyEventReader();
            events.read((msgTypeId, buffer, index, length) -> eventCountBeforeClose[0]++, 10);
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }

        int[] eventCountAfterClose = { 0 };
        try (Engine readonlyEngine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .readonly()
                .build())
        {
            readonlyEngine.start();
            MessageReader events = readonlyEngine.supplyEventReader();
            events.read((msgTypeId, buffer, index, length) -> eventCountAfterClose[0]++, 10);
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }

        assertThat(errors, empty());
        assertThat(eventCountAfterClose[0], equalTo(eventCountBeforeClose[0]));
    }

    @Test
    public void shouldNotResetTuningWhenAttachingReadonlyEngine() throws Exception
    {
        List<Throwable> errors = new LinkedList<>();
        EngineConfiguration config = new EngineConfiguration(properties);

        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            engine.start();

            Path tuning = config.directory().resolve("tuning");
            Object fileKeyBefore = Files.readAttributes(tuning, "unix:ino").get("ino");

            try (Engine readonlyEngine = Engine.builder()
                    .config(config)
                    .errorHandler(errors::add)
                    .readonly()
                    .build())
            {
                readonlyEngine.start();
            }

            Object fileKeyAfter = Files.readAttributes(tuning, "unix:ino").get("ino");

            assertThat(fileKeyAfter, equalTo(fileKeyBefore));
        }
        catch (Throwable ex)
        {
            errors.add(ex);
        }

        assertThat(errors, empty());
    }

    @Test
    public void shouldNotMarkReadyUntilBindingsAttached() throws Exception
    {
        List<Throwable> errors = new LinkedList<>();
        EngineConfiguration config = new EngineConfiguration(properties);
        Instant beforeConstruct = Instant.now();

        try (Engine engine = Engine.builder()
                .config(config)
                .errorHandler(errors::add)
                .build())
        {
            assertThat(Ready.initialized(config.directory(), beforeConstruct), equalTo(false));

            engine.start();

            assertThat(Ready.initialized(config.directory(), beforeConstruct), equalTo(true));
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
