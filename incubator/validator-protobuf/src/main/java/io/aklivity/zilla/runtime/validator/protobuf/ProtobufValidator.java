/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.validator.protobuf;

import java.io.IOException;
import java.util.function.LongFunction;

import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectCache;
import org.agrona.io.DirectBufferInputStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.validator.protobuf.config.ProtobufValidatorConfig;
import io.aklivity.zilla.runtime.validator.protobuf.internal.parser.Protobuf3Lexer;
import io.aklivity.zilla.runtime.validator.protobuf.internal.parser.Protobuf3Parser;

public class ProtobufValidator
{
    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;

    private final Int2ObjectCache<FileDescriptor> descriptors;
    private final DirectBufferInputStream in;
    private final FileDescriptor[] dependencies;

    protected ProtobufValidator(
        ProtobufValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.handler = supplyCatalog.apply(cataloged.id);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.subject = catalog != null && catalog.subject != null
                ? catalog.subject
                : config.subject;
        this.descriptors = new Int2ObjectCache<>(1, 1024, i -> {});
        this.in = new DirectBufferInputStream();
        this.dependencies = new FileDescriptor[0];
    }

    protected boolean validate(
        int schemaId,
        DirectBuffer buffer,
        int index,
        int length)
    {
        boolean status = false;
        Descriptor descriptor = supplyDescriptor(schemaId).findMessageTypeByName(catalog.record);
        if (descriptor != null)
        {
            try
            {
                in.wrap(buffer, index, length);
                DynamicMessage message = DynamicMessage.parseFrom(descriptor, in);
                status = message.getUnknownFields().asMap().isEmpty();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        return status;
    }

    private FileDescriptor supplyDescriptor(
        int schemaId)
    {
        return descriptors.computeIfAbsent(schemaId, this::createDescriptors);
    }

    private FileDescriptor createDescriptors(
        int schemaId)
    {
        FileDescriptor descriptor = null;

        String schemaText = handler.resolve(schemaId);
        if (schemaText != null)
        {
            CharStream input = CharStreams.fromString(schemaText);
            Protobuf3Lexer lexer = new Protobuf3Lexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            Protobuf3Parser parser = new Protobuf3Parser(tokens);
            parser.setErrorHandler(new BailErrorStrategy());
            ParseTreeWalker walker = new ParseTreeWalker();

            ProtoListener listener = new ProtoListener();
            walker.walk(listener, parser.proto());

            try
            {
                descriptor = FileDescriptor.buildFrom(listener.build(), dependencies);
            }
            catch (DescriptorValidationException e)
            {
                e.printStackTrace();
            }
        }
        return descriptor;
    }
}
