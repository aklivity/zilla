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
package io.aklivity.zilla.runtime.model.json.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.config.model.json.JsonModelConfig;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.model.ModelCache;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;
import io.aklivity.zilla.runtime.engine.model.ModelPipeline;
import io.aklivity.zilla.runtime.engine.model.ModelPipelineResult;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;

public class JsonModelOverlayTest
{
    private static final int FLAGS_COMPLETE = 0x03;
    private static final int OVERLAY_SCHEMA_ID_BEFORE = 100;
    private static final int OVERLAY_SCHEMA_ID_AFTER = 200;

    private static final String BASE_SCHEMA = "{" +
        "\"type\":\"object\"," +
        "\"properties\":{\"id\":{\"type\":\"string\"}}," +
        "\"required\":[\"id\"]" +
        "}";

    private static final String PATCH_ADDS_STATUS_REQUIRED =
        "[{\"op\":\"add\",\"path\":\"/required/-\",\"value\":\"status\"}]";

    private static final String PATCH_NOOP = "[]";

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
        MessageConsumer eventWriter = mock(MessageConsumer.class);
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.supplyEventWriter()).thenReturn(eventWriter);
    }

    @Test
    public void shouldApplyOverlayBeforeCompilingSchema()
    {
        MovingOverlayCatalogHandler overlay = new MovingOverlayCatalogHandler(PATCH_ADDS_STATUS_REQUIRED, PATCH_NOOP);
        JsonModelHandlerImpl handler = newHandlerWithOverlay(overlay);

        byte[] missingStatus = "{\"id\":\"1\"}".getBytes(UTF_8);

        assertEquals(ModelStatus.REJECTED, decode(handler, missingStatus).status());
    }

    @Test
    public void shouldNotServeStaleCompiledSchemaWhenNonPinnedOverlayResolvesToNewId()
    {
        MovingOverlayCatalogHandler overlay = new MovingOverlayCatalogHandler(PATCH_ADDS_STATUS_REQUIRED, PATCH_NOOP);
        JsonModelHandlerImpl handler = newHandlerWithOverlay(overlay);

        byte[] missingStatus = "{\"id\":\"1\"}".getBytes(UTF_8);

        // overlay's "latest" reference currently resolves to OVERLAY_SCHEMA_ID_BEFORE, whose patch
        // tightens the base schema to also require "status"
        assertEquals(ModelStatus.REJECTED, decode(handler, missingStatus).status());

        // the overlay catalog entry now resolves "latest" to OVERLAY_SCHEMA_ID_AFTER, a no-op patch,
        // with the base schema's own schemaId unchanged -- the cache must not keep serving the
        // compiled schema from the first, now-stale overlay resolution
        overlay.advance();

        assertEquals(ModelStatus.COMPLETE, decode(handler, missingStatus).status());
    }

    private static ModelPipelineResult decode(
        JsonModelHandlerImpl handler,
        byte[] in)
    {
        ModelPipeline pipeline = handler.supplyDecoder(ModelEnvelope.NONE, ModelTransform.NONE, ModelCache.NONE);
        MutableDirectBufferEx dst = new UnsafeBufferEx(new byte[256]);
        return pipeline.transform(0L, 0L, 0L, FLAGS_COMPLETE,
            new UnsafeBufferEx(in), 0, in.length, dst, 0, dst.capacity());
    }

    private JsonModelHandlerImpl newHandlerWithOverlay(
        CatalogHandler overlayHandler)
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(9)
                .schema(BASE_SCHEMA)
                .build()
            .build();

        JsonModelConfig model = JsonModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .subject("test")
                    .version("latest")
                    .overlay()
                        .name("test1")
                        .schema()
                            .subject("test-overlay")
                            .version("latest")
                            .build()
                        .build()
                    .build()
                .build()
            .build();
        model.cataloged.get(0).id = catalog.id;
        model.cataloged.get(0).schemas.get(0).overlay.id = 10L;

        when(context.supplyCatalog(catalog.id)).thenReturn(new TestCatalogHandler(catalog.options));
        when(context.supplyCatalog(10L)).thenReturn(overlayHandler);

        return new JsonModelHandlerImpl(model, context, List.of());
    }

    private static final class MovingOverlayCatalogHandler implements CatalogHandler
    {
        private final String patchBefore;
        private final String patchAfter;
        private boolean moved;

        private MovingOverlayCatalogHandler(
            String patchBefore,
            String patchAfter)
        {
            this.patchBefore = patchBefore;
            this.patchAfter = patchAfter;
        }

        private void advance()
        {
            this.moved = true;
        }

        @Override
        public int resolve(
            String subject,
            String version)
        {
            return moved ? OVERLAY_SCHEMA_ID_AFTER : OVERLAY_SCHEMA_ID_BEFORE;
        }

        @Override
        public String resolve(
            int schemaId)
        {
            String resolved = null;
            if (schemaId == OVERLAY_SCHEMA_ID_BEFORE)
            {
                resolved = patchBefore;
            }
            else if (schemaId == OVERLAY_SCHEMA_ID_AFTER)
            {
                resolved = patchAfter;
            }
            return resolved;
        }
    }
}
