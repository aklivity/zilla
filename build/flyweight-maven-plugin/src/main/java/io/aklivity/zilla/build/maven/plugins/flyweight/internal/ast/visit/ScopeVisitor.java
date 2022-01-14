/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.visit;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstByteOrder;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstEnumNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstListNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstMapNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNamedNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNamedNode.Kind;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstScopeNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstStructNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstType;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstTypedefNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstUnionNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstVariantNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.EnumFlyweightGenerator;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.EnumTypeGenerator;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.ListFlyweightGenerator;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.MapFlyweightGenerator;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.StructFlyweightGenerator;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeResolver;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeSpecGenerator;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.UnionFlyweightGenerator;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.VariantFlyweightGenerator;

public final class ScopeVisitor extends AstNode.Visitor<Collection<TypeSpecGenerator<?>>>
{
    private final String scopeName;
    private final String packageName;
    private final TypeResolver resolver;
    private final List<String> targetScopes;
    private final Collection<TypeSpecGenerator<?>> defaultResult;

    public ScopeVisitor(
        String scopeName,
        String packageName,
        TypeResolver resolver,
        List<String> targetScopes)
    {
        this.scopeName = requireNonNull(scopeName);
        this.packageName = requireNonNull(packageName);
        this.resolver = requireNonNull(resolver);
        this.targetScopes = requireNonNull(targetScopes);
        this.defaultResult = new LinkedList<>();
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitScope(
        AstScopeNode scopeNode)
    {
        if (!targetScopes.stream().anyMatch(this::shouldVisit))
        {
            return defaultResult();
        }

        return super.visitScope(scopeNode);
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitNestedScope(
        AstScopeNode scopeNode)
    {
        String nestedName = scopeNode.name();
        String subscopeName = String.format("%s::%s", scopeName, nestedName);
        String subpackageName = String.format("%s.%s", packageName, nestedName);
        return new ScopeVisitor(subscopeName, subpackageName, resolver, targetScopes).visitScope(scopeNode);
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitStruct(
        AstStructNode structNode)
    {
        if (!targetScopes.stream().anyMatch(this::shouldVisit))
        {
            return defaultResult();
        }

        String baseName = structNode.name();
        AstType structType = AstType.dynamicType(String.format("%s::%s", scopeName, baseName));
        ClassName structName = resolver.resolveClass(structType);
        StructFlyweightGenerator generator = new StructFlyweightGenerator(structName, resolver.flyweightName(), baseName,
            resolver);
        generator.typeId(findTypeId(structNode));

        return new StructVisitor(generator, resolver).visitStruct(structNode);
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitTypedef(
        AstTypedefNode typedefNode)
    {
        if (!targetScopes.stream().anyMatch(this::shouldVisit))
        {
            return defaultResult();
        }

        AstNamedNode originalNode = resolver.resolve(typedefNode.originalType().name());
        AstNamedNode newNode = originalNode.withName(typedefNode.name());
        Kind kind = newNode.getKind();
        switch (kind)
        {
        case STRUCT:
            return visitStruct((AstStructNode) newNode);
        case UNION:
            return visitUnion((AstUnionNode) newNode);
        case VARIANT:
            return visitVariant((AstVariantNode) newNode);
        case LIST:
            return visitList((AstListNode) newNode);
        case ENUM:
            return visitEnum((AstEnumNode) newNode);
        case TYPEDEF:
            return visitTypedef((AstTypedefNode) newNode);
        case DEFAULT:
        default:
            return defaultResult();
        }
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitMap(
        AstMapNode mapNode)
    {
        if (!targetScopes.stream().anyMatch(this::shouldVisit))
        {
            return defaultResult();
        }

        AstVariantNode templateNode = (AstVariantNode) resolver.resolve(mapNode.templateMapType().name());
        if (AstType.MAP.equals(templateNode.of()))
        {
            String baseName = mapNode.name();
            AstType mapType = AstType.dynamicType(String.format("%s::%s", scopeName, baseName));
            ClassName mapName = resolver.resolveClass(mapType);
            ClassName templateMapTypeName = resolver.resolveClass(mapNode.templateMapType());
            ClassName mapKeyTypeName = resolver.resolveClass(mapNode.keyType());
            ClassName mapValueTypeName = resolver.resolveClass(mapNode.valueType());

            MapFlyweightGenerator generator = new MapFlyweightGenerator(mapName, resolver.flyweightName(), templateMapTypeName,
                mapNode.keyType(), mapKeyTypeName, mapNode.valueType(), mapValueTypeName, resolver);
            defaultResult.add(generator);
        }
        return defaultResult();
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitUnion(
        AstUnionNode unionNode)
    {
        if (!targetScopes.stream().anyMatch(this::shouldVisit))
        {
            return defaultResult();
        }

        String baseName = unionNode.name();
        AstType unionType = AstType.dynamicType(String.format("%s::%s", scopeName, baseName));
        ClassName unionName = resolver.resolveClass(unionType);
        AstType unionSuperType = unionNode.superType();
        TypeName kindTypeName = unionNode.kindType().equals(AstType.UINT8) ? resolver.resolveType(AstType.UINT8) :
            resolver.resolveClass(unionNode.kindType());
        UnionFlyweightGenerator generator = new UnionFlyweightGenerator(unionName, resolver.flyweightName(), baseName,
            unionSuperType, kindTypeName, resolver);

        return new UnionVisitor(generator, resolver).visitUnion(unionNode);
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitEnum(
        AstEnumNode enumNode)
    {
        if (!targetScopes.stream().anyMatch(this::shouldVisit))
        {
            return defaultResult();
        }

        String baseName = enumNode.name();
        AstType enumType = AstType.dynamicType(String.format("%s::%s", scopeName, baseName));
        AstType valueType = enumNode.valueType();
        TypeName valueTypeName = resolver.resolveType(valueType);
        TypeName enumClassValueTypeName = valueTypeName;
        TypeName unsignedValueTypeName = resolver.resolveUnsignedType(valueType);
        if (valueType != null && valueType.isDynamicType())
        {
            AstVariantNode variantNode = (AstVariantNode) resolver.resolve(valueType.name());
            enumClassValueTypeName = resolver.resolveType(variantNode.of());
            unsignedValueTypeName = resolver.resolveUnsignedType(variantNode.of());
        }
        ClassName enumFlyweightName = resolver.resolveClass(enumType);
        ClassName enumTypeName = enumFlyweightName.peerClass(baseName);

        EnumTypeGenerator typeGenerator = new EnumTypeGenerator(enumTypeName, enumClassValueTypeName, unsignedValueTypeName);
        EnumFlyweightGenerator flyweightGenerator = new EnumFlyweightGenerator(enumFlyweightName, resolver.flyweightName(),
            enumTypeName, valueTypeName, enumClassValueTypeName, unsignedValueTypeName);

        return new EnumVisitor(typeGenerator, flyweightGenerator).visitEnum(enumNode);
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitVariant(
        AstVariantNode variantNode)
    {
        if (!targetScopes.stream().anyMatch(this::shouldVisit))
        {
            return defaultResult();
        }

        String baseName = variantNode.name();
        AstType variantType = AstType.dynamicType(String.format("%s::%s", scopeName, baseName));
        ClassName variantName = resolver.resolveClass(variantType);

        TypeName kindTypeName = variantNode.kindType().equals(AstType.UINT8) ? resolver.resolveType(AstType.UINT8) :
            resolver.resolveClass(variantNode.kindType());
        AstType ofType = variantNode.of();
        ClassName flyweightName = resolver.flyweightName();
        TypeName ofTypeName = resolver.resolveType(variantNode.of());
        TypeName unsignedOfTypeName = resolver.resolveUnsignedType(variantNode.of());
        AstByteOrder byteOrder = variantNode.byteOrder();
        VariantFlyweightGenerator generator = new VariantFlyweightGenerator(variantName, flyweightName, baseName,
            kindTypeName, ofType, ofTypeName, unsignedOfTypeName, resolver, byteOrder);
        return new VariantVisitor(generator, resolver).visitVariant(variantNode);
    }

    @Override
    public Collection<TypeSpecGenerator<?>> visitList(
        AstListNode listNode)
    {
        if (!targetScopes.stream().anyMatch(this::shouldVisit))
        {
            return defaultResult();
        }

        String baseName = listNode.name();
        AstType listType = AstType.dynamicType(String.format("%s::%s", scopeName, baseName));
        ClassName listName = resolver.resolveClass(listType);
        AstType templateType = listNode.templateType();
        TypeName lengthTypeName = resolver.resolveType(listNode.lengthType());
        TypeName fieldCountTypeName = resolver.resolveType(listNode.fieldCountType());
        Byte missingFieldByte = listNode.missingFieldByte();
        AstByteOrder byteOrder = listNode.byteOrder();
        ListFlyweightGenerator generator = new ListFlyweightGenerator(listName, resolver.resolveClass(AstType.LIST), baseName,
            templateType, lengthTypeName, fieldCountTypeName, missingFieldByte, resolver, byteOrder);
        return new ListVisitor(generator, resolver).visitList(listNode);
    }

    @Override
    protected Collection<TypeSpecGenerator<?>> defaultResult()
    {
        return defaultResult;
    }

    @Override
    protected Collection<TypeSpecGenerator<?>> aggregateResult(
        Collection<TypeSpecGenerator<?>> aggregate,
        Collection<TypeSpecGenerator<?>> nextResult)
    {
        if (nextResult != aggregate)
        {
            aggregate.addAll(nextResult);
        }
        return aggregate;
    }

    private boolean shouldVisit(
        String target)
    {
        return target.equals(scopeName) || scopeName.startsWith(target + "::") || target.startsWith(scopeName + "::");
    }

    private int findTypeId(
        AstStructNode structNode)
    {
        AstStructNode currentNode = structNode;
        while (currentNode != null && currentNode.typeId() == 0 && currentNode.supertype() != null)
        {
            AstNamedNode namedNode = resolver.resolve(currentNode.supertype().name());

            currentNode = namedNode != null && namedNode.getKind() == Kind.STRUCT ? (AstStructNode) namedNode : null;
        }

        return (currentNode != null) ? currentNode.typeId() : 0;
    }
}
