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

import static java.util.Map.entry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;

import io.aklivity.zilla.runtime.validator.protobuf.internal.parser.Protobuf3BaseListener;
import io.aklivity.zilla.runtime.validator.protobuf.internal.parser.Protobuf3Parser;

public class ProtoListener extends Protobuf3BaseListener
{
    private static final Map<String, Type> TYPES = Map.ofEntries(
        entry("double", Type.TYPE_DOUBLE),
        entry("float", Type.TYPE_FLOAT),
        entry("int32", Type.TYPE_INT32),
        entry("int64", Type.TYPE_INT64),
        entry("uint32", Type.TYPE_UINT32),
        entry("uint64", Type.TYPE_UINT64),
        entry("sint32", Type.TYPE_SINT32),
        entry("sint64", Type.TYPE_SINT64),
        entry("fixed32", Type.TYPE_FIXED32),
        entry("fixed64", Type.TYPE_FIXED64),
        entry("sfixed32", Type.TYPE_SFIXED32),
        entry("sfixed64", Type.TYPE_SFIXED64),
        entry("bool", Type.TYPE_BOOL),
        entry("string", Type.TYPE_STRING),
        entry("bytes", Type.TYPE_BYTES)
    );

    private static final Map<String, Label> LABELS = Map.ofEntries(
        entry("optional", Label.LABEL_OPTIONAL),
        entry("required", Label.LABEL_REQUIRED),
        entry("repeated", Label.LABEL_REPEATED)
    );

    private String packageName;
    private List<String> imports;
    private final FileDescriptorProto.Builder builder;
    private Stack<String> messageHierarchy = new Stack<>();

    public ProtoListener()
    {
        this.imports = new ArrayList<>();
        this.builder = FileDescriptorProto.newBuilder();
    }

    @Override
    public void enterSyntax(
        Protobuf3Parser.SyntaxContext ctx)
    {
        builder.setSyntax(ctx.getChild(2).getText());
    }

    @Override
    public void enterPackageStatement(
        Protobuf3Parser.PackageStatementContext ctx)
    {
        packageName = ctx.fullIdent().getText();
        builder.setPackage(packageName);
    }

    @Override
    public void enterImportStatement(
        Protobuf3Parser.ImportStatementContext ctx)
    {
        String importStatement = ctx.strLit().getText();
        imports.add(importStatement);
        System.out.println("Import statements are currently not supported");
    }

    @Override
    public void enterMessageDef(
        Protobuf3Parser.MessageDefContext ctx)
    {
        DescriptorProto.Builder builder = DescriptorProto.newBuilder();
        String name = ctx.messageName().getText();
        builder.setName(name);
        messageHierarchy.push(name);

        for (Protobuf3Parser.MessageElementContext element : ctx.messageBody().messageElement())
        {
            if (element.field() != null)
            {
                builder.addField(processFieldElement(element.field()));
            }
            if (element.messageDef() != null)
            {
                builder.addNestedType(processNestedMessage(element.messageDef()));
            }
        }
        if (messageHierarchy.size() == 1)
        {
            this.builder.addMessageType(builder.build());
            builder.clear();
        }
    }

    @Override
    public void exitMessageDef(
        Protobuf3Parser.MessageDefContext ctx)
    {
        messageHierarchy.pop();
    }

    public DescriptorProtos.FileDescriptorProto build()
    {
        return builder.build();
    }

    private DescriptorProto processNestedMessage(
        Protobuf3Parser.MessageDefContext ctx)
    {
        DescriptorProto.Builder builder = DescriptorProto.newBuilder();
        String name = ctx.messageName().getText();
        builder.setName(name);

        for (Protobuf3Parser.MessageElementContext element : ctx.messageBody().messageElement())
        {
            if (element.field() != null)
            {
                builder.addField(processFieldElement(element.field()));
            }
            if (element.messageDef() != null)
            {
                builder.addNestedType(processNestedMessage(element.messageDef()));
            }
        }
        return builder.build();
    }

    private FieldDescriptorProto processFieldElement(
        Protobuf3Parser.FieldContext ctx)
    {
        FieldDescriptorProto.Builder builder = FieldDescriptorProto.newBuilder();
        String type = ctx.type_().getText();
        String name = ctx.fieldName().getText();
        String label = ctx.fieldLabel() != null ? ctx.fieldLabel().getText() : null;
        int number = Integer.parseInt(ctx.fieldNumber().getText());

        builder.setName(name);
        builder.setNumber(number);
        if (label != null)
        {
            builder.setLabel(LABELS.get(label));
        }
        if (TYPES.containsKey(type))
        {
            builder.setType(TYPES.get(type));
        }
        else
        {
            builder.setTypeName(type);
        }
        return builder.build();
    }
}
