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
package io.aklivity.zilla.runtime.model.protobuf.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.config.engine.GenericCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogConfig;
import io.aklivity.zilla.config.engine.test.internal.catalog.config.TestCatalogOptionsConfig;
import io.aklivity.zilla.config.model.protobuf.ProtobufModelConfig;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.test.internal.catalog.TestCatalogHandler;

public class ProtobufModelOverlayTest
{
    private static final int OVERLAY_SCHEMA_ID_BEFORE = 100;
    private static final int OVERLAY_SCHEMA_ID_AFTER = 200;

    private static final String SCHEMA = """
        syntax = "proto3";
        message SimpleMessage
        {
          string content = 1;
        }
        """;

    private static final String PATCH_TAGS_CONTENT =
        "[{\"field\":\"SimpleMessage.content\",\"options\":{\"zilla\":{\"tags\":[\"PII\"]}}}]";

    private static final String PATCH_NOOP = "[]";

    private EngineContext context;

    @Before
    public void init()
    {
        context = mock(EngineContext.class);
    }

    @Test
    public void shouldApplyOverlayBeforeCachingSchema()
    {
        MovingOverlayCatalogHandler overlay = new MovingOverlayCatalogHandler(PATCH_TAGS_CONTENT, PATCH_NOOP);
        TestHandler handler = newHandlerWithOverlay(overlay);

        ProtobufSchema schema = handler.supplySchema(1);

        assertEquals("{\"zilla\":{\"tags\":[\"PII\"]}}",
            schema.message("SimpleMessage").field("content").options().toString());
    }

    @Test
    public void shouldNotServeStaleCompiledSchemaWhenNonPinnedOverlayResolvesToNewId()
    {
        MovingOverlayCatalogHandler overlay = new MovingOverlayCatalogHandler(PATCH_TAGS_CONTENT, PATCH_NOOP);
        TestHandler handler = newHandlerWithOverlay(overlay);

        // overlay's "latest" reference currently resolves to OVERLAY_SCHEMA_ID_BEFORE, whose patch
        // tags "content" with a PII annotation
        ProtobufSchema first = handler.supplySchema(1);
        assertEquals("{\"zilla\":{\"tags\":[\"PII\"]}}",
            first.message("SimpleMessage").field("content").options().toString());

        // the overlay catalog entry now resolves "latest" to OVERLAY_SCHEMA_ID_AFTER, a no-op patch,
        // with the base schema's own schemaId unchanged -- the cache must not keep serving the
        // compiled schema from the first, now-stale overlay resolution
        overlay.advance();

        ProtobufSchema second = handler.supplySchema(1);
        assertEquals("{}", second.message("SimpleMessage").field("content").options().toString());
    }

    private TestHandler newHandlerWithOverlay(
        CatalogHandler overlayHandler)
    {
        TestCatalogConfig catalog = GenericCatalogConfig.builder(TestCatalogConfig::new)
            .namespace("test")
            .name("test0")
            .type("test")
            .options(TestCatalogOptionsConfig::builder)
                .id(1)
                .schema(SCHEMA)
                .build()
            .build();

        ProtobufModelConfig model = ProtobufModelConfig.builder()
            .catalog()
                .name("test0")
                .schema()
                    .subject("test-value")
                    .version("latest")
                    .record("SimpleMessage")
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

        return new TestHandler(model, context);
    }

    private static final class TestHandler extends ProtobufModelHandler
    {
        private TestHandler(
            ProtobufModelConfig config,
            EngineContext context)
        {
            super(config, context);
        }
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
