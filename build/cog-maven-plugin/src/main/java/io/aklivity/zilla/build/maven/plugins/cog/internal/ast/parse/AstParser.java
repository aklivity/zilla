/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.cog.internal.ast.parse;

import static io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstByteOrder.NATIVE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstByteOrder.NETWORK;

import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.antlr.v4.runtime.RuleContext;

import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstByteOrder;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstEnumNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstListMemberNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstListNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstListNode.Builder;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstMapNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstScopeNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstSpecificationNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstStructMemberNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstStructNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstType;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstTypedefNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstUnionCaseNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstUnionNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstValueNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstVariantCaseNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstVariantNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogBaseVisitor;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Array_keywordContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Array_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Case_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.DeclaratorContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Default_nullContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Defined_variant_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Defined_variant_member_with_parametric_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Enum_member_with_defaultContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Enum_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Enum_valueContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Int16_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Int24_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Int32_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Int64_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Int8_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Int_literalContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Int_member_with_defaultContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Integer_array_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.KindContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.List_keywordContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.List_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.List_paramsContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.List_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.List_usingContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Map_keywordContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Map_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.MemberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Member_with_parametric_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Non_primitive_member_with_defaultContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Octets_keywordContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Octets_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.OptionByteOrderContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.ScopeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Scoped_nameContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.SpecificationContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.String16_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.String32_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.String8_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.String_literalContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.String_member_with_defaultContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.String_member_with_null_defaultContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.String_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Struct_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Type_idContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Typedef_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Uint16_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Uint24_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Uint32_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Uint64_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Uint8_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Uint_literalContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Uint_member_with_defaultContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Unbounded_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Unbounded_octets_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Union_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Variant_array_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Variant_case_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Variant_case_member_no_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Variant_case_member_without_ofContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Variant_case_valueContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Variant_int_literalContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Variant_list_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Variant_map_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Variant_octets_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Variant_of_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Variant_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Varint32_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Varint64_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Varint_array_memberContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Varstring_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Varuint32_typeContext;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.Varuint32n_typeContext;


public final class AstParser extends CogBaseVisitor<AstNode>
{
    private final Deque<AstScopeNode.Builder> scopeBuilders;
    private final Deque<String> qualifiedPrefixes;
    private final Map<String, AstType> astTypesByQualifiedName;
    private final Map<AstType, Function<RuleContext, Object>> parserByType;

    private AstSpecificationNode.Builder specificationBuilder;
    private AstStructNode.Builder structBuilder;
    private AstStructMemberNode.Builder memberBuilder;
    private AstUnionNode.Builder unionBuilder;
    private AstUnionCaseNode.Builder caseBuilder;
    private AstByteOrder byteOrder;

    public AstParser()
    {
        this.scopeBuilders = new LinkedList<>();
        this.qualifiedPrefixes = new LinkedList<>();
        this.astTypesByQualifiedName = new HashMap<>();
        this.byteOrder = NATIVE;
        this.parserByType = initParserByType();
    }

    private static Map<AstType, Function<RuleContext, Object>> initParserByType()
    {
        Map<AstType, Function<RuleContext, Object>> valueTypeByName = new HashMap<>();
        valueTypeByName.put(AstType.UINT8, AstParser::parseShort);
        valueTypeByName.put(AstType.UINT16, AstParser::parseInt);
        valueTypeByName.put(AstType.UINT24, AstParser::parseMedium);
        valueTypeByName.put(AstType.UINT32, AstParser::parseLong);
        valueTypeByName.put(AstType.UINT64, AstParser::parseLong);
        valueTypeByName.put(AstType.INT8, AstParser::parseByte);
        valueTypeByName.put(AstType.INT16, AstParser::parseShort);
        valueTypeByName.put(AstType.INT24, AstParser::parseMedium);
        valueTypeByName.put(AstType.INT32, AstParser::parseInt);
        valueTypeByName.put(AstType.INT64, AstParser::parseLong);
        valueTypeByName.put(AstType.STRING8, AstParser::parseString);
        valueTypeByName.put(AstType.STRING16, AstParser::parseString);
        valueTypeByName.put(AstType.STRING32, AstParser::parseString);
        return valueTypeByName;
    }

    @Override
    public AstSpecificationNode visitSpecification(
        SpecificationContext ctx)
    {
        specificationBuilder = new AstSpecificationNode.Builder();

        super.visitSpecification(ctx);

        return specificationBuilder.build();
    }

    @Override
    public AstScopeNode visitScope(
        ScopeContext ctx)
    {
        String name = ctx.ID().getText();

        AstScopeNode.Builder scopeBuilder = new AstScopeNode.Builder();
        scopeBuilder.depth(scopeBuilders.size());
        scopeBuilder.name(name);

        String qualifiedName = name;
        if (!qualifiedPrefixes.isEmpty())
        {
            final String qualifiedPrefix = qualifiedPrefixes.peekFirst();
            qualifiedName = String.format("%s%s", qualifiedPrefix, name);
        }
        qualifiedPrefixes.addFirst(String.format("%s::", qualifiedName));

        AstByteOrder byteOrder = this.byteOrder;
        scopeBuilders.offer(scopeBuilder);
        super.visitScope(ctx);
        scopeBuilders.pollLast();
        qualifiedPrefixes.removeFirst();
        this.byteOrder = byteOrder;

        AstScopeNode.Builder parent = scopeBuilders.peekLast();
        if (parent != null)
        {
            AstScopeNode scopeNode = scopeBuilder.build();
            parent.scope(scopeNode);
            return scopeNode;
        }
        else if (specificationBuilder != null)
        {
            AstScopeNode scopeNode = scopeBuilder.build();
            specificationBuilder.scope(scopeNode);
            return scopeNode;
        }
        else
        {
            return scopeBuilder.build();
        }
    }

    @Override
    public AstNode visitOptionByteOrder(
        OptionByteOrderContext ctx)
    {
        if (ctx.KW_NATIVE() != null)
        {
            byteOrder = NATIVE;
        }
        else if (ctx.KW_NETWORK() != null)
        {
            byteOrder = NETWORK;
        }
        else
        {
            throw new IllegalStateException("Unexpected byte order option");
        }

        return super.visitOptionByteOrder(ctx);
    }

    @Override
    public AstEnumNode visitEnum_type(
        Enum_typeContext ctx)
    {
        AstEnumNode.Builder enumBuilder = new EnumVisitor().visitEnum_type(ctx);
        AstEnumNode enumeration = enumBuilder.build();

        AstScopeNode.Builder scopeBuilder = scopeBuilders.peekLast();
        if (scopeBuilder != null)
        {
            scopeBuilder.enumeration(enumeration);
        }

        return enumeration;
    }

