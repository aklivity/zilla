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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

import io.aklivity.zilla.runtime.common.protobuf.ProtobufEnum;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufException;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufField;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufMessage;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufType;
import io.aklivity.zilla.runtime.common.protobuf.internal.parser.Protobuf2BaseListener;
import io.aklivity.zilla.runtime.common.protobuf.internal.parser.Protobuf2Lexer;
import io.aklivity.zilla.runtime.common.protobuf.internal.parser.Protobuf2Parser;
import io.aklivity.zilla.runtime.common.protobuf.internal.parser.Protobuf3BaseListener;
import io.aklivity.zilla.runtime.common.protobuf.internal.parser.Protobuf3Lexer;
import io.aklivity.zilla.runtime.common.protobuf.internal.parser.Protobuf3Parser;

/**
 * Compiles {@code .proto} source text into a {@link ProtobufSchema}, decoded with this library's own
 * ANTLR grammars so there is no third-party protobuf dependency. A single file is parsed (proto2 or
 * proto3, detected from its {@code syntax} statement); composite field type references are resolved
 * by the proto scoping rules (innermost enclosing scope outward) to the dotless full names this model
 * keys on, and {@code map} fields are expanded into the synthetic {@code map_entry} message and a
 * repeated reference to it, matching {@code protoc}.
 */
