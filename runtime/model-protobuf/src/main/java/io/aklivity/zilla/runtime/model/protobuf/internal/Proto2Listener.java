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

import static java.util.Map.entry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;

import io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf2BaseListener;
import io.aklivity.zilla.runtime.model.protobuf.internal.parser.Protobuf2Parser;

public class Proto2Listener extends Protobuf2BaseListener
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
    private final FileDescriptorProto.Builder fileBuilder;
    private Stack<DescriptorProto.Builder> messageStack = new Stack<>();
    private Stack<String> messageHierarchy = new Stack<>();
    private Stack<OneofDescriptorProto.Builder> oneofStack = new Stack<>();
    private Stack<ServiceDescriptorProto.Builder> serviceStack = new Stack<>();
    private Stack<EnumDescriptorProto.Builder> enumStack = new Stack<>();
    private int currentOneofIndex = 0;
    private Map<String, Integer> oneofIndexMap = new HashMap<>();

    public Proto2Listener()
    {
        this.imports = new ArrayList<>();
        this.fileBuilder = FileDescriptorProto.newBuilder();
    }

    @Override
    public void enterSyntax(
        Protobuf2Parser.SyntaxContext ctx)
    {
        String syntax = ctx.getChild(2).getText();
        // Remove quotes if present
        if (syntax.startsWith("\"") && syntax.endsWith("\"") ||
                syntax.startsWith("'") && syntax.endsWith("'"))
        {
            syntax = syntax.substring(1, syntax.length() - 1);
        }
        fileBuilder.setSyntax(syntax);
    }

    @Override
    public void enterPackageStatement(
        Protobuf2Parser.PackageStatementContext ctx)
    {
        packageName = ctx.fullIdent().getText();
        fileBuilder.setPackage(packageName);
    }

    @Override
    public void enterImportStatement(
        Protobuf2Parser.ImportStatementContext ctx)
    {
        String importPath = stripQuotes(ctx.strLit().getText());
        imports.add(importPath);
        fileBuilder.addDependency(importPath);

        if (ctx.PUBLIC() != null)
        {
            fileBuilder.addPublicDependency(fileBuilder.getDependencyCount() - 1);
        }
        else if (ctx.WEAK() != null)
        {
            fileBuilder.addWeakDependency(fileBuilder.getDependencyCount() - 1);
        }
    }

    @Override
    public void enterOptionStatement(
        Protobuf2Parser.OptionStatementContext ctx)
    {
        // Handle file-level options
        String optionName = ctx.optionName().getText();
        String value = ctx.constant().getText();

        FileOptions.Builder optionsBuilder = fileBuilder.getOptionsBuilder();
        // Handle common file options
        switch (optionName)
        {
        case "java_package":
            optionsBuilder.setJavaPackage(stripQuotes(value));
            break;
        case "java_outer_classname":
            optionsBuilder.setJavaOuterClassname(stripQuotes(value));
            break;
        case "java_multiple_files":
            optionsBuilder.setJavaMultipleFiles(Boolean.parseBoolean(value));
            break;
        case "optimize_for":
            break;
        }
    }

    @Override
    public void enterMessageDef(
        Protobuf2Parser.MessageDefContext ctx)
    {
        DescriptorProto.Builder msgBuilder = DescriptorProto.newBuilder();
        String name = ctx.messageName().getText();
        msgBuilder.setName(name);
        messageHierarchy.push(name);
        messageStack.push(msgBuilder);

        // Reset oneof index for this message
        currentOneofIndex = 0;
        oneofIndexMap.clear();
    }

    @Override
    public void exitMessageDef(
        Protobuf2Parser.MessageDefContext ctx)
    {
        DescriptorProto.Builder msgBuilder = messageStack.pop();
        messageHierarchy.pop();

        if (messageStack.isEmpty())
        {
            // Top-level message
            fileBuilder.addMessageType(msgBuilder.build());
        }
        else
        {
            // Nested message
            messageStack.peek().addNestedType(msgBuilder.build());
        }
    }

    @Override
    public void enterField(
        Protobuf2Parser.FieldContext ctx)
    {
        if (!messageStack.isEmpty())
        {
            FieldDescriptorProto field = processFieldElement(ctx);
            messageStack.peek().addField(field);
        }
    }

    @Override
    public void enterOneof(
        Protobuf2Parser.OneofContext ctx)
    {
        if (!messageStack.isEmpty())
        {
            String oneofName = ctx.oneofName().getText();
            OneofDescriptorProto.Builder oneofBuilder = OneofDescriptorProto.newBuilder();
            oneofBuilder.setName(oneofName);

            oneofStack.push(oneofBuilder);
            oneofIndexMap.put(oneofName, currentOneofIndex);

            messageStack.peek().addOneofDecl(oneofBuilder.build());
            currentOneofIndex++;
        }
    }

    @Override
    public void exitOneof(
        Protobuf2Parser.OneofContext ctx)
    {
        if (!oneofStack.isEmpty())
        {
            oneofStack.pop();
        }
    }

    @Override
    public void enterOneofField(
        Protobuf2Parser.OneofFieldContext ctx)
    {
        if (!messageStack.isEmpty() && !oneofStack.isEmpty())
        {
            FieldDescriptorProto.Builder fieldBuilder = FieldDescriptorProto.newBuilder();
            String type = ctx.type_().getText();
            String name = ctx.fieldName().getText();
            int number = Integer.parseInt(ctx.fieldNumber().getText());

            fieldBuilder.setName(name);
            fieldBuilder.setNumber(number);

            if (TYPES.containsKey(type))
            {
                fieldBuilder.setType(TYPES.get(type));
            }
            else
            {
                fieldBuilder.setTypeName(type);
            }

            // Set oneof index
            String oneofName = oneofStack.peek().getName();
            if (oneofIndexMap.containsKey(oneofName))
            {
                fieldBuilder.setOneofIndex(oneofIndexMap.get(oneofName));
            }

            // Handle field options
            if (ctx.fieldOptions() != null)
            {
                processFieldOptions(fieldBuilder, ctx.fieldOptions());
            }

            messageStack.peek().addField(fieldBuilder.build());
        }
    }

    @Override
    public void enterMapField(
        Protobuf2Parser.MapFieldContext ctx)
    {
        if (!messageStack.isEmpty())
        {
            String keyType = ctx.keyType().getText();
            String valueType = ctx.type_().getText();
            String mapName = ctx.mapName().getText();
            int number = Integer.parseInt(ctx.fieldNumber().getText());

            // Create map entry message
            String entryName = capitalize(mapName) + "Entry";
            DescriptorProto.Builder mapEntry = DescriptorProto.newBuilder();
            mapEntry.setName(entryName);
            mapEntry.getOptionsBuilder().setMapEntry(true);

            // Add key field
            FieldDescriptorProto.Builder keyField = FieldDescriptorProto.newBuilder();
            keyField.setName("key");
            keyField.setNumber(1);
            keyField.setType(TYPES.get(keyType));
            mapEntry.addField(keyField.build());

            // Add value field
            FieldDescriptorProto.Builder valueField = FieldDescriptorProto.newBuilder();
            valueField.setName("value");
            valueField.setNumber(2);
            if (TYPES.containsKey(valueType))
            {
                valueField.setType(TYPES.get(valueType));
            }
            else
            {
                valueField.setTypeName(valueType);
            }
            mapEntry.addField(valueField.build());

            messageStack.peek().addNestedType(mapEntry.build());

            // Create the map field
            FieldDescriptorProto.Builder mapField = FieldDescriptorProto.newBuilder();
            mapField.setName(mapName);
            mapField.setNumber(number);
            mapField.setLabel(Label.LABEL_REPEATED);
            mapField.setTypeName("." + getFullMessageName() + "." + entryName);

            // Handle field options
            if (ctx.fieldOptions() != null)
            {
                processFieldOptions(mapField, ctx.fieldOptions());
            }

            messageStack.peek().addField(mapField.build());
        }
    }

    @Override
    public void enterExtensions(
        Protobuf2Parser.ExtensionsContext ctx)
    {
        if (!messageStack.isEmpty())
        {
            DescriptorProto.Builder msgBuilder = messageStack.peek();

            for (Protobuf2Parser.Range_Context range : ctx.ranges().range_())
            {
                DescriptorProto.ExtensionRange.Builder rangeBuilder =
                        DescriptorProto.ExtensionRange.newBuilder();

                int start = Integer.parseInt(range.intLit(0).getText());
                rangeBuilder.setStart(start);

                if (range.TO() != null)
                {
                    if (range.MAX() != null)
                    {
                        rangeBuilder.setEnd(536870912); // 2^29
                    }
                    else
                    {
                        int end = Integer.parseInt(range.intLit(1).getText());
                        rangeBuilder.setEnd(end + 1); // End is exclusive
                    }
                }
                else
                {
                    rangeBuilder.setEnd(start + 1);
                }

                msgBuilder.addExtensionRange(rangeBuilder.build());
            }
        }
    }

    @Override
    public void enterEnumDef(
        Protobuf2Parser.EnumDefContext ctx)
    {
        EnumDescriptorProto.Builder enumBuilder = EnumDescriptorProto.newBuilder();
        String name = ctx.enumName().getText();
        enumBuilder.setName(name);
        enumStack.push(enumBuilder);
    }

    @Override
    public void exitEnumDef(
        Protobuf2Parser.EnumDefContext ctx)
    {
        if (!enumStack.isEmpty())
        {
            EnumDescriptorProto.Builder enumBuilder = enumStack.pop();

            if (messageStack.isEmpty())
            {
                // Top-level enum
                fileBuilder.addEnumType(enumBuilder.build());
            }
            else
            {
                // Nested enum
                messageStack.peek().addEnumType(enumBuilder.build());
            }
        }
    }

    @Override
    public void enterEnumField(
        Protobuf2Parser.EnumFieldContext ctx)
    {
        if (!enumStack.isEmpty())
        {
            EnumValueDescriptorProto.Builder valueBuilder = EnumValueDescriptorProto.newBuilder();
            String name = ctx.ident().getText();
            int number = Integer.parseInt(ctx.intLit().getText());

            if (ctx.MINUS() != null)
            {
                number = -number;
            }

            valueBuilder.setName(name);
            valueBuilder.setNumber(number);

            // Handle enum value options
            if (ctx.enumValueOptions() != null)
            {
                // Process enum value options if needed
            }

            enumStack.peek().addValue(valueBuilder.build());
        }
    }

    @Override
    public void enterServiceDef(
        Protobuf2Parser.ServiceDefContext ctx)
    {
        ServiceDescriptorProto.Builder serviceBuilder = ServiceDescriptorProto.newBuilder();
        String name = ctx.serviceName().getText();
        serviceBuilder.setName(name);
        serviceStack.push(serviceBuilder);
    }

    @Override
    public void exitServiceDef(
        Protobuf2Parser.ServiceDefContext ctx)
    {
        if (!serviceStack.isEmpty())
        {
            ServiceDescriptorProto.Builder serviceBuilder = serviceStack.pop();
            fileBuilder.addService(serviceBuilder.build());
        }
    }

    public DescriptorProtos.FileDescriptorProto build()
    {
        return fileBuilder.build();
    }

    // Helper methods

    private FieldDescriptorProto processFieldElement(
        Protobuf2Parser.FieldContext ctx)
    {
        FieldDescriptorProto.Builder builder = FieldDescriptorProto.newBuilder();
        processFieldFromContext(builder, ctx);
        return builder.build();
    }

    private void processFieldFromContext(
        FieldDescriptorProto.Builder builder,
        Protobuf2Parser.FieldContext ctx)
    {
        String type = ctx.type_().getText();
        String name = ctx.fieldName().getText();
        String label = ctx.fieldLabel().getText();
        int number = Integer.parseInt(ctx.fieldNumber().getText());

        builder.setName(name);
        builder.setNumber(number);
        builder.setLabel(LABELS.get(label));

        if (TYPES.containsKey(type))
        {
            builder.setType(TYPES.get(type));
        }
        else
        {
            builder.setTypeName(type);
        }

        if (ctx.fieldOptions() != null)
        {
            processFieldOptions(builder, ctx.fieldOptions());
        }
    }

    private void processFieldOptions(
        FieldDescriptorProto.Builder builder,
        Protobuf2Parser.FieldOptionsContext ctx)
    {
        for (Protobuf2Parser.FieldOptionContext option : ctx.fieldOption())
        {
            String optionName = option.optionName().getText();
            if ("default".equals(optionName))
            {
                String defaultValue = option.constant().getText();
                builder.setDefaultValue(stripQuotes(defaultValue));
            }
            // Add handling for other field options as needed
        }
    }

    private void processGroupFromContext(
        FieldDescriptorProto.Builder builder,
        Protobuf2Parser.GroupContext ctx)
    {
        String name = ctx.groupName().getText();
        String label = ctx.fieldLabel().getText();
        int number = Integer.parseInt(ctx.fieldNumber().getText());

        builder.setName(name.toLowerCase());
        builder.setNumber(number);
        builder.setLabel(LABELS.get(label));
        builder.setType(Type.TYPE_GROUP);
        builder.setTypeName(name);
    }

    private String stripQuotes(
        String str)
    {
        if (str.startsWith("\"") && str.endsWith("\"") ||
                str.startsWith("'") && str.endsWith("'"))
        {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    private String capitalize(
        String str)
    {
        if (str == null || str.isEmpty())
        {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private String getFullMessageName()
    {
        StringBuilder fullName = new StringBuilder();
        if (packageName != null && !packageName.isEmpty())
        {
            fullName.append(packageName);
        }
        for (String msgName : messageHierarchy)
        {
            if (fullName.length() > 0)
            {
                fullName.append(".");
            }
            fullName.append(msgName);
        }
        return fullName.toString();
    }
}
