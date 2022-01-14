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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;

import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstEnumNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstListNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstMapNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNamedNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstScopeNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstSpecificationNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstStructNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstType;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstTypedefNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstUnionNode;
import io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstVariantNode;

public final class TypeResolver
{
    private final String packageName;
    private final Map<String, AstNamedNode> namedNodesByName;
    private final Map<AstType, TypeName> namesByType;
    private final Map<AstType, TypeName> namesByUnsignedType;

    public TypeResolver(
        String packageName)
    {
        this.namedNodesByName = new HashMap<>();
        this.namesByType = initNamesByType(packageName);
        this.namesByUnsignedType =  initNamesByUnsignedType(packageName);
        this.packageName = packageName;
    }

    public AstNamedNode resolve(
        String qualifiedName)
    {
        return namedNodesByName.get(qualifiedName);
    }

    public ClassName flyweightName()
    {
        return (ClassName) namesByType.get(AstType.FLYWEIGHT);
    }

    public TypeName resolveType(
        AstType type)
    {
        return namesByType.get(type);
    }

    public TypeName resolveUnsignedType(
        AstType type)
    {
        return namesByUnsignedType.get(type);
    }

    public ClassName resolveClass(
        AstType type)
    {
        return (ClassName) namesByType.get(type);
    }

    public void visit(
        AstSpecificationNode specification)
    {
        namedNodesByName.putAll(specification.accept(new QualifiedNameVisitor()));
        namesByType.putAll(specification.accept(new ClassNameVisitor(packageName)));
    }

    private static Map<AstType, TypeName> initNamesByType(
        String packageName)
    {
        Map<AstType, TypeName> namesByType = new HashMap<>();
        namesByType.put(AstType.FLYWEIGHT, ClassName.get(packageName, "Flyweight"));
        namesByType.put(AstType.STRING, ClassName.get(packageName, "StringFW"));
        namesByType.put(AstType.STRING8, ClassName.get(packageName, "String8FW"));
        namesByType.put(AstType.STRING16, ClassName.get(packageName, "String16FW"));
        namesByType.put(AstType.STRING32, ClassName.get(packageName, "String32FW"));
        namesByType.put(AstType.VARSTRING, ClassName.get(packageName, "VarStringFW"));
        namesByType.put(AstType.ARRAY, ClassName.get(packageName, "ArrayFW"));
        namesByType.put(AstType.ARRAY8, ClassName.get(packageName, "Array8FW"));
        namesByType.put(AstType.ARRAY16, ClassName.get(packageName, "Array16FW"));
        namesByType.put(AstType.ARRAY32, ClassName.get(packageName, "Array32FW"));
        namesByType.put(AstType.LIST, ClassName.get(packageName, "ListFW"));
        namesByType.put(AstType.LIST0, ClassName.get(packageName, "List0FW"));
        namesByType.put(AstType.LIST8, ClassName.get(packageName, "List8FW"));
        namesByType.put(AstType.LIST32, ClassName.get(packageName, "List32FW"));
        namesByType.put(AstType.OCTETS, ClassName.get(packageName, "OctetsFW"));
        namesByType.put(AstType.BOUNDED_OCTETS, ClassName.get(packageName, "BoundedOctetsFW"));
        namesByType.put(AstType.BOUNDED_OCTETS8, ClassName.get(packageName, "BoundedOctets8FW"));
        namesByType.put(AstType.BOUNDED_OCTETS16, ClassName.get(packageName, "BoundedOctets16FW"));
        namesByType.put(AstType.BOUNDED_OCTETS32, ClassName.get(packageName, "BoundedOctets32FW"));
        namesByType.put(AstType.MAP, ClassName.get(packageName, "MapFW"));
        namesByType.put(AstType.MAP8, ClassName.get(packageName, "Map8FW"));
        namesByType.put(AstType.MAP16, ClassName.get(packageName, "Map16FW"));
        namesByType.put(AstType.MAP32, ClassName.get(packageName, "Map32FW"));
        namesByType.put(AstType.INT8, TypeName.BYTE);
        namesByType.put(AstType.UINT8, TypeName.BYTE);
        namesByType.put(AstType.INT16, TypeName.SHORT);
        namesByType.put(AstType.UINT16, TypeName.SHORT);
        namesByType.put(AstType.INT24, TypeName.INT);
        namesByType.put(AstType.UINT24, TypeName.INT);
        namesByType.put(AstType.INT32, TypeName.INT);
        namesByType.put(AstType.UINT32, TypeName.INT);
        namesByType.put(AstType.VARUINT32, ClassName.get(packageName, "Varuint32FW"));
        namesByType.put(AstType.VARUINT32N, ClassName.get(packageName, "Varuint32nFW"));
        namesByType.put(AstType.VARINT32, ClassName.get(packageName, "Varint32FW"));
        namesByType.put(AstType.VARINT64, ClassName.get(packageName, "Varint64FW"));
        namesByType.put(AstType.INT64, TypeName.LONG);
        namesByType.put(AstType.UINT64, TypeName.LONG);
        return namesByType;
    }

    private static Map<AstType, TypeName> initNamesByUnsignedType(
        String packageName)
    {
        Map<AstType, TypeName> namesByUnsignedType = new HashMap<>();
        namesByUnsignedType.put(AstType.UINT8, TypeName.INT);
        namesByUnsignedType.put(AstType.UINT16, TypeName.INT);
        namesByUnsignedType.put(AstType.UINT24, TypeName.INT);
        namesByUnsignedType.put(AstType.UINT32, TypeName.LONG);
        namesByUnsignedType.put(AstType.UINT64, TypeName.LONG);
        namesByUnsignedType.put(AstType.VARUINT32, ClassName.get(packageName, "Varuint32FW"));
        return namesByUnsignedType;
    }