    @Override
    public AstVariantNode visitVariant_type(
        Variant_typeContext ctx)
    {
        AstVariantNode.Builder variantBuilder = new VariantVisitor().visitVariant_type(ctx);
        AstVariantNode variant = variantBuilder.build();

        AstScopeNode.Builder scopeBuilder = scopeBuilders.peekLast();
        if (scopeBuilder != null)
        {
            scopeBuilder.variant(variant);
        }
        return variant;
    }

    @Override
    public AstTypedefNode visitTypedef_type(
        Typedef_typeContext ctx)
    {
        AstTypedefNode.Builder typeDefBuilder = new AstTypedefNode.Builder();

        final String prefix = qualifiedPrefixes.peekFirst();
        final String typedefName = ctx.typedeftype.getText();
        final String originalTypeName = ctx.originaltype.getText();
        final String qualifiedTypedefName = String.format("%s%s", prefix, typedefName);
        astTypesByQualifiedName.put(qualifiedTypedefName, AstType.dynamicType(qualifiedTypedefName));
        typeDefBuilder.name(typedefName);
        AstType originalType = astTypesByQualifiedName.get(originalTypeName);

        if (originalType == null)
        {
            originalType = qualifiedPrefixes.stream()
                .map(qp -> String.format("%s%s", qp, originalTypeName))
                .map(astTypesByQualifiedName::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(AstType.dynamicType(originalTypeName));
        }
        typeDefBuilder.originalType(originalType);

        AstTypedefNode typeDef = typeDefBuilder.build();
        AstScopeNode.Builder scopeBuilder = scopeBuilders.peekLast();
        if (scopeBuilder != null)
        {
            scopeBuilder.typedef(typeDef);
        }
        return typeDef;
    }

    @Override
    public AstNode visitMap_type(
        Map_typeContext ctx)
    {
        AstMapNode.Builder mapBuilder = new AstMapNode.Builder();

        final String prefix = qualifiedPrefixes.peekFirst();
        final String mapName = ctx.ID().getText();
        final String templateTypeName = ctx.templatetype.getText();
        final String keyTypeName = ctx.keytype.getText();
        final String valueTypeName = ctx.valuetype.getText();
        final String qualifiedMapName = String.format("%s%s", prefix, mapName);
        astTypesByQualifiedName.put(qualifiedMapName, AstType.dynamicType(qualifiedMapName));
        mapBuilder.name(mapName);

        AstType templateType = Objects.requireNonNullElse(astTypesByQualifiedName.get(templateTypeName),
            lookUpAstType(templateTypeName));
        AstType keyType = Objects.requireNonNullElse(astTypesByQualifiedName.get(keyTypeName), lookUpAstType(keyTypeName));
        AstType valueType = Objects.requireNonNullElse(astTypesByQualifiedName.get(valueTypeName), lookUpAstType(valueTypeName));

        mapBuilder.templateMapType(templateType);
        mapBuilder.keyType(keyType);
        mapBuilder.valueType(valueType);

        AstMapNode map = mapBuilder.build();
        AstScopeNode.Builder scopeBuilder = scopeBuilders.peekLast();
        if (scopeBuilder != null)
        {
            scopeBuilder.map(map);
        }
        return map;
    }

    @Override
    public AstListNode visitList_type(
        List_typeContext ctx)
    {
        AstListNode.Builder listBuilder = new ListVisitor().visitList_type(ctx);
        AstListNode list = listBuilder.build();

        AstScopeNode.Builder scopeBuilder = scopeBuilders.peekLast();
        if (scopeBuilder != null)
        {
            scopeBuilder.list(list);
        }
        return list;
    }

    @Override
    public AstStructNode visitStruct_type(
        Struct_typeContext ctx)
    {
        structBuilder = new AstStructNode.Builder();
        final String structName = ctx.ID().getText();
        final String prefix = qualifiedPrefixes.peekFirst();
        final String qualifiedName = String.format("%s%s", prefix, structName);
        astTypesByQualifiedName.put(qualifiedName, AstType.dynamicType(qualifiedName));
        structBuilder.name(structName);
        Scoped_nameContext scopedName = ctx.scoped_name();
        if (scopedName != null)
        {
            final String superTypeName = scopedName.getText();
            AstType astTypeName = astTypesByQualifiedName.get(superTypeName);
            if (astTypeName == null)
            {
                Iterator<String> prefixIterator = qualifiedPrefixes.iterator();
                String currentPrefix = qualifiedPrefixes.peekFirst();
                while (prefixIterator.hasNext() &&
                    astTypesByQualifiedName.get(String.format("%s%s", currentPrefix, superTypeName)) == null)
                {
                    currentPrefix = (String) prefixIterator.next();
                }
                structBuilder.supertype(astTypesByQualifiedName.getOrDefault(
                    String.format("%s%s", currentPrefix, superTypeName), AstType.dynamicType(superTypeName)));
            }
            else
            {
                structBuilder.supertype(astTypeName);
            }
        }

        super.visitStruct_type(ctx);

        AstScopeNode.Builder scopeBuilder = scopeBuilders.peekLast();
        if (scopeBuilder != null)
        {
            AstStructNode struct = structBuilder.build();
            scopeBuilder.struct(struct);
            return struct;
        }
        else
        {
            return structBuilder.build();
        }
    }

    @Override
    public AstStructMemberNode visitMember(
        MemberContext ctx)
    {
        memberBuilder = new AstStructMemberNode.Builder();
        memberBuilder.byteOrder(byteOrder);

        super.visitMember(ctx);

        AstStructMemberNode member = memberBuilder.build();
        memberBuilder = null;

        if (caseBuilder != null)
        {
            caseBuilder.member(member);
        }
        else if (structBuilder != null)
        {
            structBuilder.member(member);
        }

        return member;
    }

    @Override
    public AstStructMemberNode visitUnbounded_member(
        Unbounded_memberContext ctx)
    {
        memberBuilder = new AstStructMemberNode.Builder();

        super.visitUnbounded_member(ctx);

        AstStructMemberNode member = memberBuilder.build();
        memberBuilder = null;

        if (caseBuilder != null)
        {
            caseBuilder.member(member);
        }
        else if (structBuilder != null)
        {
            structBuilder.member(member);
        }

        return member;
    }

    @Override
    public AstNode visitUint_member_with_default(
        Uint_member_with_defaultContext ctx)
    {
        memberBuilder.defaultValue(parseInt(ctx.uint_literal()));
        return super.visitUint_member_with_default(ctx);
    }

    @Override
    public AstNode visitInt_member_with_default(
        Int_member_with_defaultContext ctx)
    {
        memberBuilder.defaultValue(parseInt(ctx.int_literal()));
        return super.visitInt_member_with_default(ctx);
    }

    @Override
    public AstNode visitString_member_with_default(
        String_member_with_defaultContext ctx)
    {
        memberBuilder.defaultValue(parseString(ctx.string_literal()));
        return super.visitString_member_with_default(ctx);
    }

    @Override
    public AstNode visitString_member_with_null_default(
        String_member_with_null_defaultContext ctx)
    {
        memberBuilder.defaultToNull();
        return super.visitString_member_with_null_default(ctx);
    }

    @Override
    public AstNode visitEnum_member_with_default(
        Enum_member_with_defaultContext ctx)
    {
        memberBuilder.defaultValue(ctx.ID().getText());
        return super.visitEnum_member_with_default(ctx);
    }

    @Override
    public AstNode visitInteger_array_member(
        Integer_array_memberContext ctx)
    {
        if (ctx.positive_int_const() != null)
        {
            memberBuilder.size(Integer.parseInt(ctx.positive_int_const().getText()));
        }
        else if (ctx.ID() != null)
        {
            memberBuilder.sizeName(ctx.ID().getText());
        }
        return super.visitInteger_array_member(ctx);
    }

    @Override
    public AstNode visitArray_member(
        Array_memberContext ctx)
    {
        memberBuilder.type(AstType.ARRAY32);
        return super.visitArray_member(ctx);
    }

    @Override
    public AstNode visitDefault_null(
        Default_nullContext ctx)
    {
        memberBuilder.defaultToNull();
        return super.visitDefault_null(ctx);
    }

    @Override
    public AstUnionNode visitUnion_type(
        Union_typeContext ctx)
    {
        unionBuilder = new AstUnionNode.Builder();
        final String prefix = qualifiedPrefixes.peekFirst();
        final String unionName = ctx.ID().getText();
        final String qualifiedUnionName = String.format("%s%s", prefix, unionName);
        astTypesByQualifiedName.put(qualifiedUnionName, AstType.dynamicType(qualifiedUnionName));
        unionBuilder.name(unionName);

        Scoped_nameContext kindType = ctx.kindtype;
        if (kindType != null)
        {
            final String kindTypeName = kindType.getText();
            AstType astTypeName = Objects.requireNonNullElse(astTypesByQualifiedName.get(kindTypeName),
                lookUpAstType(kindTypeName));
            unionBuilder.kindType(astTypeName);
        }
        else
        {
            unionBuilder.kindType(AstType.UINT8);
        }

        Scoped_nameContext superType = ctx.supertype;
        if (superType != null)
        {
            final String superTypeName = superType.getText();
            AstType astTypeName = Objects.requireNonNullElse(astTypesByQualifiedName.get(superTypeName),
                lookUpAstType(superTypeName));
            unionBuilder.superType(astTypeName);
        }

        super.visitUnion_type(ctx);

        AstUnionNode union = unionBuilder.build();
        unionBuilder = null;

        AstScopeNode.Builder scopeBuilder = scopeBuilders.peekLast();
        if (scopeBuilder != null)
        {
            scopeBuilder.union(union);
        }

        return union;
    }

    @Override
    public AstUnionCaseNode visitCase_member(
        Case_memberContext ctx)
    {
        caseBuilder = new AstUnionCaseNode.Builder()
            .value(ctx.uint_literal() != null ? Integer.decode(ctx.uint_literal().getText()) : ctx.ID().getText());

        super.visitCase_member(ctx);

        AstUnionCaseNode caseN = caseBuilder.build();
        caseBuilder = null;

        if (unionBuilder != null)
        {
            unionBuilder.caseN(caseN);
        }

        return caseN;
    }

    @Override
    public AstNode visitDeclarator(
        DeclaratorContext ctx)
    {
        memberBuilder.name(ctx.ID().toString());
        return super.visitDeclarator(ctx);
    }

    @Override
    public AstNode visitVarint_array_member(
        Varint_array_memberContext ctx)
    {
        memberBuilder.type(AstType.ARRAY32);
        return super.visitVarint_array_member(ctx);
    }

    @Override
    public AstNode visitVaruint32_type(
        Varuint32_typeContext ctx)
    {
        memberBuilder.type(AstType.VARUINT32);
        return super.visitVaruint32_type(ctx);
    }

    @Override
    public AstNode visitVaruint32n_type(
        Varuint32n_typeContext ctx)
    {
        memberBuilder.type(AstType.VARUINT32N);
        return super.visitVaruint32n_type(ctx);
    }

    @Override
    public AstNode visitVarint32_type(
        Varint32_typeContext ctx)
    {
        memberBuilder.type(AstType.VARINT32);
        return super.visitVarint32_type(ctx);
    }

    @Override
    public AstNode visitVarint64_type(
        Varint64_typeContext ctx)
    {
        memberBuilder.type(AstType.VARINT64);
        return super.visitVarint64_type(ctx);
    }

    @Override
    public AstNode visitInt64_type(
        Int64_typeContext ctx)
    {
        memberBuilder.type(AstType.INT64);
        return super.visitInt64_type(ctx);
    }

    @Override
    public AstNode visitInt32_type(
        Int32_typeContext ctx)
    {
        memberBuilder.type(AstType.INT32);
        return super.visitInt32_type(ctx);
    }

    @Override
    public AstNode visitInt24_type(
        Int24_typeContext ctx)
    {
        memberBuilder.type(AstType.INT24);
        return super.visitInt24_type(ctx);
    }

    @Override
    public AstNode visitInt16_type(
        Int16_typeContext ctx)
    {
        memberBuilder.type(AstType.INT16);
        return super.visitInt16_type(ctx);
    }

    @Override
    public AstNode visitInt8_type(
        Int8_typeContext ctx)
    {
        memberBuilder.type(AstType.INT8);
        return super.visitInt8_type(ctx);
    }

    @Override
    public AstNode visitUint64_type(
        Uint64_typeContext ctx)
    {
        memberBuilder.type(AstType.UINT64);
        return super.visitUint64_type(ctx);
    }

    @Override
    public AstNode visitUint32_type(
        Uint32_typeContext ctx)
    {
        memberBuilder.type(AstType.UINT32);
        return super.visitUint32_type(ctx);
    }

    @Override
    public AstNode visitUint16_type(
        Uint16_typeContext ctx)
    {
        memberBuilder.type(AstType.UINT16);
        return super.visitUint16_type(ctx);
    }

    @Override
    public AstNode visitUint24_type(
        Uint24_typeContext ctx)
    {
        memberBuilder.type(AstType.UINT24);
        return super.visitUint24_type(ctx);
    }

    @Override
    public AstNode visitUint8_type(
        Uint8_typeContext ctx)
    {
        memberBuilder.type(AstType.UINT8);
        return super.visitUint8_type(ctx);
    }

    @Override
    public AstNode visitString8_type(
        String8_typeContext ctx)
    {
        memberBuilder.type(AstType.STRING8);
        return super.visitString8_type(ctx);
    }

    @Override
    public AstNode visitString16_type(
            String16_typeContext ctx)
    {
        memberBuilder.type(AstType.STRING16);
        return super.visitString16_type(ctx);
    }

    @Override
    public AstNode visitString32_type(
        String32_typeContext ctx)
    {
        memberBuilder.type(AstType.STRING32);
        return super.visitString32_type(ctx);
    }

    @Override
    public AstNode visitVarstring_type(
        Varstring_typeContext ctx)
    {
        memberBuilder.type(AstType.VARSTRING);
        return super.visitVarstring_type(ctx);
    }

    @Override
    public AstNode visitOctets_type(
        Octets_typeContext ctx)
    {
        memberBuilder.type(AstType.OCTETS);
        if (ctx.positive_int_const() != null)
        {
            memberBuilder.size(Integer.parseInt(ctx.positive_int_const().getText()));
        }
        else if (ctx.ID() != null)
        {
            memberBuilder.sizeName(ctx.ID().getText());
        }
        return super.visitOctets_type(ctx);
    }

    @Override
    public AstNode visitUnbounded_octets_type(
        Unbounded_octets_typeContext ctx)
    {
        memberBuilder.type(AstType.OCTETS);
        return super.visitUnbounded_octets_type(ctx);
    }

    @Override
    public AstNode visitScoped_name(
        Scoped_nameContext ctx)
    {
        String typeName = ctx.getText();
        if (memberBuilder != null)
        {
            AstType astTypeName = astTypesByQualifiedName.get(typeName);
            if (astTypeName == null)
            {
                astTypeName = qualifiedPrefixes.stream()
                    .map(qp -> String.format("%s%s", qp, typeName))
                    .map(astTypesByQualifiedName::get)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(AstType.dynamicType(typeName));
            }
            memberBuilder.type(astTypeName);
        }
        return super.visitScoped_name(ctx);
    }

    @Override
    public AstNode visitType_id(
        Type_idContext ctx)
    {
        if (structBuilder != null)
        {
            structBuilder.typeId(parseInt(ctx.uint_literal()));
        }
        return super.visitType_id(ctx);
    }

    private static byte parseByte(
        RuleContext ctx)
    {
        return parseByte(ctx.getText());
    }

    private static byte parseByte(
        String text)
    {
        return Byte.decode(text);
    }

    private static short parseShort(
        RuleContext ctx)
    {
        return parseShort(ctx.getText());
    }

    private static short parseShort(
        String text)
    {
        return Short.decode(text);
    }

    private static int parseMedium(
        RuleContext ctx)
    {
        final String text = ctx.getText();
        final int value = parseInt(text);
        if ((value & 0x7f_00_00_00) != 0)
        {
            throw new NumberFormatException(String.format("Value out of range for medium int (%s)", text));
        }
        return ctx != null ? value : 0;
    }

    private static int parseInt(
        Int_literalContext ctx)
    {
        return ctx != null ? parseInt(ctx.getText()) : 0;
    }

    private static int parseInt(
        RuleContext ctx)
    {
        return ctx != null ? parseInt(ctx.getText()) : 0;
    }

    private static int parseInt(
        String text)
    {
        return Integer.decode(text);
    }

    private static long parseLong(
        RuleContext ctx)
    {
        return parseLong(ctx.getText());
    }

    private static long parseLong(
        String text)
    {
        return Long.decode(text.substring(0, text.length() - 1));
    }

    private static String parseString(
        RuleContext ctx)
    {
        return ctx.getText();
    }

    public final class ListVisitor extends CogBaseVisitor<AstListNode.Builder>
    {
        private final AstListNode.Builder listBuilder;
        private AstListMemberNode.Builder listMemberBuilder;

        public ListVisitor()
        {
            this.listBuilder = new AstListNode.Builder();
        }

        @Override
        public AstListNode.Builder visitList_type(
            List_typeContext ctx)
        {
            final String prefix = qualifiedPrefixes.peekFirst();
            final String listName = ctx.ID().getText();
            final String qualifiedListName = String.format("%s%s", prefix, listName);
            astTypesByQualifiedName.put(qualifiedListName, AstType.dynamicType(qualifiedListName));
            listBuilder.name(listName);
            listBuilder.byteOrder(byteOrder);
            return super.visitList_type(ctx);
        }

        @Override
        public AstListNode.Builder visitList_params(
            List_paramsContext ctx)
        {
            new ListParamsVisitor(listBuilder).visitList_params(ctx);
            return listBuilder;
        }

        @Override
        public Builder visitList_using(
            List_usingContext ctx)
        {
            new ListParamsVisitor(listBuilder).visitList_using(ctx);
            return listBuilder;
        }

        @Override
        public Builder visitList_member(
            List_memberContext ctx)
        {
            listMemberBuilder = new AstListMemberNode.Builder();
            listMemberBuilder.byteOrder(byteOrder);
            if (ctx.KW_REQUIRED() != null)
            {
                listMemberBuilder.isRequired(true);
            }

            super.visitList_member(ctx);

            AstListMemberNode member = listMemberBuilder.build();
            listBuilder.member(member);
            listMemberBuilder = null;
            return listBuilder;
        }

        @Override
        public AstListNode.Builder visitDeclarator(
            DeclaratorContext ctx)
        {
            listMemberBuilder.name(ctx.ID().toString());
            return super.visitDeclarator(ctx);
        }

        @Override
        public AstListNode.Builder visitUint_member_with_default(
            Uint_member_with_defaultContext ctx)
        {
            listMemberBuilder.defaultValue(parseInt(ctx.uint_literal()));
            return super.visitUint_member_with_default(ctx);
        }

        @Override
        public AstListNode.Builder visitInt_member_with_default(
            Int_member_with_defaultContext ctx)
        {
            listMemberBuilder.defaultValue(parseInt(ctx.int_literal()));
            return super.visitInt_member_with_default(ctx);
        }

        @Override
        public AstListNode.Builder visitNon_primitive_member_with_default(
            Non_primitive_member_with_defaultContext ctx)
        {
            if (ctx.ID() != null)
            {
                listMemberBuilder.defaultValue(ctx.ID().getText());
            }
            else
            {
                listMemberBuilder.defaultValue(ctx.int_literal().getText());
            }
            return super.visitNon_primitive_member_with_default(ctx);
        }

        @Override
        public AstListNode.Builder visitArray_member(
            Array_memberContext ctx)
        {
            listMemberBuilder.type(AstType.ARRAY32);
            return super.visitArray_member(ctx);
        }

        @Override
        public Builder visitMember_with_parametric_type(
            Member_with_parametric_typeContext ctx)
        {
            listMemberBuilder.name(ctx.name.getText());
            String typeName = ctx.membertype.getText();
            AstType astTypeName = Objects.requireNonNullElse(astTypesByQualifiedName.get(typeName), lookUpAstType(typeName));
            listMemberBuilder.type(astTypeName);

            String typeParam = ctx.param1.getText();
            AstType astTypeParam = Objects.requireNonNullElse(astTypesByQualifiedName.get(typeParam),
                lookUpAstType(typeParam));
            listMemberBuilder.typeParam(astTypeParam);

            if (ctx.param2 != null)
            {
                String secondTypeParam = ctx.param2.getText();
                listMemberBuilder.typeParam(Objects.requireNonNullElse(astTypesByQualifiedName.get(secondTypeParam),
                    lookUpAstType(secondTypeParam)));
            }
            return super.visitMember_with_parametric_type(ctx);
        }

        @Override
        public AstListNode.Builder visitVarint_array_member(
            Varint_array_memberContext ctx)
        {
            listMemberBuilder.type(AstType.ARRAY32);
            return super.visitVarint_array_member(ctx);
        }

        @Override
        public AstListNode.Builder visitVarint32_type(
            Varint32_typeContext ctx)
        {
            listMemberBuilder.type(AstType.VARINT32);
            return super.visitVarint32_type(ctx);
        }

        @Override
        public AstListNode.Builder visitVarint64_type(
            Varint64_typeContext ctx)
        {
            listMemberBuilder.type(AstType.VARINT64);
            return super.visitVarint64_type(ctx);
        }

        @Override
        public AstListNode.Builder visitInt64_type(
            Int64_typeContext ctx)
        {
            listMemberBuilder.type(AstType.INT64);
            return super.visitInt64_type(ctx);
        }

        @Override
        public AstListNode.Builder visitInt32_type(
            Int32_typeContext ctx)
        {
            listMemberBuilder.type(AstType.INT32);
            return super.visitInt32_type(ctx);
        }

        @Override
        public AstListNode.Builder visitInt16_type(
            Int16_typeContext ctx)
        {
            listMemberBuilder.type(AstType.INT16);
            return super.visitInt16_type(ctx);
        }

        @Override
        public AstListNode.Builder visitInt8_type(
            Int8_typeContext ctx)
        {
            listMemberBuilder.type(AstType.INT8);
            return super.visitInt8_type(ctx);
        }

        @Override
        public AstListNode.Builder visitUint64_type(
            Uint64_typeContext ctx)
        {
            listMemberBuilder.type(AstType.UINT64);
            return super.visitUint64_type(ctx);
        }

        @Override
        public AstListNode.Builder visitUint32_type(
            Uint32_typeContext ctx)
        {
            listMemberBuilder.type(AstType.UINT32);
            return super.visitUint32_type(ctx);
        }

        @Override
        public AstListNode.Builder visitUint16_type(
            Uint16_typeContext ctx)
        {
            listMemberBuilder.type(AstType.UINT16);
            return super.visitUint16_type(ctx);
        }

        @Override
        public AstListNode.Builder visitUint8_type(
            Uint8_typeContext ctx)
        {
            listMemberBuilder.type(AstType.UINT8);
            return super.visitUint8_type(ctx);
        }

        @Override
        public Builder visitString8_type(
            String8_typeContext ctx)
        {
            listMemberBuilder.type(AstType.STRING8);
            return super.visitString8_type(ctx);
        }

        @Override
        public AstListNode.Builder visitString16_type(
            String16_typeContext ctx)
        {
            listMemberBuilder.type(AstType.STRING16);
            return super.visitString16_type(ctx);
        }

        @Override
        public AstListNode.Builder visitString32_type(
            String32_typeContext ctx)
        {
            listMemberBuilder.type(AstType.STRING32);
            return super.visitString32_type(ctx);
        }

        @Override
        public Builder visitVarstring_type(
            Varstring_typeContext ctx)
        {
            listMemberBuilder.type(AstType.VARSTRING);
            return super.visitVarstring_type(ctx);
        }

        @Override
        public AstListNode.Builder visitUnbounded_octets_type(
            Unbounded_octets_typeContext ctx)
        {
            listMemberBuilder.type(AstType.OCTETS);
            return super.visitUnbounded_octets_type(ctx);
        }

        @Override
        public AstListNode.Builder visitScoped_name(
            Scoped_nameContext ctx)
        {
            String typeName = ctx.getText();
            AstType astTypeName = astTypesByQualifiedName.get(typeName);
            if (astTypeName == null)
            {
                astTypeName = qualifiedPrefixes.stream()
                    .map(qp -> String.format("%s%s", qp, typeName))
                    .map(astTypesByQualifiedName::get)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(AstType.dynamicType(typeName));
            }
            listMemberBuilder.type(astTypeName);
            return super.visitScoped_name(ctx);
        }

        public final class ListParamsVisitor extends CogBaseVisitor<AstListNode.Builder>
        {
            private final AstListNode.Builder listBuilder;

            public ListParamsVisitor(
                AstListNode.Builder listBuilder)
            {
                this.listBuilder = listBuilder;
            }

            @Override
            public Builder visitUint8_type(
                Uint8_typeContext ctx)
            {
                listBuilder.lengthType(AstType.UINT8);
                listBuilder.fieldCountType(AstType.UINT8);
                return super.visitUint8_type(ctx);
            }

            @Override
            public Builder visitUint16_type(
                Uint16_typeContext ctx)
            {
                listBuilder.lengthType(AstType.UINT16);
                listBuilder.fieldCountType(AstType.UINT16);
                return super.visitUint16_type(ctx);
            }

            @Override
            public Builder visitUint32_type(
                Uint32_typeContext ctx)
            {
                listBuilder.lengthType(AstType.UINT32);
                listBuilder.fieldCountType(AstType.UINT32);
                return super.visitUint32_type(ctx);
            }

            @Override
            public Builder visitUint64_type(
                Uint64_typeContext ctx)
            {
                listBuilder.lengthType(AstType.UINT64);
                listBuilder.fieldCountType(AstType.UINT64);
                return super.visitUint64_type(ctx);
            }

            @Override
            public Builder visitUint_literal(
                Uint_literalContext ctx)
            {
                Byte missingFieldByte = parseByte(ctx);
                listBuilder.missingFieldByte(missingFieldByte);
                return super.visitUint_literal(ctx);
            }

            @Override
            public Builder visitDeclarator(
                DeclaratorContext ctx)
            {
                String superTypeName = ctx.ID().getText();
                AstType astTypeName = astTypesByQualifiedName.get(superTypeName);
                if (astTypeName == null)
                {
                    astTypeName = qualifiedPrefixes.stream()
                        .map(qp -> String.format("%s%s", qp, superTypeName))
                        .map(astTypesByQualifiedName::get)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(AstType.dynamicType(superTypeName));
                }
                listBuilder.templateType(astTypeName);
                return super.visitDeclarator(ctx);
            }
        }

        @Override
        protected AstListNode.Builder defaultResult()
        {
            return listBuilder;
        }
    }

    public final class VariantVisitor extends CogBaseVisitor<AstVariantNode.Builder>
    {
        private final AstVariantNode.Builder variantBuilder;

        public VariantVisitor()
        {
            this.variantBuilder = new AstVariantNode.Builder();
            this.variantBuilder.byteOrder(byteOrder);
        }

        @Override
        public AstVariantNode.Builder visitVariant_type(
            Variant_typeContext ctx)
        {
            final String prefix = qualifiedPrefixes.peekFirst();
            final String variantName = ctx.ID().getText();
            final String qualifiedVariantName = String.format("%s%s", prefix, variantName);
            astTypesByQualifiedName.put(qualifiedVariantName, AstType.dynamicType(qualifiedVariantName));
            variantBuilder.name(variantName);
            return super.visitVariant_type(ctx);
        }

        @Override
        public AstVariantNode.Builder visitKind(
            KindContext ctx)
        {
            VariantKindVisitor variantKindVisitor = new VariantKindVisitor(variantBuilder);
            return variantKindVisitor.visitKind(ctx);
        }

        @Override
        public AstVariantNode.Builder visitVariant_of_type(
            Variant_of_typeContext ctx)
        {
            return super.visitVariant_of_type(ctx);
        }

        @Override
        public AstVariantNode.Builder visitVariant_case_member(
            Variant_case_memberContext ctx)
        {
            AstVariantCaseNode.Builder variantCaseNodeBuilder = new VariantCaseVisitor().visitVariant_case_member(ctx);

            AstVariantCaseNode caseN = variantCaseNodeBuilder.build();
            variantBuilder.caseN(caseN);

            return variantBuilder;
        }

        @Override
        public AstVariantNode.Builder visitVariant_case_member_no_type(
            Variant_case_member_no_typeContext ctx)
        {
            AstVariantCaseNode.Builder variantCaseNodeBuilder = new VariantCaseVisitor().visitVariant_case_member_no_type(ctx);

            AstVariantCaseNode caseN = variantCaseNodeBuilder.build();
            variantBuilder.caseN(caseN);

            return variantBuilder;
        }

        @Override
        public AstVariantNode.Builder visitVariant_case_member_without_of(
            Variant_case_member_without_ofContext ctx)
        {
            AstVariantCaseNode.Builder variantCaseNodeBuilder = new VariantCaseVisitor().visitVariant_case_member_without_of(ctx);

            AstVariantCaseNode caseN = variantCaseNodeBuilder.build();
            variantBuilder.caseN(caseN);

            return variantBuilder;
        }

        public final class VariantKindVisitor extends CogBaseVisitor<AstVariantNode.Builder>
        {
            private AstVariantNode.Builder variantBuilder;

            public VariantKindVisitor(
                AstVariantNode.Builder variantBuilder)
            {
                this.variantBuilder = variantBuilder;
            }

            @Override
            public AstVariantNode.Builder visitKind(
                KindContext ctx)
            {
                if (ctx.KW_UINT8() != null)
                {
                    variantBuilder.kindType(AstType.UINT8);
                }
                return super.visitKind(ctx);
            }

            @Override
            public AstVariantNode.Builder visitScoped_name(
                Scoped_nameContext ctx)
            {
                String kindTypeName = ctx.getText();
                AstType astTypeName = astTypesByQualifiedName.get(kindTypeName);
                if (astTypeName == null)
                {
                    astTypeName = qualifiedPrefixes.stream()
                        .map(qp -> String.format("%s%s", qp, kindTypeName))
                        .map(astTypesByQualifiedName::get)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(AstType.dynamicType(kindTypeName));
                }
                variantBuilder.kindType(astTypeName);
                return super.visitScoped_name(ctx);
            }
        }

        public final class VariantCaseVisitor extends CogBaseVisitor<AstVariantCaseNode.Builder>
        {
            private final AstVariantCaseNode.Builder variantCaseBuilder;

            public VariantCaseVisitor()
            {
                variantCaseBuilder = new AstVariantCaseNode.Builder();
            }

            @Override
            public AstVariantCaseNode.Builder visitVariant_case_member(
                Variant_case_memberContext ctx)
            {
                super.visitVariant_case_member(ctx);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitVariant_case_member_no_type(
                Variant_case_member_no_typeContext ctx)
            {
                super.visitVariant_case_member_no_type(ctx);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitVariant_case_member_without_of(
                Variant_case_member_without_ofContext ctx)
            {
                super.visitVariant_case_member_without_of(ctx);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitDefined_variant_member(
                Defined_variant_memberContext ctx)
            {
                String typeName = ctx.declarator().getText();
                AstType astTypeName = Objects.requireNonNullElse(astTypesByQualifiedName.get(typeName), lookUpAstType(typeName));
                variantCaseBuilder.type(astTypeName);
                return super.visitDefined_variant_member(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitDefined_variant_member_with_parametric_type(
                Defined_variant_member_with_parametric_typeContext ctx)
            {
                String typeName = ctx.membertype.getText();
                AstType astTypeName = Objects.requireNonNullElse(astTypesByQualifiedName.get(typeName), lookUpAstType(typeName));
                variantCaseBuilder.type(astTypeName);

                String typeParam = ctx.param1.getText();
                AstType astTypeParam = Objects.requireNonNullElse(astTypesByQualifiedName.get(typeParam),
                    lookUpAstType(typeParam));
                variantCaseBuilder.typeParam(astTypeParam);

                if (ctx.param2 != null)
                {
                    String secondTypeParam = ctx.param2.getText();
                    variantCaseBuilder.typeParam(Objects.requireNonNullElse(astTypesByQualifiedName.get(secondTypeParam),
                        lookUpAstType(secondTypeParam)));
                }
                return super.visitDefined_variant_member_with_parametric_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitVariant_case_value(
                Variant_case_valueContext ctx)
            {
                variantCaseBuilder.value(new VariantCaseValueVisitor().visitVariant_case_value(ctx));
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitInt8_type(
                Int8_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.INT8);
                return super.visitInt8_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitInt16_type(
                Int16_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.INT16);
                return super.visitInt16_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitInt24_type(
                Int24_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.INT24);
                return super.visitInt24_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitInt32_type(
                Int32_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.INT32);
                return super.visitInt32_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitInt64_type(
                Int64_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.INT64);
                return super.visitInt64_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitUint8_type(
                Uint8_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.UINT8);
                return super.visitUint8_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitUint16_type(
                Uint16_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.UINT16);
                return super.visitUint16_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitUint24_type(
                Uint24_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.UINT24);
                return super.visitUint24_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitUint32_type(
                Uint32_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.UINT32);
                return super.visitUint32_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitUint64_type(
                Uint64_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.UINT64);
                return super.visitUint64_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitString8_type(
                String8_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.STRING8);
                return super.visitString8_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitString16_type(
                String16_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.STRING16);
                return super.visitString16_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitString32_type(
                String32_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.STRING32);
                return super.visitString32_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitVarstring_type(
                Varstring_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.VARSTRING);
                return super.visitVarstring_type(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitVariant_int_literal(
                Variant_int_literalContext ctx)
            {
                variantCaseBuilder.type(AstType.dynamicType(ctx.getText()));
                return super.visitVariant_int_literal(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitVariant_list_member(
                Variant_list_memberContext ctx)
            {
                new VariantListMemberVisitor(variantCaseBuilder).visitVariant_list_member(ctx);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitVariant_array_member(
                Variant_array_memberContext ctx)
            {
                new VariantArrayMemberVisitor(variantCaseBuilder).visitVariant_array_member(ctx);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitVariant_map_member(
                Variant_map_memberContext ctx)
            {
                new MapMemberVisitor(variantCaseBuilder).visitVariant_map_member(ctx);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitVariant_octets_member(
                Variant_octets_memberContext ctx)
            {
                new OctetsMemberVisitor(variantCaseBuilder).visitVariant_octets_member(ctx);
                return variantCaseBuilder;
            }
        }

        public final class VariantCaseValueVisitor extends CogBaseVisitor<Object>
        {
            @Override
            public Object visitUint_literal(
                Uint_literalContext ctx)
            {
                return Integer.decode(ctx.getText());
            }

            @Override
            public Object visitDeclarator(
                DeclaratorContext ctx)
            {
                return ctx.ID().getText();
            }
        }

        public final class VariantListMemberVisitor extends CogBaseVisitor<AstVariantCaseNode.Builder>
        {
            private final AstVariantCaseNode.Builder variantCaseBuilder;

            public VariantListMemberVisitor(
                AstVariantCaseNode.Builder variantCaseBuilder)
            {
                this.variantCaseBuilder = variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitVariant_list_member(
                Variant_list_memberContext ctx)
            {
                if (ctx.UNSIGNED_INTEGER_LITERAL(0) != null)
                {
                    variantCaseBuilder.type(AstType.LIST0);
                    return variantCaseBuilder;
                }
                if (ctx.uint_literal() != null)
                {
                    variantCaseBuilder.missingFieldValue(parseInt(ctx.uint_literal()));
                }
                return super.visitVariant_list_member(ctx);
            }

            @Override
            public AstVariantCaseNode.Builder visitUint32_type(
                Uint32_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.LIST32);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitUint8_type(
                Uint8_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.LIST8);
                return variantCaseBuilder;
            }
        }

        public final class VariantArrayMemberVisitor extends CogBaseVisitor<AstVariantCaseNode.Builder>
        {
            private final AstVariantCaseNode.Builder variantCaseBuilder;

            public VariantArrayMemberVisitor(
                AstVariantCaseNode.Builder variantCaseBuilder)
            {
                this.variantCaseBuilder = variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitUint32_type(
                Uint32_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.ARRAY32);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitUint16_type(
                Uint16_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.ARRAY16);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitUint8_type(
                Uint8_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.ARRAY8);
                return variantCaseBuilder;
            }
        }

        public final class MapMemberVisitor extends CogBaseVisitor<AstVariantCaseNode.Builder>
        {
            private final AstVariantCaseNode.Builder variantCaseBuilder;

            public MapMemberVisitor(
                AstVariantCaseNode.Builder variantCaseBuilder)
            {
                this.variantCaseBuilder = variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitUint32_type(
                Uint32_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.MAP32);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitUint16_type(
                Uint16_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.MAP16);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitUint8_type(
                Uint8_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.MAP8);
                return variantCaseBuilder;
            }
        }

        public final class OctetsMemberVisitor extends CogBaseVisitor<AstVariantCaseNode.Builder>
        {
            private final AstVariantCaseNode.Builder variantCaseBuilder;

            public OctetsMemberVisitor(
                AstVariantCaseNode.Builder variantCaseBuilder)
            {
                this.variantCaseBuilder = variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitUint32_type(
                Uint32_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.BOUNDED_OCTETS32);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitUint16_type(
                Uint16_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.BOUNDED_OCTETS16);
                return variantCaseBuilder;
            }

            @Override
            public AstVariantCaseNode.Builder visitUint8_type(
                Uint8_typeContext ctx)
            {
                variantCaseBuilder.type(AstType.BOUNDED_OCTETS8);
                return variantCaseBuilder;
            }
        }

        @Override
        public AstVariantNode.Builder visitString_type(
            String_typeContext ctx)
        {
            variantBuilder.of(AstType.STRING);
            return super.visitString_type(ctx);
        }

        @Override
        public AstVariantNode.Builder visitList_keyword(
            List_keywordContext ctx)
        {
            variantBuilder.of(AstType.LIST);
            return super.visitList_keyword(ctx);
        }

        @Override
        public AstVariantNode.Builder visitArray_keyword(
            Array_keywordContext ctx)
        {
            variantBuilder.of(AstType.ARRAY);
            return super.visitArray_keyword(ctx);
        }

        @Override
        public AstVariantNode.Builder visitMap_keyword(
            Map_keywordContext ctx)
        {
            variantBuilder.of(AstType.MAP);
            return super.visitMap_keyword(ctx);
        }

        @Override
        public AstVariantNode.Builder visitOctets_keyword(
            Octets_keywordContext ctx)
        {
            variantBuilder.of(AstType.BOUNDED_OCTETS);
            return super.visitOctets_keyword(ctx);
        }

        @Override
        public AstVariantNode.Builder visitInt64_type(
            Int64_typeContext ctx)
        {
            variantBuilder.of(AstType.INT64);
            return super.visitInt64_type(ctx);
        }

        @Override
        public AstVariantNode.Builder visitInt32_type(
            Int32_typeContext ctx)
        {
            variantBuilder.of(AstType.INT32);
            return super.visitInt32_type(ctx);
        }

        @Override
        public AstVariantNode.Builder visitInt16_type(
            Int16_typeContext ctx)
        {
            variantBuilder.of(AstType.INT16);
            return super.visitInt16_type(ctx);
        }

        @Override
        public AstVariantNode.Builder visitInt8_type(
            Int8_typeContext ctx)
        {
            variantBuilder.of(AstType.INT8);
            return super.visitInt8_type(ctx);
        }

        @Override
        public AstVariantNode.Builder visitUint64_type(
            Uint64_typeContext ctx)
        {
            variantBuilder.of(AstType.UINT64);
            return super.visitUint64_type(ctx);
        }

        @Override
        public AstVariantNode.Builder visitUint32_type(
            Uint32_typeContext ctx)
        {
            variantBuilder.of(AstType.UINT32);
            return super.visitUint32_type(ctx);
        }

        @Override
        public AstVariantNode.Builder visitUint24_type(
            Uint24_typeContext ctx)
        {
            variantBuilder.of(AstType.UINT24);
            return super.visitUint24_type(ctx);
        }

        @Override
        public AstVariantNode.Builder visitUint16_type(
            Uint16_typeContext ctx)
        {
            variantBuilder.of(AstType.UINT16);
            return super.visitUint16_type(ctx);
        }

        @Override
        public AstVariantNode.Builder visitUint8_type(
            Uint8_typeContext ctx)
        {
            variantBuilder.of(AstType.UINT8);
            return super.visitUint8_type(ctx);
        }

        @Override
        protected AstVariantNode.Builder defaultResult()
        {
            return variantBuilder;
        }
    }

    public final class EnumVisitor extends CogBaseVisitor<AstEnumNode.Builder>
    {
        private final AstEnumNode.Builder enumBuilder;
        private AstValueNode.Builder valueBuilder;

        public EnumVisitor()
        {
            this.enumBuilder = new AstEnumNode.Builder();
        }

        @Override
        public AstEnumNode.Builder visitEnum_type(
            Enum_typeContext ctx)
        {
            final String prefix = qualifiedPrefixes.peekFirst();
            final String enumName = ctx.ID().getText();
            final String qualifiedEnumName = String.format("%s%s", prefix, enumName);
            astTypesByQualifiedName.put(qualifiedEnumName, AstType.dynamicType(qualifiedEnumName));
            enumBuilder.name(enumName);

            return super.visitEnum_type(ctx);
        }

        @Override
        protected AstEnumNode.Builder defaultResult()
        {
            return enumBuilder;
        }

        @Override
        public AstEnumNode.Builder visitInt8_type(
            Int8_typeContext ctx)
        {
            enumBuilder.valueType(AstType.INT8);
            return super.visitInt8_type(ctx);
        }

        @Override
        public AstEnumNode.Builder visitInt16_type(
            Int16_typeContext ctx)
        {
            enumBuilder.valueType(AstType.INT16);
            return super.visitInt16_type(ctx);
        }

        @Override
        public AstEnumNode.Builder visitInt32_type(
            Int32_typeContext ctx)
        {
            enumBuilder.valueType(AstType.INT32);
            return super.visitInt32_type(ctx);
        }

        @Override
        public AstEnumNode.Builder visitInt64_type(
            Int64_typeContext ctx)
        {
            enumBuilder.valueType(AstType.INT64);
            return super.visitInt64_type(ctx);
        }

        @Override
        public AstEnumNode.Builder visitUint8_type(
            Uint8_typeContext ctx)
        {
            enumBuilder.valueType(AstType.UINT8);
            return super.visitUint8_type(ctx);
        }

        @Override
        public AstEnumNode.Builder visitUint16_type(
            Uint16_typeContext ctx)
        {
            enumBuilder.valueType(AstType.UINT16);
            return super.visitUint16_type(ctx);
        }

        @Override
        public AstEnumNode.Builder visitUint32_type(
            Uint32_typeContext ctx)
        {
            enumBuilder.valueType(AstType.UINT32);
            return super.visitUint32_type(ctx);
        }

        @Override
        public AstEnumNode.Builder visitUint64_type(
            Uint64_typeContext ctx)
        {
            enumBuilder.valueType(AstType.UINT64);
            return super.visitUint64_type(ctx);
        }

        @Override
        public AstEnumNode.Builder visitString8_type(
            String8_typeContext ctx)
        {
            enumBuilder.valueType(AstType.STRING8);
            return super.visitString8_type(ctx);
        }

        @Override
        public AstEnumNode.Builder visitString16_type(
            String16_typeContext ctx)
        {
            enumBuilder.valueType(AstType.STRING16);
            return super.visitString16_type(ctx);
        }

        @Override
        public AstEnumNode.Builder visitString32_type(
            String32_typeContext ctx)
        {
            enumBuilder.valueType(AstType.STRING32);
            return super.visitString32_type(ctx);
        }

        @Override
        public AstEnumNode.Builder visitDeclarator(
            DeclaratorContext ctx)
        {
            String enumType = ctx.ID().getText();
            AstType astEnumType = Objects.requireNonNullElse(astTypesByQualifiedName.get(enumType), lookUpAstType(enumType));
            enumBuilder.valueType(astEnumType);
            return super.visitDeclarator(ctx);
        }

        @Override
        public AstEnumNode.Builder visitEnum_value(
            Enum_valueContext ctx)
        {
            this.valueBuilder = new AstValueNode.Builder()
                .name(ctx.ID().getText())
                .ordinal(enumBuilder.size());

            AstEnumNode.Builder result = super.visitEnum_value(ctx);

            AstValueNode value = valueBuilder.build();
            enumBuilder.value(value);
            this.valueBuilder = null;
            return result;
        }

        @Override
        public AstEnumNode.Builder visitInt_literal(
            Int_literalContext ctx)
        {
            return visitLiteral(ctx);
        }

        @Override
        public AstEnumNode.Builder visitUint_literal(
            Uint_literalContext ctx)
        {
            return visitLiteral(ctx);
        }

        @Override
        public AstEnumNode.Builder visitString_literal(
            String_literalContext ctx)
        {
            return visitLiteral(ctx);
        }

        private AstEnumNode.Builder visitLiteral(
            RuleContext ctx)
        {
            Function<RuleContext, Object> parser = parserByType.get(enumBuilder.valueType());
            Object parsed = parser != null ? parser.apply(ctx) : ctx.getText();
            valueBuilder.value(parsed);
            return defaultResult();
        }
    }

    private AstType lookUpAstType(
        String name)
    {
        return qualifiedPrefixes.stream()
            .map(qp -> String.format("%s%s", qp, name))
            .map(astTypesByQualifiedName::get)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(AstType.dynamicType(name));
    }
}
