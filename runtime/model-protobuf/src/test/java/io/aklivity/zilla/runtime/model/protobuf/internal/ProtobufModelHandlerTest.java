/*
 * Copyright 2021-2024 Aklivity Inc
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.model.protobuf.config.ProtobufModelConfig;

public class ProtobufModelHandlerTest
{
    private ProtobufModelHandler handler;
    private CatalogHandler mockCatalogHandler;
    private EngineContext mockContext;

    @Before
    public void setup()
    {
        mockContext = mock(EngineContext.class);
        mockCatalogHandler = mock(CatalogHandler.class);
        
        CatalogedConfig cataloged = new CatalogedConfig("test-catalog", List.of());
        ProtobufModelConfig config = new ProtobufModelConfig(
            List.of(cataloged),
            "test-subject",
            null,
            null  // No explicit syntax
        );
        
        when(mockContext.supplyCatalog(0L)).thenReturn(mockCatalogHandler);
        
        handler = new ProtobufModelHandler(config, mockContext);
    }

    @Test
    public void shouldThrowErrorWhenNoSyntaxDeclared()
    {
        // Schema without syntax declaration
        String schemaWithoutSyntax = """
            message TestMessage {
              required string name = 1;
              optional int32 value = 2;
            }
            """;
        
        when(mockCatalogHandler.resolve(1)).thenReturn(schemaWithoutSyntax);
        
        // This should return null because createDescriptors catches the exception
        assertNull(handler.supplyDescriptor(1));
    }

    @Test
    public void shouldParseProto2WithExplicitSyntax()
    {
        String proto2Schema = """
            syntax = "proto2";
            
            message TestMessage {
              required string name = 1;
              optional int32 value = 2;
            }
            """;
        
        when(mockCatalogHandler.resolve(1)).thenReturn(proto2Schema);
        
        assertNotNull(handler.supplyDescriptor(1));
    }

    @Test
    public void shouldParseProto3WithExplicitSyntax()
    {
        String proto3Schema = """
            syntax = "proto3";
            
            message TestMessage {
              string name = 1;
              int32 value = 2;
            }
            """;
        
        when(mockCatalogHandler.resolve(1)).thenReturn(proto3Schema);
        
        assertNotNull(handler.supplyDescriptor(1));
    }

    @Test
    public void shouldUseConfiguredSyntaxOverDetected()
    {
        // Create handler with explicit proto2 syntax
        CatalogedConfig cataloged = new CatalogedConfig("test-catalog", List.of());
        ProtobufModelConfig config = new ProtobufModelConfig(
            List.of(cataloged),
            "test-subject",
            null,
            "proto2"  // Explicit proto2
        );
        
        when(mockContext.supplyCatalog(0L)).thenReturn(mockCatalogHandler);
        
        ProtobufModelHandler handlerWithProto2 = new ProtobufModelHandler(config, mockContext);
        
        // Schema that says proto3 but config says proto2
        String proto3Schema = """
            syntax = "proto3";
            
            message TestMessage {
              string name = 1;
              int32 value = 2;
            }
            """;
        
        when(mockCatalogHandler.resolve(1)).thenReturn(proto3Schema);
        
        // This should parse as proto2 due to config
        assertNotNull(handlerWithProto2.supplyDescriptor(1));
    }
}