    private static final class QualifiedNameVisitor extends AstNode.Visitor<Map<String, AstNamedNode>>
    {
        private final Map<String, AstNamedNode> namedNodesByName;
        private final Deque<String> nestedNames;

        private QualifiedNameVisitor()
        {
            this.namedNodesByName = new HashMap<>();
            this.nestedNames = new LinkedList<>();
        }

        @Override
        public Map<String, AstNamedNode> visitScope(
            AstScopeNode scopeNode)
        {
            try
            {
                nestedNames.addLast(scopeNode.name());
                return super.visitScope(scopeNode);
            }
            finally
            {
                nestedNames.removeLast();
            }
        }

        @Override
        public Map<String, AstNamedNode> visitStruct(
            AstStructNode structNode)
        {
            return visitNamedNode(structNode, node -> super.visitStruct((AstStructNode) node));
        }

        @Override
        public Map<String, AstNamedNode> visitEnum(
            AstEnumNode enumNode)
        {
            return visitNamedNode(enumNode, node -> super.visitEnum((AstEnumNode) node));
        }

        @Override
        public Map<String, AstNamedNode> visitList(
            AstListNode listNode)
        {
            return visitNamedNode(listNode, node -> super.visitList((AstListNode) node));
        }

        @Override
        public Map<String, AstNamedNode> visitTypedef(
            AstTypedefNode typedefNode)
        {
            return visitNamedNode(typedefNode, node -> super.visitTypedef((AstTypedefNode) node));
        }

        @Override
        public Map<String, AstNamedNode> visitMap(
            AstMapNode mapNode)
        {
            return visitNamedNode(mapNode, node -> super.visitMap((AstMapNode) node));
        }

        @Override
        public Map<String, AstNamedNode> visitUnion(
            AstUnionNode unionNode)
        {
            return visitNamedNode(unionNode, node -> super.visitUnion((AstUnionNode) node));
        }

        @Override
        public Map<String, AstNamedNode> visitVariant(
            AstVariantNode variantNode)
        {
            return visitNamedNode(variantNode, node -> super.visitVariant((AstVariantNode) node));
        }

        private Map<String, AstNamedNode> visitNamedNode(
            AstNamedNode namedNode,
            Function<AstNamedNode, Map<String, AstNamedNode>> visit)
        {
            try
            {
                nestedNames.addLast(namedNode.name());
                String qualifiedName = String.join("::", nestedNames);
                namedNodesByName.put(qualifiedName, namedNode);
                return visit.apply(namedNode);
            }
            finally
            {
                nestedNames.removeLast();
            }
        }

        @Override
        protected Map<String, AstNamedNode> defaultResult()
        {
            return namedNodesByName;
        }
    }

    private static final class ClassNameVisitor extends AstNode.Visitor<Map<AstType, TypeName>>
    {
        private final String packageName;
        private final Map<AstType, TypeName> namesByType;
        private final Deque<String> scopedNames;

        private ClassNameVisitor(
            String packageName)
        {
            this.packageName = packageName;
            this.namesByType = new HashMap<>();
            this.scopedNames = new LinkedList<>();
        }

        @Override
        public Map<AstType, TypeName> visitScope(
            AstScopeNode scopeNode)
        {
            try
            {
                scopedNames.addLast(scopeNode.name());
                return super.visitScope(scopeNode);
            }
            finally
            {
                scopedNames.removeLast();
            }
        }

        @Override
        public Map<AstType, TypeName> visitEnum(
            AstEnumNode enumNode)
        {
            return visitNamedType(enumNode, enumNode.name(), super::visitEnum);
        }

        @Override
        public Map<AstType, TypeName> visitStruct(
            AstStructNode structNode)
        {
            return visitNamedType(structNode, structNode.name(), super::visitStruct);
        }

        @Override
        public Map<AstType, TypeName> visitUnion(
            AstUnionNode unionNode)
        {
            return visitNamedType(unionNode, unionNode.name(), super::visitUnion);
        }

        @Override
        public Map<AstType, TypeName> visitVariant(
            AstVariantNode variantNode)
        {
            return visitNamedType(variantNode, variantNode.name(), super::visitVariant);
        }

        @Override
        public Map<AstType, TypeName> visitList(
            AstListNode listNode)
        {
            return visitNamedType(listNode, listNode.name(), super::visitList);
        }

        @Override
        public Map<AstType, TypeName> visitTypedef(
            AstTypedefNode typedefNode)
        {
            return visitNamedType(typedefNode, typedefNode.name(), super::visitTypedef);
        }

        @Override
        public Map<AstType, TypeName> visitMap(
            AstMapNode mapNode)
        {
            return visitNamedType(mapNode, mapNode.name(), super::visitMap);
        }

        private <N extends AstNode> Map<AstType, TypeName> visitNamedType(
            N node,
            String nodeName,
            Function<N, Map<AstType, TypeName>> visit)
        {
            List<String> packageParts = new ArrayList<>(scopedNames);
            packageParts.set(0, packageName);
            String classPackage = String.join(".", packageParts);

            try
            {
                scopedNames.addLast(nodeName);

                String scopedName = String.join("::", scopedNames);
                AstType type = AstType.dynamicType(scopedName);

                String simpleName = nodeName + "FW";
                ClassName className = ClassName.get(classPackage, simpleName);
                namesByType.put(type, className);

                return visit.apply(node);
            }
            finally
            {
                scopedNames.removeLast();
            }
        }

        @Override
        protected Map<AstType, TypeName> defaultResult()
        {
            return namesByType;
        }
    }
}