public final class ProtobufSourceCompiler
{
    private static final Pattern SYNTAX_PATTERN = Pattern.compile(
        "^\\s*syntax\\s*=\\s*[\"'](proto2|proto3)[\"']\\s*;",
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final Map<String, ProtobufType> SCALARS = Map.ofEntries(
        Map.entry("double", ProtobufType.DOUBLE),
        Map.entry("float", ProtobufType.FLOAT),
        Map.entry("int32", ProtobufType.INT32),
        Map.entry("int64", ProtobufType.INT64),
        Map.entry("uint32", ProtobufType.UINT32),
        Map.entry("uint64", ProtobufType.UINT64),
        Map.entry("sint32", ProtobufType.SINT32),
        Map.entry("sint64", ProtobufType.SINT64),
        Map.entry("fixed32", ProtobufType.FIXED32),
        Map.entry("fixed64", ProtobufType.FIXED64),
        Map.entry("sfixed32", ProtobufType.SFIXED32),
        Map.entry("sfixed64", ProtobufType.SFIXED64),
        Map.entry("bool", ProtobufType.BOOL),
        Map.entry("string", ProtobufType.STRING),
        Map.entry("bytes", ProtobufType.BYTES));

    public ProtobufSchema compile(
        CharSequence source)
    {
        String text = source != null ? source.toString() : null;
        if (text == null || text.isEmpty())
        {
            throw new ProtobufException("empty protobuf schema");
        }

        Matcher matcher = SYNTAX_PATTERN.matcher(text);
        boolean proto3 = !matcher.find() || "proto3".equalsIgnoreCase(matcher.group(1));

        CharStream input = CharStreams.fromString(text);
        Draft draft = new Draft(proto3);
        if (proto3)
        {
            Protobuf3Lexer lexer = new Protobuf3Lexer(input);
            Protobuf3Parser parser = new Protobuf3Parser(new CommonTokenStream(lexer));
            parser.setErrorHandler(new BailErrorStrategy());
            new ParseTreeWalker().walk(new Proto3Listener(draft), parser.proto());
        }
        else
        {
            Protobuf2Lexer lexer = new Protobuf2Lexer(input);
            Protobuf2Parser parser = new Protobuf2Parser(new CommonTokenStream(lexer));
            parser.setErrorHandler(new BailErrorStrategy());
            new ParseTreeWalker().walk(new Proto2Listener(draft), parser.proto());
        }

        return link(draft);
    }

    private ProtobufSchema link(
        Draft draft)
    {
        Set<String> names = new LinkedHashSet<>();
        for (DraftMessage message : draft.messages)
        {
            names.add(message.fullName);
        }
        for (DraftEnum enumeration : draft.enums)
        {
            names.add(enumeration.fullName);
        }

        ProtobufSchema.Builder schema = ProtobufSchema.builder();
        for (DraftEnum enumeration : draft.enums)
        {
            ProtobufEnum.Builder builder = ProtobufEnum.builder(enumeration.fullName);
            for (int i = 0; i < enumeration.valueNames.size(); i++)
            {
                builder.value(enumeration.valueNames.get(i), enumeration.valueNumbers.get(i));
            }
            schema.enumeration(builder.build());
        }
        for (DraftMessage message : draft.messages)
        {
            ProtobufMessage.Builder builder = ProtobufMessage.builder(message.fullName).mapEntry(message.mapEntry);
            for (DraftField field : message.fields)
            {
                builder.field(linkField(field, draft.proto3, names));
            }
            schema.message(builder.build());
        }
        return schema.build();
    }

    private ProtobufField linkField(
        DraftField field,
        boolean proto3,
        Set<String> names)
    {
        ProtobufType type = SCALARS.get(field.typeToken);
        String typeName = null;
        if (type == null)
        {
            String resolved = resolveTypeName(field.typeToken, field.scope, names);
            if (resolved == null)
            {
                throw new ProtobufException("unresolved type " + field.typeToken);
            }
            typeName = resolved;
            type = field.enumNames.contains(resolved) ? ProtobufType.ENUM : ProtobufType.MESSAGE;
        }

        ProtobufField.Builder builder = ProtobufField.builder()
            .number(field.number)
            .name(field.name)
            .type(type)
            .repeated(field.repeated)
            .required(field.required)
            .proto3Optional(field.proto3Optional);
        if (typeName != null)
        {
            builder.typeName(typeName);
        }
        if (field.jsonName != null)
        {
            builder.jsonName(field.jsonName);
        }
        if (field.oneofName != null)
        {
            builder.oneof(field.oneofName);
        }
        if (field.defaultValue != null)
        {
            builder.defaultValue(field.defaultValue);
        }
        boolean packed = field.packed != null
            ? field.packed
            : proto3 && field.repeated && type.packable();
        builder.packed(packed);

        return builder.build();
    }

    private static String resolveTypeName(
        String token,
        String scope,
        Set<String> names)
    {
        String result = null;
        if (token.startsWith("."))
        {
            result = token.substring(1);
        }
        else
        {
            String prefix = scope;
            boolean done = false;
            while (!done)
            {
                String candidate = prefix.isEmpty() ? token : prefix + "." + token;
                if (names.contains(candidate))
                {
                    result = candidate;
                    done = true;
                }
                else if (prefix.isEmpty())
                {
                    done = true;
                }
                else
                {
                    int dot = prefix.lastIndexOf('.');
                    prefix = dot < 0 ? "" : prefix.substring(0, dot);
                }
            }
        }
        return result;
    }

    private static String capitalize(
        String name)
    {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String stripQuotes(
        String value)
    {
        String result = value;
        if (value.length() >= 2 &&
            (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"' ||
                value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\''))
        {
            result = value.substring(1, value.length() - 1);
        }
        return result;
    }

    private static final class Draft
    {
        private final boolean proto3;
        private final List<DraftMessage> messages;
        private final List<DraftEnum> enums;
        private final Set<String> enumNames;
        private String packageName;

        private Draft(
            boolean proto3)
        {
            this.proto3 = proto3;
            this.messages = new ArrayList<>();
            this.enums = new ArrayList<>();
            this.enumNames = new LinkedHashSet<>();
            this.packageName = "";
        }
    }

    private static final class DraftMessage
    {
        private final String fullName;
        private final boolean mapEntry;
        private final List<DraftField> fields;

        private DraftMessage(
            String fullName,
            boolean mapEntry)
        {
            this.fullName = fullName;
            this.mapEntry = mapEntry;
            this.fields = new ArrayList<>();
        }
    }

    private static final class DraftEnum
    {
        private final String fullName;
        private final List<String> valueNames;
        private final List<Integer> valueNumbers;

        private DraftEnum(
            String fullName)
        {
            this.fullName = fullName;
            this.valueNames = new ArrayList<>();
            this.valueNumbers = new ArrayList<>();
        }
    }

    private static final class DraftField
    {
        private int number;
        private String name;
        private String typeToken;
        private String scope;
        private boolean repeated;
        private boolean required;
        private boolean proto3Optional;
        private Boolean packed;
        private String jsonName;
        private String oneofName;
        private String defaultValue;
        private Set<String> enumNames;
    }

    private static final class Assembler
    {
        private final Draft draft;
        private final Deque<String> scope;
        private final Deque<DraftMessage> messages;
        private String oneof;

        private Assembler(
            Draft draft)
        {
            this.draft = draft;
            this.scope = new ArrayDeque<>();
            this.messages = new ArrayDeque<>();
        }

        private void setPackage(
            String packageName)
        {
            draft.packageName = packageName;
        }

        private String qualify(
            String name)
        {
            String base = currentScope();
            return base.isEmpty() ? name : base + "." + name;
        }

        private String currentScope()
        {
            StringBuilder builder = new StringBuilder(draft.packageName);
            for (String name : scope)
            {
                if (builder.length() > 0)
                {
                    builder.append('.');
                }
                builder.append(name);
            }
            return builder.toString();
        }

        private void enterMessage(
            String name)
        {
            DraftMessage message = new DraftMessage(qualify(name), false);
            draft.messages.add(message);
            messages.push(message);
            scope.addLast(name);
        }

        private void exitMessage()
        {
            messages.pop();
            scope.removeLast();
        }

        private void addEnum(
            String name,
            List<String> valueNames,
            List<Integer> valueNumbers)
        {
            String fullName = qualify(name);
            draft.enumNames.add(fullName);
            DraftEnum enumeration = new DraftEnum(fullName);
            enumeration.valueNames.addAll(valueNames);
            enumeration.valueNumbers.addAll(valueNumbers);
            draft.enums.add(enumeration);
        }

        private void addField(
            DraftField field)
        {
            field.scope = currentScope();
            field.enumNames = draft.enumNames;
            messages.peek().fields.add(field);
        }

        private void addMap(
            String mapName,
            int number,
            String keyType,
            String valueType)
        {
            String entryName = capitalize(mapName) + "Entry";
            String enclosing = currentScope();
            DraftMessage entry = new DraftMessage(enclosing.isEmpty() ? entryName : enclosing + "." + entryName, true);
            DraftField key = new DraftField();
            key.number = 1;
            key.name = "key";
            key.typeToken = keyType;
            key.scope = entry.fullName;
            key.enumNames = draft.enumNames;
            DraftField value = new DraftField();
            value.number = 2;
            value.name = "value";
            value.typeToken = valueType;
            value.scope = enclosing;
            value.enumNames = draft.enumNames;
            entry.fields.add(key);
            entry.fields.add(value);
            draft.messages.add(entry);

            DraftField field = new DraftField();
            field.number = number;
            field.name = mapName;
            field.typeToken = entryName;
            field.repeated = true;
            addField(field);
        }

        private boolean inMessage()
        {
            return !messages.isEmpty();
        }
    }

    private static final class Proto3Listener extends Protobuf3BaseListener
    {
        private final Assembler helper;

        private Proto3Listener(
            Draft draft)
        {
            this.helper = new Assembler(draft);
        }

        @Override
        public void enterPackageStatement(
            Protobuf3Parser.PackageStatementContext ctx)
        {
            helper.setPackage(ctx.fullIdent().getText());
        }

        @Override
        public void enterMessageDef(
            Protobuf3Parser.MessageDefContext ctx)
        {
            helper.enterMessage(ctx.messageName().getText());
        }

        @Override
        public void exitMessageDef(
            Protobuf3Parser.MessageDefContext ctx)
        {
            helper.exitMessage();
        }

        @Override
        public void enterField(
            Protobuf3Parser.FieldContext ctx)
        {
            if (helper.inMessage())
            {
                String label = ctx.fieldLabel() != null ? ctx.fieldLabel().getText() : null;
                DraftField field = new DraftField();
                field.name = ctx.fieldName().getText();
                field.number = Integer.parseInt(ctx.fieldNumber().getText());
                field.typeToken = ctx.type_().getText();
                field.repeated = "repeated".equals(label);
                field.proto3Optional = "optional".equals(label);
                applyOptions(field, ctx.fieldOptions());
                helper.addField(field);
            }
        }

        @Override
        public void enterOneof(
            Protobuf3Parser.OneofContext ctx)
        {
            helper.oneof = ctx.oneofName().getText();
        }

        @Override
        public void exitOneof(
            Protobuf3Parser.OneofContext ctx)
        {
            helper.oneof = null;
        }

        @Override
        public void enterOneofField(
            Protobuf3Parser.OneofFieldContext ctx)
        {
            if (helper.inMessage())
            {
                DraftField field = new DraftField();
                field.name = ctx.fieldName().getText();
                field.number = Integer.parseInt(ctx.fieldNumber().getText());
                field.typeToken = ctx.type_().getText();
                field.oneofName = helper.oneof;
                applyOptions(field, ctx.fieldOptions());
                helper.addField(field);
            }
        }

        @Override
        public void enterMapField(
            Protobuf3Parser.MapFieldContext ctx)
        {
            if (helper.inMessage())
            {
                helper.addMap(ctx.mapName().getText(), Integer.parseInt(ctx.fieldNumber().getText()),
                    ctx.keyType().getText(), ctx.type_().getText());
            }
        }

        @Override
        public void enterEnumDef(
            Protobuf3Parser.EnumDefContext ctx)
        {
            List<String> valueNames = new ArrayList<>();
            List<Integer> valueNumbers = new ArrayList<>();
            for (Protobuf3Parser.EnumElementContext element : ctx.enumBody().enumElement())
            {
                if (element.enumField() != null)
                {
                    Protobuf3Parser.EnumFieldContext value = element.enumField();
                    int number = Integer.parseInt(value.intLit().getText());
                    if (value.MINUS() != null)
                    {
                        number = -number;
                    }
                    valueNames.add(value.ident().getText());
                    valueNumbers.add(number);
                }
            }
            helper.addEnum(ctx.enumName().getText(), valueNames, valueNumbers);
        }

        private void applyOptions(
            DraftField field,
            Protobuf3Parser.FieldOptionsContext ctx)
        {
            if (ctx != null)
            {
                for (Protobuf3Parser.FieldOptionContext option : ctx.fieldOption())
                {
                    String name = option.optionName().getText();
                    String value = option.constant().getText();
                    if ("packed".equals(name))
                    {
                        field.packed = Boolean.parseBoolean(value);
                    }
                    else if ("json_name".equals(name))
                    {
                        field.jsonName = stripQuotes(value);
                    }
                    else if ("default".equals(name))
                    {
                        field.defaultValue = stripQuotes(value);
                    }
                }
            }
        }
    }

    private static final class Proto2Listener extends Protobuf2BaseListener
    {
        private final Assembler helper;

        private Proto2Listener(
            Draft draft)
        {
            this.helper = new Assembler(draft);
        }

        @Override
        public void enterPackageStatement(
            Protobuf2Parser.PackageStatementContext ctx)
        {
            helper.setPackage(ctx.fullIdent().getText());
        }

        @Override
        public void enterMessageDef(
            Protobuf2Parser.MessageDefContext ctx)
        {
            helper.enterMessage(ctx.messageName().getText());
        }

        @Override
        public void exitMessageDef(
            Protobuf2Parser.MessageDefContext ctx)
        {
            helper.exitMessage();
        }

        @Override
        public void enterField(
            Protobuf2Parser.FieldContext ctx)
        {
            if (helper.inMessage())
            {
                String label = ctx.fieldLabel() != null ? ctx.fieldLabel().getText() : null;
                DraftField field = new DraftField();
                field.name = ctx.fieldName().getText();
                field.number = Integer.parseInt(ctx.fieldNumber().getText());
                field.typeToken = ctx.type_().getText();
                field.repeated = "repeated".equals(label);
                field.required = "required".equals(label);
                applyOptions(field, ctx.fieldOptions());
                helper.addField(field);
            }
        }

        @Override
        public void enterOneof(
            Protobuf2Parser.OneofContext ctx)
        {
            helper.oneof = ctx.oneofName().getText();
        }

        @Override
        public void exitOneof(
            Protobuf2Parser.OneofContext ctx)
        {
            helper.oneof = null;
        }

        @Override
        public void enterOneofField(
            Protobuf2Parser.OneofFieldContext ctx)
        {
            if (helper.inMessage())
            {
                DraftField field = new DraftField();
                field.name = ctx.fieldName().getText();
                field.number = Integer.parseInt(ctx.fieldNumber().getText());
                field.typeToken = ctx.type_().getText();
                field.oneofName = helper.oneof;
                applyOptions(field, ctx.fieldOptions());
                helper.addField(field);
            }
        }

        @Override
        public void enterMapField(
            Protobuf2Parser.MapFieldContext ctx)
        {
            if (helper.inMessage())
            {
                helper.addMap(ctx.mapName().getText(), Integer.parseInt(ctx.fieldNumber().getText()),
                    ctx.keyType().getText(), ctx.type_().getText());
            }
        }

        @Override
        public void enterEnumDef(
            Protobuf2Parser.EnumDefContext ctx)
        {
            List<String> valueNames = new ArrayList<>();
            List<Integer> valueNumbers = new ArrayList<>();
            for (Protobuf2Parser.EnumElementContext element : ctx.enumBody().enumElement())
            {
                if (element.enumField() != null)
                {
                    Protobuf2Parser.EnumFieldContext value = element.enumField();
                    int number = Integer.parseInt(value.intLit().getText());
                    if (value.MINUS() != null)
                    {
                        number = -number;
                    }
                    valueNames.add(value.ident().getText());
                    valueNumbers.add(number);
                }
            }
            helper.addEnum(ctx.enumName().getText(), valueNames, valueNumbers);
        }

        private void applyOptions(
            DraftField field,
            Protobuf2Parser.FieldOptionsContext ctx)
        {
            if (ctx != null)
            {
                for (Protobuf2Parser.FieldOptionContext option : ctx.fieldOption())
                {
                    String name = option.optionName().getText();
                    String value = option.constant().getText();
                    if ("packed".equals(name))
                    {
                        field.packed = Boolean.parseBoolean(value);
                    }
                    else if ("json_name".equals(name))
                    {
                        field.jsonName = stripQuotes(value);
                    }
                    else if ("default".equals(name))
                    {
                        field.defaultValue = stripQuotes(value);
                    }
                }
            }
        }
    }
}
