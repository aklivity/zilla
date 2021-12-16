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
package io.aklivity.zilla.build.maven.plugins.cog.internal.generate;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstByteOrder.NATIVE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.BIT_UTIL_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.BUFFER_UTIL_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static java.util.Collections.unmodifiableMap;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.FieldSpec.Builder;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstByteOrder;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstNamedNode.Kind;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstType;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstVariantNode;

public final class VariantFlyweightGenerator extends ClassSpecGenerator
{
    private static final Map<String, String> NUMBER_WORDS;
    private static final Map<TypeName, String> TYPE_NAMES;
    private static final Map<String, Long> BIT_MASK_LONG;
    private static final Map<String, Integer> BIT_MASK_INT;
    private static final Map<TypeName, String> CLASS_NAMES;
    private static final Set<TypeName> INTEGER_TYPES;

    private final String baseName;
    private final TypeSpec.Builder builder;
    private final MemberFieldGenerator memberField;
    private final KindConstantGenerator memberKindConstant;
    private final MemberSizeConstantGenerator memberSizeConstant;
    private final MemberOffsetConstantGenerator memberOffsetConstant;
    private final MemberFieldValueConstantGenerator memberFieldValueConstant;
    private final MissingFieldPlaceholderConstantGenerator missingFieldPlaceholderConstant;
    private final ConstructorGenerator constructor;
    private final TryWrapMethodGenerator tryWrapMethod;
    private final WrapMethodGenerator wrapMethod;
    private final WrapMethodWithArrayGenerator wrapMethodWithArray;
    private final ToStringMethodGenerator toStringMethod;
    private final StringOfTypeMethodsGenerator stringOfTypeMethods;
    private final ListOfTypeMethodsGenerator listOfTypeMethods;
    private final ArrayOfTypeMethodsGenerator arrayOfTypeMethods;
    private final OctetsOfTypeMethodsGenerator octetsOfTypeMethods;
    private final KindAccessorGenerator kindAccessor;
    private final MemberAccessorGenerator memberAccessor;
    private final LimitMethodGenerator limitMethod;
    private final MapOfTypeMethodsGenerator mapOfTypeMethods;
    private final BuilderClassGenerator builderClass;
    private final GetMethodGenerator getMethod;
    private final BitMaskConstantGenerator bitMaskConstant;
    private final TypeVariableName typeVarV;
    private final TypeVariableName typeVarO;
    private final TypeVariableName typeVarKV;
    private final TypeVariableName typeVarVV;

    static
    {
        Map<String, String> numberByWord = new HashMap<>();
        numberByWord.put("0", "zero");
        numberByWord.put("1", "one");
        NUMBER_WORDS = unmodifiableMap(numberByWord);

        Map<TypeName, String> typeNames = new HashMap<>();
        typeNames.put(TypeName.BYTE, "Byte");
        typeNames.put(TypeName.CHAR, "Char");
        typeNames.put(TypeName.SHORT, "Short");
        typeNames.put(TypeName.INT, "Int");
        typeNames.put(TypeName.FLOAT, "Float");
        typeNames.put(TypeName.LONG, "Long");
        typeNames.put(TypeName.DOUBLE, "Double");
        TYPE_NAMES = unmodifiableMap(typeNames);

        Map<String, Long> longBitMaskValues = new HashMap<>();
        longBitMaskValues.put("int8", 0xffff_ffff_ffff_ff80L);
        longBitMaskValues.put("int16", 0xffff_ffff_ffff_8000L);
        longBitMaskValues.put("int24", 0xffff_ffff_ff80_0000L);
        longBitMaskValues.put("int32", 0xffff_ffff_8000_0000L);
        BIT_MASK_LONG = unmodifiableMap(longBitMaskValues);

        Map<String, Integer> intBitMaskValues = new HashMap<>();
        intBitMaskValues.put("int8", 0xffff_ff80);
        intBitMaskValues.put("int16", 0xffff_8000);
        intBitMaskValues.put("int24", 0xff80_0000);
        BIT_MASK_INT = unmodifiableMap(intBitMaskValues);

        Map<TypeName, String> classNames = new HashMap<>();
        classNames.put(TypeName.BYTE, "Byte");
        classNames.put(TypeName.SHORT, "Short");
        classNames.put(TypeName.INT, "Integer");
        classNames.put(TypeName.FLOAT, "Float");
        classNames.put(TypeName.LONG, "Long");
        classNames.put(TypeName.DOUBLE, "Double");
        CLASS_NAMES = unmodifiableMap(classNames);

        Set<TypeName> integerTypes = new HashSet<>();
        integerTypes.add(TypeName.INT);
        integerTypes.add(TypeName.SHORT);
        integerTypes.add(TypeName.BYTE);
        INTEGER_TYPES = integerTypes;
    }

    public VariantFlyweightGenerator(
        ClassName variantName,
        ClassName flyweightName,
        String baseName,
        TypeName kindTypeName,
        AstType ofType,
        TypeName ofTypeName,
        TypeName unsignedOfTypeName,
        TypeResolver resolver,
        AstByteOrder byteOrder)
    {
        super(variantName);
        this.typeVarO = TypeVariableName.get("O", flyweightName);
        this.typeVarV = TypeVariableName.get("V", flyweightName);
        this.typeVarKV = TypeVariableName.get("K", flyweightName);
        this.typeVarVV = TypeVariableName.get("V", flyweightName);
        this.baseName = baseName;
        this.builder = builder(variantName, kindTypeName, ofType, resolver);
        this.memberField = new MemberFieldGenerator(variantName, kindTypeName, ofType, typeVarV, typeVarKV, typeVarVV, builder,
            resolver, byteOrder);
        this.memberKindConstant = new KindConstantGenerator(variantName, kindTypeName, ofType, builder);
        this.memberSizeConstant = new MemberSizeConstantGenerator(variantName, kindTypeName, builder);
        this.memberOffsetConstant = new MemberOffsetConstantGenerator(variantName, kindTypeName, builder);
        this.memberFieldValueConstant = new MemberFieldValueConstantGenerator(variantName, builder);
        this.missingFieldPlaceholderConstant = new MissingFieldPlaceholderConstantGenerator(variantName, builder);
        this.constructor = new ConstructorGenerator(ofType, typeVarV, typeVarKV, typeVarVV, byteOrder);
        this.tryWrapMethod = new TryWrapMethodGenerator(kindTypeName, ofType, resolver);
        this.wrapMethod = new WrapMethodGenerator(kindTypeName, ofType, resolver);
        this.wrapMethodWithArray = new WrapMethodWithArrayGenerator(kindTypeName, ofType, resolver);
        this.toStringMethod = new ToStringMethodGenerator(kindTypeName, ofType, resolver);
        this.stringOfTypeMethods = new StringOfTypeMethodsGenerator(variantName, builder, kindTypeName, ofType);
        this.listOfTypeMethods = new ListOfTypeMethodsGenerator(variantName, builder, kindTypeName, ofType);
        this.arrayOfTypeMethods = new ArrayOfTypeMethodsGenerator(variantName, builder, kindTypeName, typeVarV, ofType);
        this.octetsOfTypeMethods = new OctetsOfTypeMethodsGenerator(variantName, flyweightName, builder, kindTypeName, ofType);
        this.kindAccessor = new KindAccessorGenerator(variantName, kindTypeName, ofType, builder);
        this.memberAccessor = new MemberAccessorGenerator(variantName, kindTypeName, ofType, builder, resolver, byteOrder);
        this.limitMethod = new LimitMethodGenerator(kindTypeName, ofType, resolver);
        this.mapOfTypeMethods = new MapOfTypeMethodsGenerator(variantName, ofType, builder);
        this.getMethod = new GetMethodGenerator(kindTypeName, ofType, ofTypeName, unsignedOfTypeName, resolver);
        this.bitMaskConstant = new BitMaskConstantGenerator(variantName, ofTypeName, builder);
        this.builderClass = new BuilderClassGenerator(variantName, flyweightName, kindTypeName, ofType, ofTypeName,
            unsignedOfTypeName, resolver, typeVarO, byteOrder);
    }

    public VariantFlyweightGenerator addMember(
        Object kindValue,
        String memberName,
        AstType memberType,
        TypeName memberTypeName,
        TypeName unsignedMemberTypeName,
        int missingFieldValue,
        AstType mapKeyType,
        AstType mapValueType)
    {
        memberKindConstant.addMember(kindValue, memberName, memberType, memberTypeName);
        memberOffsetConstant.addMember(memberName, memberTypeName);
        memberFieldValueConstant.addMember(memberName, memberTypeName);
        memberField.addMember(memberName, memberType, memberTypeName, mapKeyType, mapValueType);
        memberSizeConstant.addMember(memberName, memberTypeName, unsignedMemberTypeName);
        missingFieldPlaceholderConstant.addMember(memberType, missingFieldValue);
        constructor.addMember(memberName, memberType, memberTypeName);
        wrapMethod.addMember(kindValue, memberName, memberTypeName, mapKeyType);
        wrapMethodWithArray.addMember(kindValue, memberName);
        tryWrapMethod.addMember(kindValue, memberName, memberTypeName, mapKeyType);
        toStringMethod.addMember(memberName, kindValue, memberName, memberType, memberTypeName, mapKeyType);
        memberAccessor.addMember(memberName, memberType, memberTypeName, unsignedMemberTypeName, mapKeyType, mapValueType);
        limitMethod.addMember(memberName, kindValue, memberName, memberTypeName, mapKeyType);
        getMethod.addMember(memberName, kindValue, memberType, memberTypeName);
        bitMaskConstant.addMember(memberName, memberType, memberTypeName, unsignedMemberTypeName);
        builderClass.addMember(kindValue, memberName, memberType, memberTypeName, unsignedMemberTypeName, mapKeyType,
            mapValueType);
        return this;
    }

    @Override
    public TypeSpec generate()
    {
        memberKindConstant.build();
        memberSizeConstant.build();
        missingFieldPlaceholderConstant.build();
        memberField.build();
        kindAccessor.build();
        memberAccessor.build();
        bitMaskConstant.build();
        constructor.mixin(builder);
        getMethod.mixin(builder);
        tryWrapMethod.mixin(builder);
        wrapMethod.mixin(builder);
        wrapMethodWithArray.mixin(builder);
        toStringMethod.mixin(builder);
        stringOfTypeMethods.build();
        listOfTypeMethods.build();
        arrayOfTypeMethods.build();
        octetsOfTypeMethods.build();
        limitMethod.mixin(builder);
        mapOfTypeMethods.build();
        return builder.addType(builderClass.generate())
                      .build();
    }

    private TypeSpec.Builder builder(
        ClassName variantName,
        TypeName kindTypeName,
        AstType ofType,
        TypeResolver resolver)
    {
        TypeSpec.Builder classBuilder = classBuilder(variantName);
        if ((isListType(ofType) || isStringType(ofType) || isBoundedOctetsType(ofType)) && !kindTypeName.isPrimitive())
        {
            return classBuilder
                .superclass(resolver.resolveClass(ofType))
                .addModifiers(PUBLIC, FINAL);
        }
        else if (isArrayType(ofType))
        {
            TypeName parameterizedOfTypeName = ParameterizedTypeName.get(resolver.resolveClass(ofType), typeVarV);
            return classBuilder
                .addTypeVariable(typeVarV)
                .superclass(parameterizedOfTypeName)
                .addModifiers(PUBLIC, FINAL);
        }
        else if (isMapType(ofType))
        {
            return classBuilder
                .addTypeVariable(typeVarKV)
                .addTypeVariable(typeVarVV)
                .superclass(ParameterizedTypeName.get(resolver.resolveClass(ofType), typeVarKV, typeVarVV))
                .addModifiers(PUBLIC, FINAL);
        }
        return classBuilder
            .superclass(resolver.flyweightName())
            .addModifiers(PUBLIC, FINAL);
    }

    private static final class MemberFieldGenerator extends ClassSpecMixinGenerator
    {
        private final AstType ofType;
        private final TypeVariableName typeVarV;
        private final TypeVariableName typeVarKV;
        private final TypeVariableName typeVarVV;
        private final TypeResolver resolver;
        private final AstByteOrder byteOrder;

        private MemberFieldGenerator(
            ClassName thisType,
            TypeName kindName,
            AstType ofType,
            TypeVariableName typeVarV,
            TypeVariableName typeVarKV,
            TypeVariableName typeVarVV,
            TypeSpec.Builder builder,
            TypeResolver resolver,
            AstByteOrder byteOrder)
        {
            super(thisType, builder);
            this.ofType = ofType;
            this.typeVarV = typeVarV;
            this.typeVarKV = typeVarKV;
            this.typeVarVV = typeVarVV;
            this.resolver = resolver;
            this.byteOrder = byteOrder;
            if (!kindName.isPrimitive())
            {
                String kindTypeVariableName = enumRO(kindName);
                builder.addField(FieldSpec.builder(kindName, kindTypeVariableName, PRIVATE, FINAL)
                                          .initializer("new $T()", kindName)
                                          .build());
            }
        }

        public MemberFieldGenerator addMember(
            String name,
            AstType memberType,
            TypeName memberTypeName,
            AstType mapKeyType,
            AstType mapValueType)
        {
            if (memberTypeName != null && !memberTypeName.isPrimitive())
            {
                String fieldRO = String.format("%sRO", name);
                if (memberType != null && memberType.isDynamicType() || ofType == null)
                {
                    fieldRO = String.format("%sRO", fieldName(memberTypeName));
                }
                Builder fieldBuilder;
                if (isArrayType(ofType))
                {
                    TypeName parameterizedArrayType = ParameterizedTypeName.get((ClassName) memberTypeName, typeVarV);
                    fieldBuilder = FieldSpec.builder(parameterizedArrayType, fieldRO, PRIVATE, FINAL);
                }
                else if (isMapType(ofType))
                {
                    TypeName parameterizedMapType = ParameterizedTypeName.get((ClassName) memberTypeName, typeVarKV, typeVarVV);
                    fieldBuilder = FieldSpec.builder(parameterizedMapType, fieldRO, PRIVATE, FINAL);
                }
                else if (mapKeyType != null)
                {
                    TypeName parameterizedMapTypeName =
                        ParameterizedTypeName.get((ClassName) memberTypeName, resolver.resolveClass(mapKeyType),
                            resolver.resolveClass(mapValueType));
                    fieldBuilder = FieldSpec.builder(parameterizedMapTypeName, fieldRO, PRIVATE);
                }
                else if (isListType(ofType) || isBoundedOctetsType(ofType) || isStringType(ofType))
                {
                    fieldBuilder = FieldSpec.builder(memberTypeName, fieldRO, PRIVATE, FINAL);
                    if (byteOrder == NATIVE || memberType.equals(AstType.LIST0) || memberType.equals(AstType.LIST8) ||
                        memberType.equals(AstType.BOUNDED_OCTETS8) || memberType.equals(AstType.STRING8))
                    {
                        fieldBuilder.initializer("new $T()", memberTypeName);
                    }
                    else
                    {
                        fieldBuilder.initializer("new $T($T.BIG_ENDIAN)", memberTypeName, ByteOrder.class);
                    }
                }
                else
                {
                    fieldBuilder = FieldSpec.builder(memberTypeName, fieldRO, PRIVATE, FINAL)
                        .initializer("new $T()", memberTypeName);
                }
                builder.addField(fieldBuilder.build());
            }
            return this;
        }
    }

    private static final class KindConstantGenerator extends ClassSpecMixinGenerator
    {
        private final TypeName kindName;
        private final AstType ofType;

        private KindConstantGenerator(
            ClassName thisType,
            TypeName kindName,
            AstType ofType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            this.kindName = kindName;
            this.ofType = ofType;
        }

        public KindConstantGenerator addMember(
            Object kindValue,
            String name,
            AstType memberType,
            TypeName memberTypeName)
        {
            FieldSpec field;
            String kindName = name;
            if ((memberTypeName != null && memberType.isDynamicType()) || ofType == null)
            {
                kindName = kindValue.toString();
            }
            if (this.kindName.isPrimitive())
            {
                field = FieldSpec.builder(int.class, kind(kindName), PUBLIC, STATIC, FINAL)
                    .initializer("$L", kindValue)
                    .build();
            }
            else
            {
                ClassName enumTypeName = enumClassName(this.kindName);
                field = FieldSpec.builder(enumTypeName, kind(kindName), PUBLIC, STATIC, FINAL)
                    .initializer("$L.$L", enumTypeName.simpleName(), kindValue)
                    .build();
            }
            builder.addField(field);
            return this;
        }
    }

    private static final class MemberSizeConstantGenerator extends ClassSpecMixinGenerator
    {
        private MemberSizeConstantGenerator(
            ClassName thisType,
            TypeName kindName,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            if (kindName.isPrimitive())
            {
                builder.addField(FieldSpec.builder(int.class, size("kind"), PRIVATE, STATIC, FINAL)
                    .initializer("$T.SIZE_OF_BYTE", BIT_UTIL_TYPE)
                    .build());
            }
        }

        public MemberSizeConstantGenerator addMember(
            String name,
            TypeName memberTypeName,
            TypeName unsignedMemberTypeName)
        {
            if (memberTypeName != null && memberTypeName.isPrimitive())
            {
                builder.addField(
                    FieldSpec.builder(int.class, size(name), PRIVATE, STATIC, FINAL)
                             .initializer("$T.SIZE_OF_$L", BIT_UTIL_TYPE, TYPE_NAMES.get(memberTypeName).toUpperCase())
                             .build());
            }
            return this;
        }
    }

    private static final class MemberOffsetConstantGenerator extends ClassSpecMixinGenerator
    {
        private final TypeName kindName;

        private MemberOffsetConstantGenerator(
            ClassName thisType,
            TypeName kindName,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            this.kindName = kindName;
            if (kindName.isPrimitive())
            {
                builder.addField(FieldSpec.builder(int.class, offset("kind"), PRIVATE, STATIC, FINAL)
                       .initializer("0")
                       .build());
            }
        }

        public MemberOffsetConstantGenerator addMember(
            String memberName,
            TypeName memberTypeName)
        {
            if (kindName.isPrimitive() && memberTypeName != null)
            {
                builder.addField(
                    FieldSpec.builder(int.class, offset(memberName), PRIVATE, STATIC, FINAL)
                             .initializer(String.format("%s + %s", offset("kind"), size("kind")))
                             .build());
            }
            return this;
        }
    }

    private static final class MemberFieldValueConstantGenerator extends ClassSpecMixinGenerator
    {
        private MemberFieldValueConstantGenerator(
            ClassName thisType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
        }

        public MemberFieldValueConstantGenerator addMember(
            String memberName,
            TypeName memberTypeName)
        {
            if (memberName != null && memberTypeName == null)
            {
                builder.addField(
                    FieldSpec.builder(int.class, value(memberName), PRIVATE, STATIC, FINAL)
                             .initializer(memberName)
                             .build());
            }
            return this;
        }
    }

    private static final class MissingFieldPlaceholderConstantGenerator extends ClassSpecMixinGenerator
    {
        private int missingFieldValue;

        private MissingFieldPlaceholderConstantGenerator(
            ClassName thisType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
        }

        public MissingFieldPlaceholderConstantGenerator addMember(
            AstType memberType,
            int missingFieldValue)
        {
            if (AstType.LIST32.equals(memberType) || AstType.LIST8.equals(memberType))
            {
                if (missingFieldValue != 0)
                {
                    this.missingFieldValue = missingFieldValue;
                }
            }
            return this;
        }

        @Override
        public TypeSpec.Builder build()
        {
            if (missingFieldValue != 0)
            {
                builder.addField(
                    FieldSpec.builder(byte.class, "MISSING_FIELD_PLACEHOLDER", PUBLIC, STATIC, FINAL)
                        .initializer(String.valueOf(missingFieldValue))
                        .build());
            }
            return super.build();
        }
    }

    private static final class ConstructorGenerator extends MethodSpecGenerator
    {
        private final AstType ofType;
        private final TypeVariableName typeVarV;
        private final TypeVariableName typeVarKV;
        private final TypeVariableName typeVarVV;
        private final AstByteOrder byteOrder;

        private ConstructorGenerator(
            AstType ofType,
            TypeVariableName typeVarV,
            TypeVariableName typeVarKV,
            TypeVariableName typeVarVV,
            AstByteOrder byteOrder)
        {
            super(constructorBuilder()
                .addModifiers(PUBLIC));
            this.ofType = ofType;
            this.typeVarV = typeVarV;
            this.typeVarKV = typeVarKV;
            this.typeVarVV = typeVarVV;
            this.byteOrder = byteOrder;
        }

        public ConstructorGenerator addMember(
            String memberName,
            AstType memberType,
            TypeName memberTypeName)
        {
            if (isArrayType(ofType))
            {
                if (byteOrder == NATIVE || memberType.equals(AstType.ARRAY8))
                {
                    builder.addStatement("$LRO = new $T<>(type)", memberName, memberTypeName);
                }
                else
                {
                    builder.addStatement("$LRO = new $T<>(type, $T.BIG_ENDIAN)", memberName, memberTypeName, ByteOrder.class);
                }
            }
            else if (isMapType(ofType))
            {
                if (byteOrder == NATIVE || memberType.equals(AstType.MAP8))
                {
                    builder.addStatement("$LRO = new $T<>(keyType, valueType)", memberName, memberTypeName);
                }
                else
                {
                    builder.addStatement("$LRO = new $T<>(keyType, valueType, $T.BIG_ENDIAN)", memberName, memberTypeName,
                        ByteOrder.class);
                }
            }
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            if (isArrayType(ofType))
            {
                builder.addParameter(typeVarV, "type");
            }
            else if (isMapType(ofType))
            {
                builder.addParameter(typeVarKV, "keyType")
                    .addParameter(typeVarVV, "valueType");
            }
            return builder.build();
        }

        @Override
        public void mixin(
            TypeSpec.Builder builder)
        {
            if (isArrayType(ofType) || isMapType(ofType))
            {
                super.mixin(builder);
            }
        }
    }

    private final class TryWrapMethodGenerator extends MethodSpecGenerator
    {
        private final TypeName kindTypeName;
        private final AstType ofType;
        private final TypeResolver resolver;

        private TryWrapMethodGenerator(
            TypeName kindTypeName,
            AstType ofType,
            TypeResolver resolver)
        {
            super(methodBuilder("tryWrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .returns(thisName)
                .beginControlFlow("if (super.tryWrap(buffer, offset, maxLimit) == null)")
                .addStatement("return null")
                .endControlFlow());
            this.kindTypeName = kindTypeName;
            this.ofType = ofType;
            this.resolver = resolver;

            if (isArrayType(ofType))
            {
                builder.returns(ParameterizedTypeName.get(thisName, typeVarV));
            }
            else if (isMapType(ofType))
            {
                builder.returns(ParameterizedTypeName.get(thisName, typeVarKV, typeVarVV));
            }

            if (!kindTypeName.isPrimitive())
            {
                final String kindFWName = enumFWName(kindTypeName);
                final String kindRO = enumRO(kindTypeName);

                builder.addStatement("final $T $L = $L.tryWrap(buffer, offset, maxLimit)",
                        kindTypeName, kindFWName, kindRO);

                builder.beginControlFlow("if ($L == null)", kindFWName)
                    .addStatement("return null")
                    .endControlFlow();

                if (isStringType(ofType))
                {
                    builder.addStatement("kind = $L.get()", kindFWName)
                        .beginControlFlow("switch (kind)");
                }
                else
                {
                    builder.beginControlFlow("if (kind() == null)")
                            .addStatement("return null")
                            .endControlFlow();
                    builder.beginControlFlow("switch (kind())");
                }
            }
            else
            {
                builder.beginControlFlow("switch (kind())");
            }
        }

        public TryWrapMethodGenerator addMember(
            Object kindValue,
            String memberName,
            TypeName memberTypeName,
            AstType mapKeyType)
        {
            builder.beginControlFlow("case $L:", kindTypeName.isPrimitive() ? kind(memberName) : kindValue);
            if (isNonPrimitiveType(ofType))
            {
                builder.beginControlFlow("if ($LRO.tryWrap(buffer, offset + $L, maxLimit) == null)", memberName,
                    kindTypeName.isPrimitive() ? offset(memberName) : String.format("%s.sizeof()", enumFWName(kindTypeName)))
                       .addStatement("return null")
                       .endControlFlow();
            }
            else if (mapKeyType != null)
            {
                builder.beginControlFlow("if ($L().tryWrap(buffer, offset, maxLimit) == null)",
                    fieldName(memberTypeName))
                    .addStatement("return null")
                    .endControlFlow();
            }
            else if (ofType == null && memberTypeName != null)
            {
                builder.beginControlFlow("if ($LRO.tryWrap(buffer, offset, maxLimit) == null)",
                    fieldName(memberTypeName))
                    .addStatement("return null")
                    .endControlFlow();
            }
            else if (resolver.resolve(memberName) != null && resolver.resolve(memberName).getKind() == Kind.VARIANT)
            {
                builder.beginControlFlow("if ($LRO.tryWrap(buffer, offset + $L, maxLimit) == null)",
                    fieldName(memberTypeName), kindTypeName.isPrimitive() ? offset(memberName) : String.format("%s.sizeof()",
                        enumFWName(kindTypeName)))
                    .addStatement("return null")
                    .endControlFlow();
            }
            if (ofType != null || memberTypeName != null)
            {
                builder.addStatement("break");
            }
            builder.endControlFlow();
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            return builder.beginControlFlow("default:")
                          .addStatement("return null")
                          .endControlFlow()
                          .endControlFlow()
                          .beginControlFlow("if (limit() > maxLimit)")
                          .addStatement("return null")
                          .endControlFlow()
                          .addStatement("return this")
                          .build();
        }
    }

    private final class WrapMethodGenerator extends MethodSpecGenerator
    {
        private final TypeName kindTypeName;
        private final AstType ofType;
        private final TypeResolver resolver;

        private WrapMethodGenerator(
            TypeName kindTypeName,
            AstType ofType,
            TypeResolver resolver)
        {
            super(methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .returns(thisName)
                .addStatement("super.wrap(buffer, offset, maxLimit)"));
            this.kindTypeName = kindTypeName;
            this.ofType = ofType;
            this.resolver = resolver;
            if (isArrayType(ofType))
            {
                builder.returns(ParameterizedTypeName.get(thisName, typeVarV));
            }
            else if (isMapType(ofType))
            {
                builder.returns(ParameterizedTypeName.get(thisName, typeVarKV, typeVarVV));
            }

            if (!kindTypeName.isPrimitive())
            {
                final String kindVar = enumFWName(kindTypeName);
                final String kindRO = enumRO(kindTypeName);

                builder.addStatement("final $T $L = $L.wrap(buffer, offset, maxLimit)", kindTypeName, kindVar, kindRO);

                if (isStringType(ofType))
                {
                    builder.addStatement("kind = $L.get()", kindVar)
                        .beginControlFlow("switch (kind)");
                }
                else
                {
                    builder.beginControlFlow("switch ($L.get())", kindVar);
                }
            }
            else
            {
                builder.beginControlFlow("switch (kind())");
            }
        }

        public WrapMethodGenerator addMember(
            Object kindValue,
            String memberName,
            TypeName memberTypeName,
            AstType mapKeyType)
        {
            builder.beginControlFlow("case $L:", kindTypeName.isPrimitive() ? kind(memberName) : kindValue);
            if (isNonPrimitiveType(ofType))
            {
                builder.addStatement("$LRO.wrap(buffer, offset + $L, maxLimit)", memberName,
                    kindTypeName.isPrimitive() ? offset(memberName) : String.format("%s.sizeof()", enumFWName(kindTypeName)));
            }
            else if (mapKeyType != null)
            {
                builder.addStatement("$L().wrap(buffer, offset, maxLimit)", fieldName(memberTypeName));
            }
            else if (ofType == null && memberTypeName != null)
            {
                builder.addStatement("$LRO.wrap(buffer, offset, maxLimit)", fieldName(memberTypeName));
            }
            else if (resolver.resolve(memberName) != null && resolver.resolve(memberName).getKind() == Kind.VARIANT)
            {
                builder.addStatement("$LRO.wrap(buffer, offset + $L, maxLimit)", fieldName(memberTypeName),
                    kindTypeName.isPrimitive() ? offset(memberName) : String.format("%s.sizeof()", enumFWName(kindTypeName)));
            }
            if (ofType != null || memberTypeName != null)
            {
                builder.addStatement("break");
            }
            builder.endControlFlow();
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            return builder.beginControlFlow("default:")
                          .addStatement("throw new IllegalStateException(\"Unrecognized kind: \" + $L)",
                              isStringType(ofType) && !kindTypeName.isPrimitive() ? "kind" : "kind()")
                          .endControlFlow()
                          .endControlFlow()
                          .addStatement("checkLimit(limit(), maxLimit)")
                          .addStatement("return this")
                          .build();
        }
    }

    private final class WrapMethodWithArrayGenerator extends MethodSpecGenerator
    {
        private final TypeName kindTypeName;
        private final AstType ofType;

        private WrapMethodWithArrayGenerator(
            TypeName kindTypeName,
            AstType ofType,
            TypeResolver resolver)
        {
            super(methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .addParameter(ParameterizedTypeName.get(resolver.resolveClass(AstType.ARRAY),
                        WildcardTypeName.subtypeOf(Object.class)), "array")
                .returns(thisName));
            this.kindTypeName = kindTypeName;
            this.ofType = ofType;
            if (isStringType(ofType) && !kindTypeName.isPrimitive())
            {
                final String enumFieldName = enumFWName(kindTypeName);
                builder.addStatement("final int fieldsOffset = array.fieldsOffset()")
                    .addStatement("super.wrap(buffer, fieldsOffset, maxLimit)")
                    .addStatement("final $L $L = $L.wrap(buffer, fieldsOffset, maxLimit)",
                        ((ClassName) kindTypeName).simpleName(), enumFieldName, enumRO(kindTypeName))
                    .addStatement("final int kindPadding = offset == fieldsOffset ? 0 : offset - fieldsOffset - $L.sizeof()",
                        enumFieldName)
                    .beginControlFlow("if (kind == null)")
                    .addStatement("kind = $L.get()", enumFieldName)
                    .endControlFlow()
                    .beginControlFlow("switch (kind())");
            }
        }

        public WrapMethodWithArrayGenerator addMember(
            Object kindValue,
            String memberName)
        {
            if (isStringType(ofType) && !kindTypeName.isPrimitive())
            {
                builder.beginControlFlow("case $L:", kindValue)
                    .addStatement("$LRO.wrap(buffer, $L.limit() + kindPadding, maxLimit)", memberName, enumFWName(kindTypeName))
                    .addStatement("break")
                    .endControlFlow();
            }
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            return builder.beginControlFlow("default:")
                .addStatement("throw new IllegalStateException(\"Unrecognized kind: \" + $L)",
                    isStringType(ofType) && !kindTypeName.isPrimitive() ? "kind" : "kind()")
                .endControlFlow()
                .endControlFlow()
                .addStatement("checkLimit(limit(), maxLimit)")
                .addStatement("return this")
                .build();
        }

        @Override
        public void mixin(
            TypeSpec.Builder builder)
        {
            if (isStringType(ofType) && !kindTypeName.isPrimitive())
            {
                super.mixin(builder);
            }
        }
    }

    private final class ToStringMethodGenerator extends MethodSpecGenerator
    {
        private final TypeName kindTypeName;
        private final AstType ofType;
        private final TypeResolver resolver;

        private ToStringMethodGenerator(
            TypeName kindTypeName,
            AstType ofType,
            TypeResolver resolver)
        {
            super(methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(String.class));
            this.kindTypeName = kindTypeName;
            this.ofType = ofType;
            this.resolver = resolver;
            if (!isListType(ofType) && !isArrayType(ofType) && !isMapType(ofType) && !isBoundedOctetsType(ofType))
            {
                builder.beginControlFlow("switch (kind())");
            }
        }

        public ToStringMethodGenerator addMember(
            String name,
            Object kindValue,
            String memberName,
            AstType memberType,
            TypeName memberTypeName,
            AstType mapKeyType)
        {
            if (!isListType(ofType) && !isArrayType(ofType) && !isMapType(ofType) && !isBoundedOctetsType(ofType))
            {
                builder.beginControlFlow("case $L:", kindTypeName.isPrimitive() && memberType != null ?
                    kind(memberName) : kindValue);
                if (memberType != null)
                {
                    if (isStringType(memberType))
                    {
                        builder.addStatement("return String.format(\"$L [$L=%s]\", $LRO.asString())", constant(baseName),
                            memberName, memberName);
                    }
                    else if (memberTypeName == null || memberTypeName.isPrimitive())
                    {
                        builder.addStatement("return String.format(\"$L [$L=%d]\", $L())", constant(baseName),
                            NUMBER_WORDS.get(memberName) == null ? memberName : NUMBER_WORDS.get(memberName), getAs(memberName));
                    }
                    else if (ofType == null)
                    {
                        if (mapKeyType == null)
                        {
                            builder.addStatement("return $LRO.toString()", fieldName(memberTypeName));
                        }
                        else
                        {
                            builder.addStatement("return $L().toString()", fieldName(memberTypeName));
                        }
                    }
                    else if (resolver.resolve(name) != null && resolver.resolve(name).getKind() == Kind.VARIANT)
                    {
                        builder.addStatement("return $LRO.toString()", fieldName(memberTypeName));
                    }
                    else
                    {
                        builder.addStatement("return String.format(\"$L [$L=%s]\", $L())", constant(baseName), memberName,
                            getAs(memberName));
                    }
                }
                builder.endControlFlow();
            }
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            if (isListType(ofType) || isArrayType(ofType) || isMapType(ofType) || isBoundedOctetsType(ofType))
            {
                builder.addStatement("return get().toString()");
            }
            else
            {
                builder.beginControlFlow("default:")
                    .addStatement("return String.format(\"$L [unknown]\")", constant(baseName))
                    .endControlFlow()
                    .endControlFlow();
            }
            return builder.build();
        }
    }

    private static final class StringOfTypeMethodsGenerator extends ClassSpecMixinGenerator
    {
        private StringOfTypeMethodsGenerator(
            ClassName thisType,
            TypeSpec.Builder builder,
            TypeName kindTypeName,
            AstType ofType)
        {
            super(thisType, builder);
            if (isStringType(ofType) && !kindTypeName.isPrimitive())
            {
                builder
                    .addMethod(methodBuilder("fieldSizeLength")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return get().fieldSizeLength()")
                        .build())
                    .addMethod(methodBuilder("asString")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(String.class)
                        .addStatement("return get().asString()")
                        .build())
                    .addMethod(methodBuilder("length")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return get().length()")
                        .build());
            }
        }
    }

    private static final class ListOfTypeMethodsGenerator extends ClassSpecMixinGenerator
    {
        private ListOfTypeMethodsGenerator(
            ClassName thisType,
            TypeSpec.Builder builder,
            TypeName kindTypeName,
            AstType ofType)
        {
            super(thisType, builder);
            if (isListType(ofType) && !kindTypeName.isPrimitive())
            {
                builder
                    .addMethod(methodBuilder("length")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return get().length()")
                        .build())
                    .addMethod(methodBuilder("fieldCount")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return get().fieldCount()")
                        .build())
                    .addMethod(methodBuilder("fields")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(DIRECT_BUFFER_TYPE)
                        .addStatement("return get().fields()")
                        .build());
            }
        }
    }

    private static final class ArrayOfTypeMethodsGenerator extends ClassSpecMixinGenerator
    {
        private ArrayOfTypeMethodsGenerator(
            ClassName thisType,
            TypeSpec.Builder builder,
            TypeName kindTypeName,
            TypeVariableName typeVarV,
            AstType ofType)
        {
            super(thisType, builder);
            if (isArrayType(ofType) && !kindTypeName.isPrimitive())
            {
                TypeName itemType = WildcardTypeName.supertypeOf(typeVarV);
                TypeName parameterizedConsumerType = ParameterizedTypeName.get(ClassName.get(Consumer.class), itemType);
                TypeName parameterizedPredicateType = ParameterizedTypeName.get(ClassName.get(Predicate.class), itemType);
                builder
                    .addMethod(methodBuilder("length")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return get().length()")
                        .build())
                    .addMethod(methodBuilder("fieldCount")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return get().fieldCount()")
                        .build())
                    .addMethod(methodBuilder("fieldsOffset")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return get().fieldsOffset()")
                        .build())
                    .addMethod(methodBuilder("maxLength")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return get().maxLength()")
                        .build())
                    .addMethod(methodBuilder("forEach")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(void.class)
                        .addParameter(parameterizedConsumerType, "consumer")
                        .addStatement("get().forEach(consumer)")
                        .build())
                    .addMethod(methodBuilder("anyMatch")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(boolean.class)
                        .addParameter(parameterizedPredicateType, "predicate")
                        .addStatement("return get().anyMatch(predicate)")
                        .build())
                    .addMethod(methodBuilder("matchFirst")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(typeVarV)
                        .addParameter(parameterizedPredicateType, "predicate")
                        .addStatement("return get().matchFirst(predicate)")
                        .build())
                    .addMethod(methodBuilder("isEmpty")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(boolean.class)
                        .addStatement("return get().isEmpty()")
                        .build())
                    .addMethod(methodBuilder("items")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(DIRECT_BUFFER_TYPE)
                        .addStatement("return get().items()")
                        .build())
                    .addMethod(methodBuilder("maxLength")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(void.class)
                        .addParameter(int.class, "maxLength")
                        .addStatement("get().maxLength(maxLength)")
                        .build());
            }
        }
    }

    private static final class OctetsOfTypeMethodsGenerator extends ClassSpecMixinGenerator
    {
        private OctetsOfTypeMethodsGenerator(
            ClassName thisType,
            ClassName flyweightType,
            TypeSpec.Builder builder,
            TypeName kindTypeName,
            AstType ofType)
        {
            super(thisType, builder);
            if (isBoundedOctetsType(ofType) && !kindTypeName.isPrimitive())
            {
                TypeVariableName typeVarT = TypeVariableName.get("T");
                TypeName visitorType = ParameterizedTypeName.get(flyweightType.nestedClass("Visitor"), typeVarT);
                builder
                    .addMethod(methodBuilder("get")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(typeVarT)
                        .addTypeVariable(typeVarT)
                        .addParameter(visitorType, "visitor")
                        .addStatement("return get().get(visitor)")
                        .build())
                    .addMethod(methodBuilder("value")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(DIRECT_BUFFER_TYPE)
                        .addStatement("return get().value()")
                        .build())
                    .addMethod(methodBuilder("length")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return get().length()")
                        .build());
            }
        }
    }

    private static final class KindAccessorGenerator extends ClassSpecMixinGenerator
    {
        private final TypeName kindTypeName;
        private final AstType ofType;

        private KindAccessorGenerator(
            ClassName thisType,
            TypeName kindTypeName,
            AstType ofType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            this.kindTypeName = kindTypeName;
            this.ofType = ofType;
        }

        @Override
        public TypeSpec.Builder build()
        {
            if (kindTypeName.isPrimitive())
            {
                builder.addMethod(
                    methodBuilder("kind")
                       .addModifiers(PUBLIC)
                       .returns(int.class)
                       .addStatement("return buffer().getByte(offset() + $L) & 0xFF", offset("kind"))
                       .build());
            }
            else
            {
                String enumFWName = ((ClassName) kindTypeName).simpleName();
                ClassName enumName = ClassName.bestGuess(enumFWName.substring(0, enumFWName.length() - 2));

                MethodSpec.Builder kindMethodBuilder = methodBuilder("kind")
                    .addModifiers(PUBLIC)
                    .returns(enumName);
                if (isStringType(ofType))
                {
                    builder.addField(FieldSpec.builder(enumName, "kind", PRIVATE).build());
                    kindMethodBuilder.addStatement("return kind");
                }
                else
                {
                    kindMethodBuilder.addStatement("return $L.get()", enumRO(kindTypeName));
                }
                builder.addMethod(kindMethodBuilder.build());
            }
            return super.build();
        }
    }

    private static final class MemberAccessorGenerator extends ClassSpecMixinGenerator
    {
        private final TypeName kindTypeName;
        private final AstType ofType;
        private final TypeResolver resolver;
        private final AstByteOrder byteOrder;

        private MemberAccessorGenerator(
            ClassName thisType,
            TypeName kindTypeName,
            AstType ofType,
            TypeSpec.Builder builder,
            TypeResolver resolver,
            AstByteOrder byteOrder)
        {
            super(thisType, builder);
            this.kindTypeName = kindTypeName;
            this.ofType = ofType;
            this.resolver = resolver;
            this.byteOrder = byteOrder;
        }

        public MemberAccessorGenerator addMember(
            String name,
            AstType memberType,
            TypeName memberTypeName,
            TypeName unsignedMemberTypeName,
            AstType mapKeyType,
            AstType mapValueType)
        {
            if (memberType == null)
            {
                return this;
            }
            CodeBlock.Builder codeBlock = CodeBlock.builder();
            if (memberTypeName != null && memberTypeName.isPrimitive())
            {
                if (memberType == AstType.INT24 || memberType == AstType.UINT24)
                {
                    return addInt24Member(name);
                }
                String getterName = String.format("get%s", TYPE_NAMES.get(memberTypeName));
                if (getterName == null)
                {
                    throw new IllegalStateException("member type not supported: " + memberTypeName);
                }

                String unsignedHex = "";
                if (unsignedMemberTypeName != null)
                {
                    if (memberTypeName.equals(TypeName.BYTE))
                    {
                        unsignedHex = " & 0xFF";
                    }
                    else if (memberTypeName.equals(TypeName.SHORT))
                    {
                        unsignedHex = " & 0xFFFF";
                    }
                    else if (memberTypeName.equals(TypeName.INT))
                    {
                        unsignedHex = " & 0xFFFF_FFFFL";
                    }
                }
                String byteOrderStatement = "";
                if (byteOrder == AstByteOrder.NETWORK)
                {
                    if (memberTypeName == TypeName.SHORT || memberTypeName == TypeName.INT || memberTypeName == TypeName.LONG)
                    {
                        byteOrderStatement = ", $T.BIG_ENDIAN";
                    }
                }
                String getStatement = String.format(kindTypeName.isPrimitive() ? "return buffer().$L(offset() + $L%s)$L" :
                            "return buffer().$L($L.limit()%s)$L", byteOrderStatement);
                if (kindTypeName.isPrimitive())
                {
                    if ("".equals(byteOrderStatement))
                    {
                        codeBlock.addStatement(getStatement, getterName, offset(name), unsignedHex);
                    }
                    else
                    {
                        codeBlock.addStatement(getStatement, getterName, offset(name), ByteOrder.class, unsignedHex);
                    }
                }
                else
                {
                    if ("".equals(byteOrderStatement))
                    {
                        codeBlock.addStatement(getStatement, getterName, enumRO(kindTypeName), unsignedHex);
                    }
                    else
                    {
                        codeBlock.addStatement(getStatement, getterName, enumRO(kindTypeName), ByteOrder.class, unsignedHex);
                    }
                }
            }
            else
            {
                if (mapKeyType != null)
                {
                    String memberName = fieldName(memberTypeName);
                    ClassName mapKeyTypeName = resolver.resolveClass(mapKeyType);
                    ClassName mapValueTypeName = resolver.resolveClass(mapValueType);
                    codeBlock.beginControlFlow("if ($LRO == null)", memberName)
                        .addStatement("$LRO = new $T<>(new $T(), new $T())",
                            memberName, memberTypeName, mapKeyTypeName, mapValueTypeName)
                        .endControlFlow()
                        .addStatement("return $LRO", memberName);
                }
                else if (memberTypeName != null && isStringType((ClassName) memberTypeName))
                {
                    codeBlock.addStatement("return $LRO", name);
                }
                else if (memberType.isDynamicType() && memberTypeName != null)
                {
                    codeBlock.addStatement("return $LRO", fieldName(memberTypeName));
                }
                else
                {
                    codeBlock.addStatement("return $L", value(name));
                }
            }

            TypeName returnType = returnType(name, memberTypeName, unsignedMemberTypeName, mapKeyType, mapValueType);

            if (!isNonPrimitiveType(ofType))
            {
                final String accessorName = memberType.isDynamicType() && memberTypeName != null ?
                    fieldName(memberTypeName) : name;
                builder.addMethod(methodBuilder(mapKeyType == null ? getAs(accessorName) : accessorName)
                    .addModifiers(PUBLIC)
                    .returns(returnType)
                    .addCode(codeBlock.build())
                    .build());
            }
            return this;
        }

        private TypeName returnType(
            String name,
            TypeName memberTypeName,
            TypeName unsignedMemberTypeName,
            AstType mapKeyType,
            AstType mapValueType)
        {
            TypeName returnType = TypeName.INT;
            if (memberTypeName != null)
            {
                if (!memberTypeName.equals(TypeName.BYTE) && !memberTypeName.equals(TypeName.SHORT))
                {
                    returnType = Objects.requireNonNullElse(unsignedMemberTypeName, mapKeyType == null ? memberTypeName :
                        ParameterizedTypeName.get((ClassName) memberTypeName, resolver.resolveClass(mapKeyType),
                            resolver.resolveClass(mapValueType)));
                }
            }
            return returnType;
        }

        private MemberAccessorGenerator addInt24Member(
            String name)
        {
            String offsetStatement = kindTypeName.isPrimitive() ? String.format("int offset = offset() + %s", offset(name)) :
                String.format("int offset = %s.limit()", enumRO(kindTypeName));
            builder.addMethod(methodBuilder(getAs(name))
                .addModifiers(PUBLIC)
                .returns(TypeName.INT)
                .addStatement(offsetStatement)
                .addStatement("int bits = (buffer().getByte(offset) & 0xff) << 16 | (buffer().getByte(offset + 1) & 0xff) << 8 " +
                    "| (buffer().getByte(offset + 2) & 0xff)")
                .beginControlFlow("if ($T.NATIVE_BYTE_ORDER != $T.BIG_ENDIAN)", BUFFER_UTIL_TYPE, ByteOrder.class)
                .addStatement("bits = (buffer().getByte(offset) & 0xff) | (buffer().getByte(offset + 1) & 0xff) << 8 | " +
                    "(buffer().getByte(offset + 2) & 0xff) << 16")
                .endControlFlow()
                .addStatement("return bits")
                .build());
            return this;
        }
    }

    private final class GetMethodGenerator extends MethodSpecGenerator
    {
        private final TypeName kindTypeName;
        private final AstType ofType;
        private final TypeName ofTypeName;
        private final TypeName unsignedOfType;
        private final TypeResolver resolver;

        private GetMethodGenerator(
            TypeName kindTypeName,
            AstType ofType,
            TypeName ofTypeName,
            TypeName unsignedOfType,
            TypeResolver resolver)
        {
            super(methodBuilder("get")
                .addModifiers(PUBLIC));
            this.kindTypeName = kindTypeName;
            this.ofType = ofType;
            this.ofTypeName = ofTypeName;
            this.unsignedOfType = unsignedOfType;
            this.resolver = resolver;
            builder.beginControlFlow("switch (kind())");
        }

        public GetMethodGenerator addMember(
            String name,
            Object kindValue,
            AstType memberType,
            TypeName memberTypeName)
        {
            if (ofType == null)
            {
                return this;
            }
            Object kind = kindTypeName.isPrimitive() ? kind(name) : kindValue;
            builder.beginControlFlow("case $L:", kind);
            if (isNonPrimitiveType(ofType))
            {
                builder.addStatement("return $LRO", name);
            }
            else
            {
                final String fieldName = memberType.isDynamicType() && memberTypeName != null ?
                    fieldName(memberTypeName) : name;
                if (memberTypeName == null || memberTypeName.isPrimitive())
                {
                    builder.addStatement("return $L()", getAs(fieldName));
                }
                else if (resolver.resolve(name) != null && resolver.resolve(name).getKind() == Kind.VARIANT)
                {
                    builder.addStatement("return $L().get()", getAs(fieldName));
                }
                else
                {
                    builder.addStatement("return $L().asString()", getAs(fieldName));
                }
            }
            builder.endControlFlow();
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            TypeName primitiveReturnType = ofTypeName.equals(TypeName.BYTE) || ofTypeName.equals(TypeName.SHORT) ||
                ofTypeName.equals(TypeName.INT) ? TypeName.INT : TypeName.LONG;
            TypeName returnType;

            if (isListType(ofType) || isStringType(ofType) || isBoundedOctetsType(ofType))
            {
                returnType = ofTypeName;
            }
            else if (isArrayType(ofType))
            {
                returnType = ParameterizedTypeName.get(resolver.resolveClass(ofType), typeVarV);
            }
            else if (isMapType(ofType))
            {
                returnType = ParameterizedTypeName.get(resolver.resolveClass(ofType), typeVarKV, typeVarVV);
            }
            else
            {
                returnType = Objects.requireNonNullElseGet(unsignedOfType, () -> ofTypeName.isPrimitive() ? primitiveReturnType :
                    ClassName.bestGuess("String"));
            }
            return builder.beginControlFlow("default:")
                .addStatement("throw new IllegalStateException(\"Unrecognized kind: \" + kind())")
                .endControlFlow()
                .endControlFlow()
                .returns(returnType)
                .build();
        }

        @Override
        public void mixin(
            TypeSpec.Builder builder)
        {
            if (ofTypeName != null)
            {
                super.mixin(builder);
            }
        }
    }

    private static final class BitMaskConstantGenerator extends ClassSpecMixinGenerator
    {
        private final TypeName ofTypeName;

        private BitMaskConstantGenerator(
            ClassName thisType,
            TypeName ofTypeName,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            this.ofTypeName = ofTypeName;
        }

        public BitMaskConstantGenerator addMember(
            String kindTypeName,
            AstType memberType,
            TypeName memberTypeName,
            TypeName unsignedMemberTypeName)
        {
            if (ofTypeName != null && unsignedMemberTypeName == null && memberTypeName != null && !memberType.isDynamicType())
            {
                boolean isTypeInt = INTEGER_TYPES.contains(ofTypeName);
                TypeName bitMaskType = isTypeInt ? TypeName.INT : TypeName.LONG;
                FieldSpec.Builder bitMaskField = FieldSpec.builder(bitMaskType, bitMask(kindTypeName), PRIVATE,
                    STATIC, FINAL);
                if (isTypeInt)
                {
                    if (BIT_MASK_INT.get(kindTypeName) != null)
                    {
                        bitMaskField.initializer("$L", BIT_MASK_INT.get(kindTypeName));
                        builder.addField(bitMaskField.build());
                    }
                }
                else
                {
                    if (BIT_MASK_LONG.get(kindTypeName) != null)
                    {
                        bitMaskField.initializer("$LL", BIT_MASK_LONG.get(kindTypeName));
                        builder.addField(bitMaskField.build());
                    }
                }
            }
            return this;
        }
    }

    private final class LimitMethodGenerator extends MethodSpecGenerator
    {
        private final TypeName kindTypeName;
        private final AstType ofType;
        private final TypeResolver resolver;

        private LimitMethodGenerator(
            TypeName kindTypeName,
            AstType ofType,
            TypeResolver resolver)
        {
            super(methodBuilder("limit")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(int.class));
            this.kindTypeName = kindTypeName;
            this.ofType = ofType;
            this.resolver = resolver;
            if (!isNonPrimitiveType(ofType))
            {
                builder.beginControlFlow("switch (kind())");
            }
        }

        public LimitMethodGenerator addMember(
            String name,
            Object kindValue,
            String memberName,
            TypeName memberTypeName,
            AstType mapKeyType)
        {
            if (!isNonPrimitiveType(ofType))
            {
                builder.beginControlFlow("case $L:", kindTypeName.isPrimitive() ? kind(memberName) : kindValue);

                if (memberTypeName == null)
                {
                    if (ofType != null)
                    {
                        if (kindTypeName.isPrimitive())
                        {
                            builder.addStatement("return offset()");
                        }
                        else
                        {
                            builder.addStatement("return $L.limit()", enumRO(kindTypeName));
                        }
                    }
                }
                else if (DIRECT_BUFFER_TYPE.equals(memberTypeName) || memberTypeName.isPrimitive())
                {
                    if (kindTypeName.isPrimitive())
                    {
                        builder.addStatement("return offset() + $L + $L", offset(memberName), size(memberName));
                    }
                    else
                    {
                        builder.addStatement("return $L.limit() + $L", enumRO(kindTypeName), size(memberName));
                    }
                }
                else if (ofType == null)
                {
                    if (mapKeyType == null)
                    {
                        builder.addStatement("return $LRO.limit()", fieldName(memberTypeName));
                    }
                    else
                    {
                        builder.addStatement("return $L().limit()", fieldName(memberTypeName));
                    }
                }
                else if (resolver.resolve(name) != null && resolver.resolve(name).getKind() == Kind.VARIANT)
                {
                    builder.addStatement("return $LRO.limit()", fieldName(memberTypeName));
                }
                else
                {
                    builder.addStatement("return $L().limit()", getAs(memberName));
                }
                builder.endControlFlow();
            }
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            if (isNonPrimitiveType(ofType))
            {
                return builder.addStatement("return get().limit()").build();
            }
            builder.beginControlFlow("default:");
            if (kindTypeName.isPrimitive())
            {
                builder.addStatement("return offset()");
            }
            else if (ofType == null)
            {
                builder.addStatement("return offset()");
            }
            else
            {
                builder.addStatement("return $L.limit()", enumRO(kindTypeName));
            }
            return builder.endControlFlow()
                          .endControlFlow()
                          .build();
        }
    }

    private final class MapOfTypeMethodsGenerator extends ClassSpecMixinGenerator
    {
        private MapOfTypeMethodsGenerator(
            ClassName thisType,
            AstType ofType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            if (isMapType(ofType))
            {
                TypeName parameterizedBiConsumerType = ParameterizedTypeName.get(ClassName.get(BiConsumer.class), typeVarKV,
                    typeVarVV);
                builder
                    .addMethod(methodBuilder("length")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return get().length()")
                        .build())
                    .addMethod(methodBuilder("fieldCount")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("return get().fieldCount()")
                        .build())
                    .addMethod(methodBuilder("forEach")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .addParameter(parameterizedBiConsumerType, "consumer")
                        .addStatement("get().forEach(consumer)")
                        .build())
                    .addMethod(methodBuilder("entries")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(DIRECT_BUFFER_TYPE)
                        .addStatement("return get().entries()")
                        .build());
            }
        }
    }

    private static final class BuilderClassGenerator extends ClassSpecGenerator
    {
        private final TypeSpec.Builder builder;
        private final KindMethodGenerator kindMethod;
        private final ItemMethodGenerator itemMethod;
        private final EntryMethodGenerator entryMethod;
        private final EntriesMethodGenerator entriesMethod;
        private final MaxKindMethodGenerator maxKindMethod;
        private final MinKindMethodGenerator minKindMethod;
        private final FieldMethodGenerator fieldMethod;
        private final FieldsMethodGenerator fieldsMethod;
        private final ConstructorGenerator constructor;
        private final SetAsFieldMethodGenerator setAsFieldMethod;
        private final SetAsFieldMethodForStringOfTypeGenerator setAsFieldMethodForStringOfType;
        private final SetAsFieldMethodForOctetsOfTypeGenerator setAsFieldMethodForOctetsOfType;
        private final SetWithSpecificKindMethodGenerator setWithSpecificKindMethod;
        private final MemberFieldGenerator memberField;
        private final WrapMethodGenerator wrapMethod;
        private final WrapMethodWithArrayGenerator wrapMethodWithArray;
        private final ResetMethodGenerator resetMethod;
        private final SizeOfMethodGenerator sizeOfMethod;
        private final RebuildMethodGenerator rebuildMethod;
        private final SetMethodGenerator setMethod;
        private final SetMethodWithBufferGenerator setMethodWithBuffer;
        private final SetMethodWithStringGenerator setMethodWithString;
        private final SetMethodWithByteArrayGenerator setMethodWithByteArray;
        private final BuildMethodGenerator buildMethod;
        private final SetList32FieldsMethodGenerator setList32FieldsMethod;
        private final ArrayFieldGenerator arrayField;
        private final TypeVariableName typeVarB;
        private final TypeVariableName typeVarV;
        private final TypeVariableName typeVarKB;
        private final TypeVariableName typeVarKV;
        private final TypeVariableName typeVarVB;
        private final TypeVariableName typeVarVV;

        private BuilderClassGenerator(
            ClassName thisVariantType,
            ClassName flyweightType,
            TypeName kindTypeName,
            AstType ofType,
            TypeName ofTypeName,
            TypeName unsignedOfTypeName,
            TypeResolver resolver,
            TypeVariableName typeVarO,
            AstByteOrder byteOrder)
        {
            this(thisVariantType.nestedClass("Builder"), flyweightType, flyweightType.nestedClass("Builder"), thisVariantType,
                kindTypeName, ofType, ofTypeName, unsignedOfTypeName, resolver, typeVarO, byteOrder);
        }

        private BuilderClassGenerator(
            ClassName thisVariantBuilderType,
            ClassName flyweightType,
            ClassName flyweightBuilderRawType,
            ClassName thisVariantType,
            TypeName kindTypeName,
            AstType ofType,
            TypeName ofTypeName,
            TypeName unsignedOfTypeName,
            TypeResolver resolver,
            TypeVariableName typeVarO,
            AstByteOrder byteOrder)
        {
            super(thisVariantBuilderType);

            this.typeVarV = TypeVariableName.get("V", flyweightType);
            this.typeVarB = TypeVariableName.get("B", ParameterizedTypeName.get(flyweightBuilderRawType, typeVarV));
            this.typeVarKV = TypeVariableName.get("K", flyweightType);
            this.typeVarKB = TypeVariableName.get("KB", ParameterizedTypeName.get(flyweightBuilderRawType, typeVarKV));
            this.typeVarVV = TypeVariableName.get("V", flyweightType);
            this.typeVarVB = TypeVariableName.get("VB", ParameterizedTypeName.get(flyweightBuilderRawType, typeVarVV));
            this.builder = classBuilder(thisVariantBuilderType.simpleName())
                .addModifiers(PUBLIC, STATIC, FINAL);
            builder(flyweightBuilderRawType, thisVariantType, kindTypeName, ofType, resolver);

            this.kindMethod = new KindMethodGenerator(kindTypeName, ofType);
            this.maxKindMethod = new MaxKindMethodGenerator(kindTypeName, ofType);
            this.minKindMethod = new MinKindMethodGenerator(kindTypeName, ofType);
            this.itemMethod = new ItemMethodGenerator(thisVariantType, builder, ofType);
            this.entryMethod = new EntryMethodGenerator(ofType);
            this.entriesMethod = new EntriesMethodGenerator(ofType);
            this.fieldMethod = new FieldMethodGenerator(flyweightBuilderRawType, ofType);
            this.fieldsMethod = new FieldsMethodGenerator(thisVariantBuilderType, ofType, flyweightBuilderRawType, builder);
            this.wrapMethod = new WrapMethodGenerator(ofType, kindTypeName);
            this.wrapMethodWithArray = new WrapMethodWithArrayGenerator(ofType, kindTypeName, resolver);
            this.resetMethod = new ResetMethodGenerator(ofType, kindTypeName, resolver);
            this.sizeOfMethod = new SizeOfMethodGenerator(thisVariantType, kindTypeName, ofType, builder);
            this.rebuildMethod = new RebuildMethodGenerator(thisVariantType, kindTypeName, ofType, ofTypeName, builder);
            this.setAsFieldMethod = new SetAsFieldMethodGenerator(thisVariantBuilderType, kindTypeName, ofType, ofTypeName,
                builder, resolver, byteOrder);
            this.setAsFieldMethodForStringOfType = new SetAsFieldMethodForStringOfTypeGenerator(thisVariantBuilderType,
                kindTypeName, ofType, ofTypeName, builder);
            this.setAsFieldMethodForOctetsOfType = new SetAsFieldMethodForOctetsOfTypeGenerator(thisVariantBuilderType,
                kindTypeName, ofType, builder);
            this.setWithSpecificKindMethod = new SetWithSpecificKindMethodGenerator(kindTypeName, ofType, resolver);
            this.memberField = new MemberFieldGenerator(thisVariantBuilderType, kindTypeName, typeVarB, typeVarV, typeVarKB,
                typeVarKV, typeVarVB, typeVarVV, ofType, builder, resolver, byteOrder);
            this.constructor = new ConstructorGenerator(thisVariantType, ofType, typeVarB, typeVarV, typeVarKB, typeVarKV,
                typeVarVB, typeVarVV, byteOrder);
            this.setMethod = new SetMethodGenerator(ofType, ofTypeName, unsignedOfTypeName, kindTypeName, resolver);
            this.setMethodWithBuffer = new SetMethodWithBufferGenerator(kindTypeName, ofType);
            this.setMethodWithString = new SetMethodWithStringGenerator(kindTypeName, ofType);
            this.setMethodWithByteArray = new SetMethodWithByteArrayGenerator(kindTypeName, ofType);
            this.buildMethod = new BuildMethodGenerator(kindTypeName, thisVariantType, ofType);
            this.setList32FieldsMethod = new SetList32FieldsMethodGenerator(ofType);
            this.arrayField = new ArrayFieldGenerator(thisVariantType, flyweightBuilderRawType, kindTypeName, ofType, resolver,
                builder);
        }

        private void builder(
            ClassName flyweightBuilderRawType,
            ClassName thisVariantType,
            TypeName kindTypeName,
            AstType ofType,
            TypeResolver resolver)
        {
            if ((isStringType(ofType) || isListType(ofType) || isBoundedOctetsType(ofType)) && !kindTypeName.isPrimitive())
            {
                ClassName ofTypeClassBuilderName = resolver.resolveClass(ofType).nestedClass("Builder");
                builder.superclass(ParameterizedTypeName.get(ofTypeClassBuilderName, thisVariantType))
                    .addModifiers(PUBLIC, FINAL);
            }
            else if (isArrayType(ofType))
            {
                ClassName ofTypeBuilderName = resolver.resolveClass(ofType).nestedClass("Builder");
                ParameterizedTypeName parameterizedVariantType = ParameterizedTypeName.get(thisVariantType, typeVarV);
                TypeName parameterizedSuperType = ParameterizedTypeName.get(ofTypeBuilderName, parameterizedVariantType,
                    typeVarB, typeVarV);
                builder.addTypeVariable(typeVarB)
                    .addTypeVariable(typeVarV)
                    .superclass(parameterizedSuperType);
            }
            else if (isMapType(ofType))
            {
                ClassName mapBuilderType = resolver.resolveClass(ofType).nestedClass("Builder");
                TypeName parameterizedVariantType = ParameterizedTypeName.get(thisVariantType, typeVarKV, typeVarVV);
                TypeName parameterizedSuperType = ParameterizedTypeName.get(mapBuilderType, parameterizedVariantType,
                    typeVarKV, typeVarVV, typeVarKB, typeVarVB);
                builder.addTypeVariable(typeVarKV)
                    .addTypeVariable(typeVarVV)
                    .addTypeVariable(typeVarKB)
                    .addTypeVariable(typeVarVB)
                    .superclass(parameterizedSuperType);
            }
            else
            {
                builder.superclass(ParameterizedTypeName.get(flyweightBuilderRawType, thisVariantType));
            }
        }

        private void addMember(
            Object kindValue,
            String memberName,
            AstType memberType,
            TypeName memberTypeName,
            TypeName unsignedMemberTypeName,
            AstType mapKeyType,
            AstType mapValueType)
        {
            maxKindMethod.addMember(memberName);
            minKindMethod.addMember(memberName, memberTypeName, unsignedMemberTypeName);
            itemMethod.addMember(memberType);
            entryMethod.addMember(memberType);
            entriesMethod.addMember(memberType);
            fieldMethod.addMember(memberType);
            fieldsMethod.addMember(memberType);
            wrapMethod.addMember(memberType);
            setAsFieldMethod.addMember(kindValue, memberName, memberType, memberTypeName, unsignedMemberTypeName, mapKeyType,
                mapValueType);
            setAsFieldMethodForStringOfType.addMember(memberName, memberType, memberTypeName);
            setAsFieldMethodForOctetsOfType.addMember(memberName, memberType, memberTypeName);
            setWithSpecificKindMethod.addMember(kindValue, memberName);
            memberField.addMember(memberName, memberType, memberTypeName, mapKeyType, mapValueType);
            constructor.addMember(memberName, memberType, memberTypeName);
            setMethod.addMember(memberName, memberType, memberTypeName, unsignedMemberTypeName);
            setMethodWithBuffer.addMember(memberName, memberTypeName, unsignedMemberTypeName);
            setMethodWithString.addMember(memberName, memberTypeName, unsignedMemberTypeName);
            setMethodWithByteArray.addMember(memberName, memberTypeName, unsignedMemberTypeName);
            buildMethod.addMember(memberType);
            setList32FieldsMethod.addMember(memberType);
        }

        @Override
        public TypeSpec generate()
        {
            maxKindMethod.mixin(builder);
            itemMethod.build();
            entryMethod.mixin(builder);
            entriesMethod.mixin(builder);
            fieldMethod.mixin(builder);
            fieldsMethod.build();
            setAsFieldMethod.build();
            setAsFieldMethodForStringOfType.build();
            setAsFieldMethodForOctetsOfType.build();
            setWithSpecificKindMethod.mixin(builder);
            memberField.build();
            setMethod.mixin(builder);
            setMethodWithBuffer.mixin(builder);
            setMethodWithString.mixin(builder);
            setMethodWithByteArray.mixin(builder);
            wrapMethod.mixin(builder);
            wrapMethodWithArray.mixin(builder);
            resetMethod.mixin(builder);
            sizeOfMethod.build();
            rebuildMethod.build();
            buildMethod.mixin(builder);
            setList32FieldsMethod.mixin(builder);
            minKindMethod.mixin(builder);
            kindMethod.mixin(builder);
            arrayField.build();
            return builder.addMethod(constructor.generate())
                .build();
        }

        private static final class ConstructorGenerator extends MethodSpecGenerator
        {
            private final ClassName thisVariantType;
            private final AstType ofType;
            private final AstByteOrder byteOrder;

            private ConstructorGenerator(
                ClassName thisVariantType,
                AstType ofType,
                TypeVariableName typeVarB,
                TypeVariableName typeVarV,
                TypeVariableName typeVarKB,
                TypeVariableName typeVarKV,
                TypeVariableName typeVarVB,
                TypeVariableName typeVarVV,
                AstByteOrder byteOrder)
            {
                super(constructorBuilder());
                this.thisVariantType = thisVariantType;
                this.ofType = ofType;
                this.byteOrder = byteOrder;
                if (isArrayType(ofType))
                {
                    builder.addParameter(typeVarB, "itemRW")
                        .addParameter(typeVarV, "itemRO")
                        .addStatement("super(new $T<>(itemRO))", thisVariantType);
                }
                else if (isMapType(ofType))
                {
                    builder.addParameter(typeVarKV, "keyRO")
                        .addParameter(typeVarVV, "valueRO")
                        .addParameter(typeVarKB, "keyRW")
                        .addParameter(typeVarVB, "valueRW")
                        .addStatement("super(new $T<>(keyRO, valueRO))", thisVariantType);
                }
            }

            public ConstructorGenerator addMember(
                String memberName,
                AstType memberType,
                TypeName memberTypeName)
            {
                if (isArrayType(ofType))
                {
                    if (byteOrder == NATIVE || memberType.equals(AstType.ARRAY8))
                    {
                        builder.addStatement("$LRW = new $T.Builder<>(itemRW, itemRO)", memberName, memberTypeName);
                    }
                    else
                    {
                        builder.addStatement("$LRW = new $T.Builder<>(itemRW, itemRO, $T.BIG_ENDIAN)", memberName,
                            memberTypeName, ByteOrder.class);
                    }
                }
                else if (isMapType(ofType))
                {
                    if (byteOrder == NATIVE || memberType.equals(AstType.MAP8))
                    {
                        builder.addStatement("$LRW = new $T.Builder<>(keyRO, valueRO, keyRW, valueRW)", memberName,
                            memberTypeName);
                    }
                    else
                    {
                        builder.addStatement("$LRW = new $T.Builder<>(keyRO, valueRO, keyRW, valueRW, $T.BIG_ENDIAN)", memberName,
                            memberTypeName, ByteOrder.class);
                    }
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                if (!isArrayType(ofType) && !isMapType(ofType))
                {
                    return builder
                        .addModifiers(PUBLIC)
                        .addStatement("super(new $T())", thisVariantType)
                        .build();
                }
                return builder.addModifiers(PUBLIC).build();
            }
        }

        private final class SetMethodGenerator extends MethodSpecGenerator
        {
            private final Set<TypeWidth> kindTypeSet = new TreeSet<>();
            private final AstType ofType;
            private final TypeName ofTypeName;
            private final TypeName unsignedOfType;
            private final TypeResolver resolver;
            private final TypeName kindTypeName;
            boolean isList0Type = false;
            boolean isCaseVariantType = false;

            private SetMethodGenerator(
                AstType ofType,
                TypeName ofTypeName,
                TypeName unsignedOfType,
                TypeName kindTypeName,
                TypeResolver resolver)
            {
                super(methodBuilder("set")
                    .addModifiers(PUBLIC)
                    .returns(thisName));
                this.ofType = ofType;
                this.ofTypeName = ofTypeName;
                this.unsignedOfType = unsignedOfType;
                this.kindTypeName = kindTypeName;
                this.resolver = resolver;
                if ((isStringType(ofType) || isBoundedOctetsType(ofType)) && !kindTypeName.isPrimitive())
                {
                    builder.addAnnotation(Override.class);
                }
            }

            public SetMethodGenerator addMember(
                String kindTypeName,
                AstType memberType,
                TypeName kindType,
                TypeName unsignedKindType)
            {
                if (ofType == null)
                {
                    return this;
                }
                if (Character.isDigit(kindTypeName.charAt(0)))
                {
                    String constantDigit = kindTypeName.substring(0, 1);
                    TypeWidth currentType = new TypeWidth(kindType, unsignedKindType, kindTypeName,
                        "0".equals(constantDigit) ? 0 : 8, Integer.parseInt(constantDigit));
                    kindTypeSet.add(currentType);
                }
                else if (resolver.resolve(kindTypeName) != null && resolver.resolve(kindTypeName).getKind() == Kind.VARIANT)
                {
                    AstVariantNode node = (AstVariantNode) resolver.resolve(kindTypeName);
                    String typeSize = node.of().toString().replaceAll("\\D+", "");
                    int memberWidth = !typeSize.isEmpty() ? Integer.parseInt(typeSize) : 8;
                    kindTypeSet.add(new TypeWidth(kindType, unsignedKindType, fieldName(kindType), memberWidth,
                        Integer.MAX_VALUE));
                    isCaseVariantType = true;
                }
                else
                {
                    if (AstType.LIST0.equals(memberType))
                    {
                        isList0Type = true;
                    }
                    String typeSize = kindTypeName.replaceAll("\\D+", "");
                    int memberWidth = !typeSize.isEmpty() ? Integer.parseInt(typeSize) : 8;
                    kindTypeSet.add(new TypeWidth(kindType, unsignedKindType, kindTypeName, memberWidth, Integer.MAX_VALUE));
                }
                return this;
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (ofTypeName != null && !isMapType(ofType) && !isArrayType(ofType))
                {
                    super.mixin(builder);
                }
            }

            @Override
            public MethodSpec generate()
            {
                boolean hasConstant = false;
                boolean isParameterTypeLong = TypeName.LONG.equals(ofTypeName) || TypeName.LONG.equals(unsignedOfType);
                addVariableDefinitions();

                builder.beginControlFlow("switch (highestByteIndex)");
                int lastCaseSet = -1;
                for (TypeWidth type : kindTypeSet)
                {
                    int width = type.width();
                    switch (width)
                    {
                    case 8:
                        hasConstant = addCase8(type, isParameterTypeLong, hasConstant);
                        lastCaseSet = 0;
                        break;
                    case 16:
                        hasConstant = addCase16(type, hasConstant, isParameterTypeLong, lastCaseSet);
                        lastCaseSet = 1;
                        break;
                    case 32:
                        hasConstant = addCase32(type, hasConstant, isParameterTypeLong, lastCaseSet);
                        lastCaseSet = 3;
                        break;
                    case 64:
                        hasConstant = addCase64(type, hasConstant, isParameterTypeLong, lastCaseSet);
                        break;
                    }
                }

                if (ofTypeName.isPrimitive() && !isCaseVariantType)
                {
                    if (unsignedOfType == null)
                    {
                        addSignedNegativeIntBlock();
                    }
                    else
                    {
                        addUnsignedIntZeroCase();
                    }
                }

                if (isList0Type)
                {
                    builder.beginControlFlow("case 8:")
                        .addStatement("setAsList0(list)")
                        .addStatement("break")
                        .endControlFlow();
                }

                String parameterName = isListType(ofType) ? "list" : "value";
                builder.beginControlFlow("default:")
                       .addStatement("throw new IllegalArgumentException(\"Illegal $L: \" + $L)", parameterName,
                           parameterName)
                       .endControlFlow()
                       .endControlFlow();
                TypeName parameterType = Objects.requireNonNullElseGet(unsignedOfType, () -> ofTypeName.isPrimitive() ?
                    ofTypeName.equals(TypeName.LONG) ? TypeName.LONG : TypeName.INT : ofTypeName);
                return builder.addParameter(parameterType, isListType(ofType) ? "list" : "value")
                              .addStatement("return this")
                              .build();
            }

            private boolean addCase8(
                TypeWidth type,
                boolean isParameterTypeLong,
                boolean hasConstant)
            {
                if (type.value() != Integer.MAX_VALUE)
                {
                    builder.beginControlFlow("case 0:")
                        .beginControlFlow("switch ($Lvalue)", isParameterTypeLong ? "(int) " : "")
                        .beginControlFlow("case $L:", type.value())
                        .addStatement("$L()", setAs(type.kindTypeName()))
                        .addStatement("break")
                        .endControlFlow();
                    hasConstant = true;
                }
                else
                {
                    if (hasConstant)
                    {
                        addDefaultCase(type, isParameterTypeLong);
                        hasConstant = false;
                    }
                    else
                    {
                        builder.beginControlFlow("case 0:");
                        if (isStringType(ofType) || isBoundedOctetsType(ofType))
                        {
                            builder.endControlFlow()
                                .beginControlFlow("case 4:");
                        }
                        if (isListType(ofType))
                        {
                            builder.addStatement("$L(list)", setAs(type.kindTypeName()));
                        }
                        else if (isStringType(ofType) && !kindTypeName.isPrimitive())
                        {
                            builder.addStatement("$L(value)", setAs(type.kindTypeName()));
                        }
                        else
                        {
                            builder.addStatement(String.format("$L(%svalue)", isParameterTypeLong ? "(int) " : ""),
                                setAs(type.kindTypeName()));
                        }
                        builder.addStatement("break")
                            .endControlFlow();
                    }
                }
                return hasConstant;
            }

            private boolean addCase16(
                TypeWidth type,
                boolean hasConstant,
                boolean isParameterTypeLong,
                int lastCaseSet)
            {
                if (hasConstant)
                {
                    addDefaultCase(type, isParameterTypeLong);
                    hasConstant = false;
                }
                if (lastCaseSet < 0)
                {
                    builder.beginControlFlow("case 0:")
                        .endControlFlow();
                }
                builder.beginControlFlow("case 1:");
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    builder.addStatement("$L(value)", setAs(type.kindTypeName()));
                }
                else
                {
                    builder.addStatement(String.format("$L(%svalue)", isParameterTypeLong ? "(int) " : ""),
                        setAs(type.kindTypeName()));
                }
                builder.addStatement("break")
                    .endControlFlow();
                return hasConstant;
            }

            private boolean addCase32(
                TypeWidth type,
                boolean hasConstant,
                boolean isParameterTypeLong,
                int lastCaseSet)
            {
                if (hasConstant)
                {
                    addDefaultCase(type, isParameterTypeLong);
                    hasConstant = false;
                }
                if (lastCaseSet < 0)
                {
                    builder.beginControlFlow("case 0:")
                        .endControlFlow();
                }
                if (lastCaseSet < 1)
                {
                    builder.beginControlFlow("case 1:")
                        .endControlFlow();
                }
                builder.beginControlFlow("case 2:")
                    .endControlFlow()
                    .beginControlFlow("case 3:");
                if (isListType(ofType))
                {
                    builder.addStatement("$L(list)", setAs(type.kindTypeName()));
                }
                else if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    builder.addStatement("$L(value)", setAs(type.kindTypeName()));
                }
                else
                {
                    builder.addStatement(String.format("$L(%svalue)",
                        ofTypeName.equals(TypeName.LONG) ? type.unsignedKindType() == null ? "(int) " : "" : ""),
                        setAs(type.kindTypeName()));
                }
                builder.addStatement("break")
                    .endControlFlow();
                return hasConstant;
            }

            private boolean addCase64(
                TypeWidth type,
                boolean hasConstant,
                boolean isParameterTypeLong,
                int lastCaseSet)
            {
                if (hasConstant)
                {
                    addDefaultCase(type, isParameterTypeLong);
                    hasConstant = false;
                }
                if (lastCaseSet < 3)
                {
                    for (int i = lastCaseSet + 1; i <= 3; i++)
                    {
                        builder.beginControlFlow("case $L:", i)
                            .endControlFlow();
                    }
                }
                builder.beginControlFlow("case 4:")
                    .endControlFlow()
                    .beginControlFlow("case 5:")
                    .endControlFlow()
                    .beginControlFlow("case 6:")
                    .endControlFlow();
                if (!ofTypeName.isPrimitive() || isCaseVariantType)
                {
                    builder.beginControlFlow("case 7:")
                        .endControlFlow()
                        .beginControlFlow("case 8:")
                        .addStatement("$L(value)", setAs(type.kindTypeName()))
                        .addStatement("break")
                        .endControlFlow();
                }
                else
                {
                    builder.beginControlFlow("case 7:")
                        .addStatement("$L(value)", setAs(type.kindTypeName()))
                        .addStatement("break")
                        .endControlFlow();
                }
                return hasConstant;
            }

            private void addDefaultCase(
                TypeWidth type,
                boolean isParameterTypeLong)
            {
                builder.beginControlFlow("default:")
                       .addStatement("$L($Lvalue)", setAs(type.kindTypeName()), isParameterTypeLong ? "(int) " : "")
                       .addStatement("break")
                       .endControlFlow()
                       .endControlFlow()
                       .addStatement("break")
                       .endControlFlow();
            }

            private void addVariableDefinitions()
            {
                if (isListType(ofType))
                {
                    builder.addStatement("int length = Math.max(list.length(), list.fieldCount())")
                        .addStatement("int highestByteIndex = Long.numberOfTrailingZeros(Long.highestOneBit(length)) >> 3");
                }
                else if (!ofTypeName.isPrimitive())
                {
                    builder.addStatement("int length = value.length()")
                        .addStatement("int highestByteIndex = Integer.numberOfTrailingZeros(Integer.highestOneBit(length)) >> 3");
                }
                else
                {
                    if (unsignedOfType == null)
                    {
                        TypeName className = ofTypeName.equals(TypeName.LONG) ? TypeName.LONG : TypeName.INT;
                        builder.addStatement("int highestByteIndex = ($L.numberOfTrailingZeros($L.highestOneBit(value)) " +
                            "+ 1)  >> 3", CLASS_NAMES.get(className), CLASS_NAMES.get(className));
                    }
                    else
                    {
                        builder.addStatement("int highestByteIndex = $L.numberOfTrailingZeros($L.highestOneBit(value)) >> 3",
                            CLASS_NAMES.get(unsignedOfType), CLASS_NAMES.get(unsignedOfType));
                    }
                }
            }

            private void addUnsignedIntZeroCase()
            {
                TypeWidth typeZero = kindTypeSet.iterator().next();
                builder.beginControlFlow(String.format("case %s:", unsignedOfType.equals(TypeName.LONG) ? "8" : "4"));
                if (typeZero.width() == 0)
                {
                    builder.addStatement("$L()", setAs(typeZero.kindTypeName()));
                }
                else
                {
                    builder.addStatement("$L(0)", setAs(typeZero.kindTypeName()));
                }
                builder.addStatement("break")
                    .endControlFlow();
            }

            private void addSignedNegativeIntBlock()
            {
                builder.beginControlFlow(String.format("case %s:", ofTypeName.equals(TypeName.LONG) ? "8" : "4"));
                Iterator<TypeWidth> iterator = kindTypeSet.iterator();
                int i = 0;
                while (iterator.hasNext())
                {
                    TypeWidth currentType = iterator.next();
                    String kindTypeName = currentType.kindTypeName();
                    if (i == 0)
                    {
                        if (currentType.width() == 0)
                        {
                            builder.beginControlFlow("if (value == 0)")
                                   .addStatement("$L()", setAs(kindTypeName))
                                   .endControlFlow();
                        }
                        else
                        {
                            if (iterator.hasNext() || currentType.kindType().equals(TypeName.BYTE) ||
                                currentType.kindType().equals(TypeName.SHORT))
                            {
                                builder.beginControlFlow("if ((value & $L) == $L || value == 0)", bitMask(kindTypeName),
                                    bitMask(kindTypeName))
                                    .addStatement(String.format("$L(%svalue)",
                                        currentType.kindType().equals(TypeName.LONG) ? "" :
                                            ofTypeName.equals(TypeName.LONG) ? "(int) " : ""), setAs(currentType.kindTypeName()))
                                    .endControlFlow();
                            }
                            else
                            {
                                builder.addStatement(String.format("$L(%svalue)",
                                    currentType.kindType().equals(TypeName.LONG) ? "" :
                                        ofTypeName.equals(TypeName.LONG) ? "(int) " : ""), setAs(currentType.kindTypeName()));
                            }
                        }

                    }
                    else if (!iterator.hasNext() && currentType.value() == Integer.MAX_VALUE)
                    {
                        builder.beginControlFlow("else")
                               .addStatement(String.format("$L(%svalue)",
                                   currentType.kindType().equals(TypeName.LONG) ? "" :
                                       ofTypeName.equals(TypeName.LONG) ? "(int) " : ""), setAs(kindTypeName))
                               .endControlFlow();
                    }
                    else if (currentType.value() == Integer.MAX_VALUE)
                    {
                        builder.beginControlFlow("else if ((value & $L) == $L)",
                            bitMask(kindTypeName), bitMask(kindTypeName))
                               .addStatement(String.format("$L(%svalue)", ofTypeName.equals(TypeName.LONG) ? "(int) " : ""),
                                   setAs(kindTypeName))
                               .endControlFlow();

                    }
                    i++;
                }
                builder.addStatement("break");
                builder.endControlFlow();
            }
        }

        private final class SetMethodWithBufferGenerator extends MethodSpecGenerator
        {
            private final Set<TypeWidth> kindTypeSet = new TreeSet<>();
            private final AstType ofType;
            private final TypeName kindType;

            private SetMethodWithBufferGenerator(
                TypeName kindTypeName,
                AstType ofType)
            {
                super(methodBuilder("set")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(thisName));
                this.ofType = ofType;
                this.kindType = kindTypeName;
            }

            public SetMethodWithBufferGenerator addMember(
                String kindTypeName,
                TypeName kindType,
                TypeName unsignedKindType)
            {
                if ((isStringType(ofType) || isBoundedOctetsType(ofType))  && !kindType.isPrimitive())
                {
                    if (Character.isDigit(kindTypeName.charAt(0)))
                    {
                        String constantDigit = kindTypeName.substring(0, 1);
                        TypeWidth currentType = new TypeWidth(kindType, unsignedKindType, kindTypeName,
                            "0".equals(constantDigit) ? 0 : 8, Integer.parseInt(constantDigit));
                        kindTypeSet.add(currentType);
                    }
                    else
                    {
                        String typeSize = kindTypeName.replaceAll("\\D+", "");
                        int memberWidth = !typeSize.isEmpty() ? Integer.parseInt(typeSize) : 8;
                        kindTypeSet.add(new TypeWidth(kindType, unsignedKindType, kindTypeName, memberWidth, Integer.MAX_VALUE));
                    }
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                builder.addParameter(DIRECT_BUFFER_TYPE, isStringType(ofType) ? "srcBuffer" : "value")
                    .addParameter(int.class, isStringType(ofType) ? "srcOffset" : "offset")
                    .addParameter(int.class, "length")
                    .addStatement("int highestByteIndex = Integer.numberOfTrailingZeros(Integer.highestOneBit(length)) >> 3")
                    .beginControlFlow("switch (highestByteIndex)");
                int lastCaseSet = -1;
                final String setAsStatement = isStringType(ofType) ? "$L(srcBuffer, srcOffset, length)" :
                    "$L(value, offset, length)";
                for (TypeWidth type : kindTypeSet)
                {
                    int width = type.width();
                    switch (width)
                    {
                    case 8:
                        builder.beginControlFlow("case 0:");
                        if (isStringType(ofType) || isBoundedOctetsType(ofType))
                        {
                            builder.endControlFlow()
                                .beginControlFlow("case 4:");
                        }
                        builder.addStatement(setAsStatement, setAs(type.kindTypeName()))
                            .addStatement("break")
                            .endControlFlow();
                        lastCaseSet = 0;
                        break;
                    case 16:
                        if (lastCaseSet < 0)
                        {
                            builder.beginControlFlow("case 0:")
                                .endControlFlow();
                        }
                        builder.beginControlFlow("case 1:")
                            .addStatement(setAsStatement, setAs(type.kindTypeName()))
                            .addStatement("break")
                            .endControlFlow();
                        lastCaseSet = 1;
                        break;
                    case 32:
                        if (lastCaseSet < 0)
                        {
                            builder.beginControlFlow("case 0:")
                                .endControlFlow();
                        }
                        if (lastCaseSet == 0)
                        {
                            builder.beginControlFlow("case 1:")
                                .endControlFlow();
                        }
                        builder.beginControlFlow("case 2:")
                            .endControlFlow()
                            .beginControlFlow("case 3:")
                            .addStatement(setAsStatement, setAs(type.kindTypeName()))
                            .addStatement("break")
                            .endControlFlow();
                        break;
                    }
                }
                final String exception = String.format("throw new IllegalArgumentException(\"Illegal %s: \" + %s)",
                    isStringType(ofType) ? "length" : "value", isStringType(ofType) ? "length" : "value");
                builder.beginControlFlow("default:")
                    .addStatement(exception)
                    .endControlFlow()
                    .endControlFlow();
                return builder.addStatement("return this").build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if ((isStringType(ofType) || isBoundedOctetsType(ofType)) && !kindType.isPrimitive())
                {
                    super.mixin(builder);
                }
            }
        }

        private final class SetMethodWithStringGenerator extends MethodSpecGenerator
        {
            private final Set<TypeWidth> kindTypeSet = new TreeSet<>();
            private final AstType ofType;
            private final TypeName kindType;

            private SetMethodWithStringGenerator(
                TypeName kindTypeName,
                AstType ofType)
            {
                super(methodBuilder("set")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(thisName));
                this.ofType = ofType;
                this.kindType = kindTypeName;
            }

            public SetMethodWithStringGenerator addMember(
                String kindTypeName,
                TypeName kindType,
                TypeName unsignedKindType)
            {
                if (isStringType(ofType) && !kindType.isPrimitive())
                {
                    if (Character.isDigit(kindTypeName.charAt(0)))
                    {
                        String constantDigit = kindTypeName.substring(0, 1);
                        TypeWidth currentType = new TypeWidth(kindType, unsignedKindType, kindTypeName,
                            "0".equals(constantDigit) ? 0 : 8, Integer.parseInt(constantDigit));
                        kindTypeSet.add(currentType);
                    }
                    else
                    {
                        String typeSize = kindTypeName.replaceAll("\\D+", "");
                        int memberWidth = !typeSize.isEmpty() ? Integer.parseInt(typeSize) : 8;
                        kindTypeSet.add(new TypeWidth(kindType, unsignedKindType, kindTypeName, memberWidth, Integer.MAX_VALUE));
                    }
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                builder.addParameter(String.class, "value")
                    .addParameter(Charset.class, "charset")
                    .addStatement("int length = value.length()")
                    .addStatement("int highestByteIndex = Integer.numberOfTrailingZeros(Integer.highestOneBit(length)) >> 3")
                    .beginControlFlow("switch (highestByteIndex)");
                int lastCaseSet = -1;
                for (TypeWidth type : kindTypeSet)
                {
                    int width = type.width();
                    switch (width)
                    {
                    case 8:
                        builder.beginControlFlow("case 0:");
                        if (isStringType(ofType))
                        {
                            builder.endControlFlow()
                                .beginControlFlow("case 4:");
                        }
                        builder.addStatement("$L(value, charset)", setAs(type.kindTypeName()))
                            .addStatement("break")
                            .endControlFlow();
                        lastCaseSet = 0;
                        break;
                    case 16:
                        if (lastCaseSet < 0)
                        {
                            builder.beginControlFlow("case 0:")
                                .endControlFlow();
                        }
                        builder.beginControlFlow("case 1:")
                            .addStatement("$L(value, charset)", setAs(type.kindTypeName()))
                            .addStatement("break")
                            .endControlFlow();
                        lastCaseSet = 1;
                        break;
                    case 32:
                        if (lastCaseSet < 0)
                        {
                            builder.beginControlFlow("case 0:")
                                .endControlFlow();
                        }
                        if (lastCaseSet == 0)
                        {
                            builder.beginControlFlow("case 1:")
                                .endControlFlow();
                        }
                        builder.beginControlFlow("case 2:")
                            .endControlFlow()
                            .beginControlFlow("case 3:")
                            .addStatement("$L(value, charset)", setAs(type.kindTypeName()))
                            .addStatement("break")
                            .endControlFlow();
                        break;
                    }
                }
                builder.beginControlFlow("default:")
                    .addStatement("throw new IllegalArgumentException(\"Illegal value: \" + value)")
                    .endControlFlow()
                    .endControlFlow();
                return builder.addStatement("return this").build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isStringType(ofType) && !kindType.isPrimitive())
                {
                    super.mixin(builder);
                }
            }
        }

        private final class SetMethodWithByteArrayGenerator extends MethodSpecGenerator
        {
            private final Set<TypeWidth> kindTypeSet = new TreeSet<>();
            private final AstType ofType;
            private final TypeName kindType;

            private SetMethodWithByteArrayGenerator(
                TypeName kindTypeName,
                AstType ofType)
            {
                super(methodBuilder("set")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(thisName));
                this.ofType = ofType;
                this.kindType = kindTypeName;
            }

            public SetMethodWithByteArrayGenerator addMember(
                String kindTypeName,
                TypeName kindType,
                TypeName unsignedKindType)
            {
                if (isBoundedOctetsType(ofType) && !kindType.isPrimitive())
                {
                    if (Character.isDigit(kindTypeName.charAt(0)))
                    {
                        String constantDigit = kindTypeName.substring(0, 1);
                        TypeWidth currentType = new TypeWidth(kindType, unsignedKindType, kindTypeName,
                            "0".equals(constantDigit) ? 0 : 8, Integer.parseInt(constantDigit));
                        kindTypeSet.add(currentType);
                    }
                    else
                    {
                        String typeSize = kindTypeName.replaceAll("\\D+", "");
                        int memberWidth = !typeSize.isEmpty() ? Integer.parseInt(typeSize) : 8;
                        kindTypeSet.add(new TypeWidth(kindType, unsignedKindType, kindTypeName, memberWidth, Integer.MAX_VALUE));
                    }
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                builder.addParameter(byte[].class, "value")
                    .addStatement("int highestByteIndex = Integer.numberOfTrailingZeros(Integer.highestOneBit(value.length)) >>" +
                        " 3")
                    .beginControlFlow("switch (highestByteIndex)");
                int lastCaseSet = -1;
                final String setAsStatement = "$L(value)";
                for (TypeWidth type : kindTypeSet)
                {
                    int width = type.width();
                    switch (width)
                    {
                    case 8:
                        builder.beginControlFlow("case 0:");
                        if (isStringType(ofType) || isBoundedOctetsType(ofType))
                        {
                            builder.endControlFlow()
                                .beginControlFlow("case 4:");
                        }
                        builder.addStatement(setAsStatement, setAs(type.kindTypeName()))
                            .addStatement("break")
                            .endControlFlow();
                        lastCaseSet = 0;
                        break;
                    case 16:
                        if (lastCaseSet < 0)
                        {
                            builder.beginControlFlow("case 0:")
                                .endControlFlow();
                        }
                        builder.beginControlFlow("case 1:")
                            .addStatement(setAsStatement, setAs(type.kindTypeName()))
                            .addStatement("break")
                            .endControlFlow();
                        lastCaseSet = 1;
                        break;
                    case 32:
                        if (lastCaseSet < 0)
                        {
                            builder.beginControlFlow("case 0:")
                                .endControlFlow();
                        }
                        if (lastCaseSet == 0)
                        {
                            builder.beginControlFlow("case 1:")
                                .endControlFlow();
                        }
                        builder.beginControlFlow("case 2:")
                            .endControlFlow()
                            .beginControlFlow("case 3:")
                            .addStatement(setAsStatement, setAs(type.kindTypeName()))
                            .addStatement("break")
                            .endControlFlow();
                        break;
                    }
                }
                builder.beginControlFlow("default:")
                    .addStatement("throw new IllegalArgumentException(\"Illegal value: \" + value)")
                    .endControlFlow()
                    .endControlFlow();
                return builder.addStatement("return this").build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isBoundedOctetsType(ofType) && !kindType.isPrimitive())
                {
                    super.mixin(builder);
                }
            }
        }

        private class TypeWidth implements Comparable<TypeWidth>
        {
            private TypeName kindType;
            private TypeName unsignedKindType;
            private String kindTypeName;
            private int width;
            private int value;

            TypeWidth(
                TypeName kindType,
                TypeName unsignedKindType,
                String kindTypeName,
                int width,
                int value)
            {
                this.kindType = kindType;
                this.unsignedKindType = unsignedKindType;
                this.kindTypeName = kindTypeName;
                this.width = width;
                this.value = value;
            }

            public TypeName kindType()
            {
                return kindType;
            }

            public TypeName unsignedKindType()
            {
                return unsignedKindType;
            }

            public String kindTypeName()
            {
                return kindTypeName;
            }

            public int width()
            {
                return width;
            }

            public int value()
            {
                return value;
            }

            @Override
            public int compareTo(
                TypeWidth anotherType)
            {
                return this.width != anotherType.width() ?
                    this.width - anotherType.width() : this.value - anotherType.value();
            }
        }

        private static final class SetAsFieldMethodGenerator extends ClassSpecMixinGenerator
        {
            private final TypeName kindTypeName;
            private final AstType ofType;
            private final TypeName ofTypeName;
            private final TypeResolver resolver;
            private final AstByteOrder byteOrder;

            private SetAsFieldMethodGenerator(
                ClassName thisType,
                TypeName kindTypeName,
                AstType ofType,
                TypeName ofTypeName,
                TypeSpec.Builder builder,
                TypeResolver resolver,
                AstByteOrder byteOrder)
            {
                super(thisType, builder);
                this.kindTypeName = kindTypeName;
                this.ofType = ofType;
                this.ofTypeName = ofTypeName;
                this.resolver = resolver;
                this.byteOrder = byteOrder;
            }

            public SetAsFieldMethodGenerator addMember(
                Object kindValue,
                String memberName,
                AstType memberType,
                TypeName memberTypeName,
                TypeName unsignedMemberTypeName,
                AstType mapKeyType,
                AstType mapValueType)
            {
                if (!isArrayType(ofType) && !isMapType(ofType))
                {
                    if (memberTypeName != null)
                    {
                        CodeBlock.Builder code = memberTypeName.isPrimitive() ?
                            addPrimitiveMember(memberName, memberType, memberTypeName, unsignedMemberTypeName) :
                            addNonPrimitiveMember(kindValue, memberName, memberTypeName, mapKeyType, mapValueType);
                        TypeName parameterType = TypeName.INT;
                        if (ofType == null)
                        {
                            parameterType = memberTypeName;
                        }
                        else if (memberTypeName.isPrimitive())
                        {
                            if (!memberTypeName.equals(TypeName.BYTE) && !memberTypeName.equals(TypeName.SHORT))
                            {
                                parameterType = Objects.requireNonNullElse(unsignedMemberTypeName, memberTypeName);
                            }
                        }
                        else
                        {
                            parameterType = ofTypeName;
                        }
                        MethodSpec.Builder setAsMethodBuilder = methodBuilder(setAs(memberType.isDynamicType() ?
                            fieldName(memberTypeName) : memberName))
                            .addModifiers(PUBLIC)
                            .addParameter(parameterType, isListType(ofType) ? "list" : "value");
                        setAsMethodBuilder.returns(thisType)
                            .addCode(code.build())
                            .addStatement("return this");
                        if (!isStringType(ofType) || kindTypeName.isPrimitive())
                        {
                            builder.addMethod(setAsMethodBuilder.build());
                        }
                    }
                    else if (memberType != null)
                    {
                        CodeBlock.Builder code = addConstantValueMember(memberName);
                        builder.addMethod(methodBuilder(setAs(memberName))
                            .addModifiers(PUBLIC)
                            .returns(thisType)
                            .addCode(code.build())
                            .addStatement("return this")
                            .build());
                    }
                }
                return this;
            }

            public CodeBlock.Builder addPrimitiveMember(
                String memberName,
                AstType memberType,
                TypeName memberTypeName,
                TypeName unsignedMemberTypeName)
            {
                CodeBlock.Builder code = CodeBlock.builder();
                code.addStatement("kind($L)", kind(memberName));
                if (kindTypeName.isPrimitive())
                {
                    code.addStatement("int newLimit = offset() + $L + $L", offset(memberName), size(memberName));
                }
                else
                {
                    code.addStatement("int newLimit = limit() + $L", size(memberName));
                }
                code.addStatement("checkLimit(newLimit, maxLimit())");
                if (memberType == AstType.INT24 || memberType == AstType.UINT24)
                {
                    return addInt24Member(code, memberName);
                }
                String putterName = String.format("put%s", TYPE_NAMES.get(memberTypeName));
                if (putterName == null)
                {
                    throw new IllegalStateException("member type not supported: " + memberTypeName);
                }

                String castType = "";
                String unsignedHex = "";
                if (unsignedMemberTypeName != null)
                {
                    if (memberTypeName.equals(TypeName.BYTE))
                    {
                        castType = "(byte) (";
                        unsignedHex = " & 0xFF)";
                    }
                    else if (memberTypeName.equals(TypeName.SHORT))
                    {
                        castType = "(short) (";
                        unsignedHex = " & 0xFFFF)";
                    }
                    else if (memberTypeName.equals(TypeName.INT))
                    {
                        castType = "(int) (";
                        unsignedHex = " & 0xFFFF_FFFFL)";
                    }
                }
                else
                {
                    if (memberTypeName.equals(TypeName.BYTE))
                    {
                        castType = "(byte) ";
                    }
                    else if (memberTypeName.equals(TypeName.SHORT))
                    {
                        castType = "(short) ";
                    }
                }

                String primitiveKindMemberPutStatement = "buffer().%s(offset() + $L, %svalue%s%s)";
                String nonPrimitiveKindMemberPutStatement = "buffer().%s(limit(), %svalue%s%s)";
                String byteOrderMark = "";

                if (byteOrder == AstByteOrder.NETWORK)
                {
                    if (memberTypeName == TypeName.SHORT || memberTypeName == TypeName.INT || memberTypeName == TypeName.LONG)
                    {
                        byteOrderMark = ", $T.BIG_ENDIAN";
                    }
                }

                String putStatement = String.format(kindTypeName.isPrimitive() ? primitiveKindMemberPutStatement :
                    nonPrimitiveKindMemberPutStatement, putterName, castType, unsignedHex, byteOrderMark);
                if (kindTypeName.isPrimitive())
                {
                    if ("".equals(byteOrderMark))
                    {
                        code.addStatement(putStatement, offset(memberName));
                    }
                    else
                    {
                        code.addStatement(putStatement, offset(memberName), ByteOrder.class);
                    }
                }
                else
                {
                    if ("".equals(byteOrderMark))
                    {
                        code.addStatement(putStatement);
                    }
                    else
                    {
                        code.addStatement(putStatement, ByteOrder.class);
                    }
                }
                code.addStatement("limit(newLimit)");
                return code;
            }

            public CodeBlock.Builder addInt24Member(
                CodeBlock.Builder code,
                String memberName)
            {
                String offsetStatement = kindTypeName.isPrimitive() ? String.format("int offset = offset() + %s",
                    offset(memberName)) : "int offset = limit()";
                return code.addStatement(offsetStatement)
                    .beginControlFlow("if ($T.NATIVE_BYTE_ORDER != $T.BIG_ENDIAN)", BUFFER_UTIL_TYPE, ByteOrder.class)
                    .addStatement("buffer().putByte(offset, (byte) (value >> 16))")
                    .addStatement("buffer().putByte(offset + 1, (byte) (value >> 8))")
                    .addStatement("buffer().putByte(offset + 2, (byte) value)")
                    .endControlFlow()
                    .beginControlFlow("else")
                    .addStatement("buffer().putByte(offset, (byte) value)")
                    .addStatement("buffer().putByte(offset + 1, (byte) (value >> 8))")
                    .addStatement("buffer().putByte(offset + 2, (byte) (value >> 16))")
                    .endControlFlow()
                    .addStatement("limit(newLimit)");
            }

            public CodeBlock.Builder addNonPrimitiveMember(
                Object kindValue,
                String memberName,
                TypeName memberTypeName,
                AstType mapKeyType,
                AstType mapValueType)
            {
                CodeBlock.Builder code = CodeBlock.builder();
                if (ofType == null)
                {
                    if (mapKeyType == null)
                    {
                        code.addStatement("$T.Builder $L = $LRW.wrap(buffer(), offset(), maxLimit())", memberTypeName,
                            fieldName(memberTypeName), fieldName(memberTypeName))
                            .addStatement("$L.set(value.get())",  fieldName(memberTypeName))
                            .addStatement("limit($L.build().limit())", fieldName(memberTypeName));
                    }
                    else
                    {
                        ClassName mapKeyTypeName = resolver.resolveClass(mapKeyType);
                        ClassName mapValueTypeName = resolver.resolveClass(mapValueType);
                        TypeName parameterizedMapBuilderType = ParameterizedTypeName.get(((ClassName) memberTypeName)
                                .nestedClass("Builder"), mapKeyTypeName, mapValueTypeName,
                            mapKeyTypeName.nestedClass("Builder"), mapValueTypeName.nestedClass("Builder"));
                        code.addStatement("$T $L = $LRW().wrap(buffer(), offset(), maxLimit())", parameterizedMapBuilderType,
                            fieldName(memberTypeName), fieldName(memberTypeName))
                            .addStatement("$L.entries(value.entries(), 0, value.entries().capacity(), value.fieldCount())",
                                fieldName(memberTypeName))
                            .addStatement("limit($L.build().limit())", fieldName(memberTypeName));
                    }
                }
                else
                {
                    if (resolver.resolve(memberName) != null && resolver.resolve(memberName).getKind() == Kind.VARIANT)
                    {
                        final String fieldName = fieldName(memberTypeName);
                        code.addStatement("kind($L)", kind(kindValue.toString()))
                            .addStatement("$LRW.wrap(buffer(), limit(), maxLimit())", fieldName)
                            .addStatement("$LRW.set(value)", fieldName)
                            .addStatement("limit($LRW.limit())", fieldName);
                    }
                    else
                    {
                        code.addStatement("kind($L)", kind(memberName));
                    }
                    if (kindTypeName.isPrimitive())
                    {
                        code.addStatement("$T.Builder $L = $LRW.wrap(buffer(), offset() + $L, maxLimit())", memberTypeName,
                            memberName, memberName, offset(memberName));
                    }
                    else if (isListType(ofType) || isBoundedOctetsType(ofType))
                    {
                        code.addStatement("$T.Builder $L = $LRW.wrap(buffer(), limit(), maxLimit())", memberTypeName,
                            memberName, memberName);
                    }

                    if (isListType(ofType))
                    {
                        code.addStatement("final DirectBuffer fields = list.fields()")
                            .addStatement("$L.fields(list.fieldCount(), fields, 0, fields.capacity())", memberName)
                            .addStatement("limit($L.build().limit())", memberName);
                    }
                    else if (isBoundedOctetsType(ofType))
                    {
                        code.addStatement("$L.set(value)", memberName)
                            .addStatement("limit($L.build().limit())", memberName);
                    }
                    else if (isStringType(ofType) && kindTypeName.isPrimitive())
                    {
                        code.addStatement("$L.set(value.asString(), $T.UTF_8)", memberName, StandardCharsets.class)
                            .addStatement("$T $LRO = $L.build()", memberTypeName, memberName, memberName);
                        code.addStatement("limit($LRO.limit())", memberName);
                    }
                }
                return code;
            }

            public CodeBlock.Builder addConstantValueMember(
                String memberName)
            {
                CodeBlock.Builder code = CodeBlock.builder();
                if (kindTypeName.isPrimitive())
                {
                    code.addStatement("int newLimit = offset() + $L", size("kind"))
                        .addStatement("checkLimit(newLimit, maxLimit())")
                        .addStatement("kind($L)", kind(memberName))
                        .addStatement("limit(newLimit)");
                }
                else
                {
                    code.addStatement("kind($L)", kind(memberName));
                }
                return code;
            }

            @Override
            public TypeSpec.Builder build()
            {
                return super.build();
            }
        }

        private static final class SetAsFieldMethodForStringOfTypeGenerator extends ClassSpecMixinGenerator
        {
            private final TypeName kindTypeName;
            private final AstType ofType;
            private final TypeName ofTypeName;

            private SetAsFieldMethodForStringOfTypeGenerator(
                ClassName thisType,
                TypeName kindTypeName,
                AstType ofType,
                TypeName ofTypeName,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                this.kindTypeName = kindTypeName;
                this.ofType = ofType;
                this.ofTypeName = ofTypeName;
            }

            public SetAsFieldMethodForStringOfTypeGenerator addMember(
                String memberName,
                AstType memberType,
                TypeName memberTypeName)
            {
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    String kindName = enumFWName(kindTypeName);
                    MethodSpec.Builder setAsMethodWithStringFWBuilder = methodBuilder(setAs(memberType.isDynamicType() ?
                        fieldName(memberTypeName) : memberName))
                        .addModifiers(PUBLIC)
                        .addParameter(ofTypeName, "value")
                        .addStatement("kind($L)", kind(memberName))
                        .addStatement("int offset = array == null || array.limit() == array.fieldsOffset() ? " +
                            "$LRW.limit() : array.limit()", kindName)
                        .addStatement("$T $L = $LRW.wrap(buffer(), offset, maxLimit()).set(value).build()",
                            memberTypeName, memberName, memberName)
                        .addStatement("limit($L.limit())", memberName)
                        .returns(thisType)
                        .addStatement("return this");
                    builder.addMethod(setAsMethodWithStringFWBuilder.build());

                    MethodSpec.Builder setAsMethodWithBufferBuilder = methodBuilder(setAs(memberType.isDynamicType() ?
                        fieldName(memberTypeName) : memberName))
                        .addModifiers(PUBLIC)
                        .addParameter(DIRECT_BUFFER_TYPE, "srcBuffer")
                        .addParameter(int.class, "srcOffset")
                        .addParameter(int.class, "length")
                        .addStatement("kind($L)", kind(memberName))
                        .addStatement("int offset = array == null || array.limit() == array.fieldsOffset() ? " +
                            "$LRW.limit() : array.limit()", kindName)
                        .addStatement("$T $L = $LRW.wrap(buffer(), offset, maxLimit()).set(srcBuffer, srcOffset, length).build()",
                            memberTypeName, memberName, memberName)
                        .addStatement("limit($L.limit())", memberName)
                        .returns(thisType)
                        .addStatement("return this");
                    builder.addMethod(setAsMethodWithBufferBuilder.build());

                    MethodSpec.Builder setAsMethodWithStringBuilder = methodBuilder(setAs(memberType.isDynamicType() ?
                        fieldName(memberTypeName) : memberName))
                        .addModifiers(PUBLIC)
                        .addParameter(String.class, "value")
                        .addParameter(Charset.class, "charset")
                        .addStatement("kind($L)", kind(memberName))
                        .addStatement("int offset = array == null || array.limit() == array.fieldsOffset() ? " +
                            "$LRW.limit() : array.limit()", kindName)
                        .addStatement("$T $L = $LRW.wrap(buffer(), offset, maxLimit()).set(value, charset).build()",
                            memberTypeName, memberName, memberName)
                        .addStatement("limit($L.limit())", memberName)
                        .returns(thisType)
                        .addStatement("return this");
                    builder.addMethod(setAsMethodWithStringBuilder.build());
                }
                return this;
            }
        }

        private static final class SetAsFieldMethodForOctetsOfTypeGenerator extends ClassSpecMixinGenerator
        {
            private final TypeName kindTypeName;
            private final AstType ofType;

            private SetAsFieldMethodForOctetsOfTypeGenerator(
                ClassName thisType,
                TypeName kindTypeName,
                AstType ofType,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                this.kindTypeName = kindTypeName;
                this.ofType = ofType;
            }

            public SetAsFieldMethodForOctetsOfTypeGenerator addMember(
                String memberName,
                AstType memberType,
                TypeName memberTypeName)
            {
                if (isBoundedOctetsType(ofType) && !kindTypeName.isPrimitive())
                {
                    MethodSpec.Builder setAsMethodWithBufferBuilder = methodBuilder(setAs(memberType.isDynamicType() ?
                        fieldName(memberTypeName) : memberName))
                        .addModifiers(PRIVATE)
                        .returns(thisType)
                        .addParameter(DIRECT_BUFFER_TYPE, "value")
                        .addParameter(int.class, "offset")
                        .addParameter(int.class, "length")
                        .addStatement("kind($L)", kind(memberName))
                        .addStatement("$T.Builder $L = $LRW.wrap(buffer(), limit(), maxLimit())", memberTypeName, memberName,
                            memberName)
                        .addStatement("$L.set(value, offset, length)", memberName)
                        .addStatement("limit($L.build().limit())", memberName)
                        .addStatement("return this");
                    builder.addMethod(setAsMethodWithBufferBuilder.build());

                    MethodSpec.Builder setAsMethodWithByteArrayBuilder = methodBuilder(setAs(memberType.isDynamicType() ?
                        fieldName(memberTypeName) : memberName))
                        .addModifiers(PRIVATE)
                        .returns(thisType)
                        .addParameter(byte[].class, "value")
                        .addStatement("kind($L)", kind(memberName))
                        .addStatement("$T.Builder $L = $LRW.wrap(buffer(), limit(), maxLimit())", memberTypeName, memberName,
                            memberName)
                        .addStatement("$L.set(value)", memberName)
                        .addStatement("limit($L.build().limit())", memberName)
                        .addStatement("return this");
                    builder.addMethod(setAsMethodWithByteArrayBuilder.build());
                }
                return this;
            }
        }

        private final class SetWithSpecificKindMethodGenerator extends MethodSpecGenerator
        {
            private final AstType ofType;
            private final TypeName kindTypeName;

            private SetWithSpecificKindMethodGenerator(
                TypeName kindTypeName,
                AstType ofType,
                TypeResolver resolver)
            {
                super(methodBuilder("setAs")
                    .addModifiers(PUBLIC)
                    .returns(thisName));
                this.ofType = ofType;
                this.kindTypeName = kindTypeName;

                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    ClassName kindName = enumClassName(kindTypeName);
                    builder.addParameter(kindName, "kind")
                        .addParameter(resolver.resolveClass(ofType), "value")
                        .beginControlFlow("switch (kind)");
                }
            }

            public SetWithSpecificKindMethodGenerator addMember(
                Object kindValue,
                String memberName)
            {
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    builder.beginControlFlow("case $L:", kindValue)
                        .addStatement("$L(value)", setAs(memberName))
                        .addStatement("break")
                        .endControlFlow();
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                builder.beginControlFlow("default:")
                    .addStatement("throw new IllegalArgumentException($S + kind)", "Unexpected kind: ")
                    .endControlFlow();

                return builder.endControlFlow()
                    .addStatement("return this")
                    .build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    super.mixin(builder);
                }
            }
        }

        private final class BuildMethodGenerator extends MethodSpecGenerator
        {
            private final List<Integer> sizes = new ArrayList<>();
            private final TypeName kindTypeName;
            private final AstType ofType;
            private final ClassName variantType;
            private AstType largestListTypeName;

            private BuildMethodGenerator(
                TypeName kindTypeName,
                ClassName variantType,
                AstType ofType)
            {
                super(methodBuilder("build")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(variantType));
                this.kindTypeName = kindTypeName;
                this.ofType = ofType;
                this.largestListTypeName = AstType.LIST0;
                this.variantType = variantType;
                if (isArrayType(ofType))
                {
                    builder.returns(ParameterizedTypeName.get(variantType, typeVarV));
                }
                else if (isMapType(ofType))
                {
                    builder.returns(ParameterizedTypeName.get(variantType, typeVarKV, typeVarVV));
                }
            }

            public BuildMethodGenerator addMember(
                AstType memberType)
            {
                if (isListType(ofType) || isArrayType(ofType) || isMapType(ofType))
                {
                    if (typeSize(memberType) > typeSize(largestListTypeName))
                    {
                        largestListTypeName = memberType;
                    }
                    sizes.add(typeSize(memberType));
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                if (isListType(ofType))
                {
                    generateListType();
                }
                else if (isArrayType(ofType))
                {
                    return generateArrayType();
                }
                else if (isMapType(ofType))
                {
                    generateMapType();
                }
                builder.beginControlFlow("default:")
                    .addStatement("throw new IllegalArgumentException(\"Illegal length: \" + length)")
                    .endControlFlow()
                    .endControlFlow();
                if (isListType(ofType))
                {
                    builder.endControlFlow();
                }
                return  builder.addStatement("return super.build()").build();
            }

            private MethodSpec generateArrayType()
            {
                Collections.sort(sizes);
                int largestSize = sizes.get(sizes.size() - 1);

                builder.addStatement("Array$LFW<V> array$L = array$LRW.build()", largestSize, largestSize,
                    largestSize)
                    .addStatement("long length = Math.max(array$L.length(), array$L.fieldCount())", largestSize, largestSize)
                    .addStatement("int highestByteIndex = Long.numberOfTrailingZeros(Long.highestOneBit(length)) >> 3")
                    .beginControlFlow("switch (highestByteIndex)");
                int priorSize = -1;
                for (int size : sizes)
                {
                    switch (size)
                    {
                    case 8:
                        builder.beginControlFlow("case 0:")
                            .endControlFlow()
                            .beginControlFlow("case 8:")
                            .addStatement("$L.wrap(buffer(), offset(), maxLimit())", enumRW(kindTypeName))
                            .addStatement("$L.set(KIND_ARRAY8)", enumRW(kindTypeName))
                            .addStatement("int fieldCount = array$L.fieldCount()", largestSize)
                            .addStatement("array8RW.wrap(buffer(), $L.limit(), maxLimit())", enumRW(kindTypeName))
                            .addStatement("array8RW.items(array$L.items(), 0, array$L.items().capacity(), fieldCount, " +
                                    "array$L.maxLength())", largestSize, largestSize, largestSize)
                            .addStatement("limit(array8RW.limit())")
                            .addStatement("break")
                            .endControlFlow();
                        priorSize = 8;
                        break;
                    case 16:
                        if (priorSize < 8)
                        {
                            builder.beginControlFlow("case 0:")
                                .endControlFlow()
                                .beginControlFlow("case 8:")
                                .endControlFlow();
                        }
                        builder.beginControlFlow("case 1:");
                        if (largestSize == 16)
                        {
                            builder.addStatement("limit(array16.limit())");
                        }
                        else
                        {
                            builder.addStatement("$L.wrap(buffer(), offset(), maxLimit())", enumRW(kindTypeName))
                                .addStatement("$L.set(KIND_ARRAY16)", enumRW(kindTypeName))
                                .addStatement("int fieldCount = array$L.fieldCount()", largestSize)
                                .addStatement("array16RW.wrap(buffer(), $L.limit(), maxLimit())", enumRW(kindTypeName))
                                .addStatement("array16RW.items(array$L.items(), 0, array$L.items().capacity(), " +
                                        "fieldCount, array$L.maxLength())", largestSize, largestSize, largestSize)
                                .addStatement("limit(array16RW.limit())");
                        }
                        builder.addStatement("break")
                            .endControlFlow();
                        priorSize = 16;
                        break;
                    case 32:
                        switch (priorSize)
                        {
                        case -1:
                            builder.beginControlFlow("case 0:")
                                .endControlFlow()
                                .beginControlFlow("case 8:")
                                .endControlFlow()
                                .beginControlFlow("case 1:")
                                .endControlFlow();
                            break;
                        case 8:
                            builder.beginControlFlow("case 1:")
                                .endControlFlow();
                            break;
                        }
                        builder.beginControlFlow("case 2:")
                            .endControlFlow()
                            .beginControlFlow("case 3:")
                            .addStatement("limit(array$L.limit())", largestSize)
                            .addStatement("break")
                            .endControlFlow();
                        break;
                    }
                }
                return builder.beginControlFlow("default:")
                    .addStatement("throw new IllegalArgumentException(\"Illegal length: \" + length)")
                    .endControlFlow()
                    .endControlFlow()
                    .addStatement("final $T variant = super.build()", ParameterizedTypeName.get(variantType, typeVarV))
                    .addStatement("variant.maxLength(array$L.maxLength())", largestSize)
                    .addStatement("return variant")
                    .build();
            }

            private void generateMapType()
            {
                Collections.sort(sizes);
                int largestSize = sizes.get(sizes.size() - 1);

                builder.addStatement("Map$LFW<K,V> map$L = map$LRW.build()", largestSize, largestSize, largestSize)
                    .addStatement("long length = Math.max(map$L.length(), map$L.fieldCount())", largestSize, largestSize)
                    .addStatement("int highestByteIndex = Long.numberOfTrailingZeros(Long.highestOneBit(length)) >> 3")
                    .addStatement("int fieldCount = map$L.fieldCount()", largestSize)
                    .beginControlFlow("switch (highestByteIndex)");
                int priorSize = -1;
                for (int size : sizes)
                {
                    switch (size)
                    {
                    case 8:
                        builder.beginControlFlow("case 0:")
                            .endControlFlow()
                            .beginControlFlow("case 8:")
                            .addStatement("$L.wrap(buffer(), offset(), maxLimit())", enumRW(kindTypeName))
                            .addStatement("$L.set(KIND_MAP8)", enumRW(kindTypeName))
                            .addStatement("map8RW.wrap(buffer(), $L.limit(), maxLimit())", enumRW(kindTypeName))
                            .addStatement("map8RW.entries(map$L.entries(), 0, map$L.entries().capacity(), fieldCount)",
                                largestSize, largestSize)
                            .addStatement("limit(map8RW.build().limit())")
                            .addStatement("break")
                            .endControlFlow();
                        priorSize = 8;
                        break;
                    case 16:
                        if (priorSize < 8)
                        {
                            builder.beginControlFlow("case 0:")
                                .endControlFlow()
                                .beginControlFlow("case 8:")
                                .endControlFlow();
                        }
                        builder.beginControlFlow("case 1:");
                        if (largestSize == 16)
                        {
                            builder.addStatement("limit(map16.limit())");
                        }
                        else
                        {
                            builder.addStatement("$L.wrap(buffer(), offset(), maxLimit())", enumRW(kindTypeName))
                                .addStatement("$L.set(KIND_MAP16)", enumRW(kindTypeName))
                                .addStatement("map16RW.wrap(buffer(), $L.limit(), maxLimit())", enumRW(kindTypeName))
                                .addStatement("map16RW.entries(map$L.entries(), 0, map$L.entries().capacity(), " +
                                    "fieldCount)", largestSize, largestSize)
                                .addStatement("limit(map16RW.build().limit())");
                        }
                        builder.addStatement("break")
                            .endControlFlow();
                        priorSize = 16;
                        break;
                    case 32:
                        switch (priorSize)
                        {
                        case -1:
                            builder.beginControlFlow("case 0:")
                                .endControlFlow()
                                .beginControlFlow("case 8:")
                                .endControlFlow()
                                .beginControlFlow("case 1:")
                                .endControlFlow();
                            break;
                        case 8:
                            builder.beginControlFlow("case 1:")
                                .endControlFlow();
                            break;
                        }
                        builder.beginControlFlow("case 2:")
                            .endControlFlow()
                            .beginControlFlow("case 3:")
                            .addStatement("limit(map$L.limit())", largestSize)
                            .addStatement("break")
                            .endControlFlow();
                        break;
                    }
                }
            }

            private void generateListType()
            {
                builder.addStatement("$LFW kind = $L.build()", enumClassName(kindTypeName), enumRW(kindTypeName))
                    .beginControlFlow("if (kind.get() == $L)", kind(largestListTypeName.name()))
                    .addStatement("$L $L = $LRW.build()", listClassName(largestListTypeName.name()), largestListTypeName,
                        largestListTypeName)
                    .addStatement("long length = $L.fieldCount() == 0 ? 0 : Math.max($L.length(), $L.fieldCount())",
                        largestListTypeName, largestListTypeName, largestListTypeName)
                    .addStatement("int highestByteIndex = Long.numberOfTrailingZeros(Long.highestOneBit(length)) >> 3")
                    .beginControlFlow("switch (highestByteIndex)");
                Collections.sort(sizes);
                for (int size : sizes)
                {
                    switch (size)
                    {
                    case 8:
                        builder.beginControlFlow("case 0:");
                        if (largestListTypeName.equals(AstType.LIST8))
                        {
                            builder.addStatement("limit(list8.limit())");
                        }
                        else
                        {
                            builder.addStatement("$L.wrap(buffer(), offset(), maxLimit())", enumRW(kindTypeName))
                                .addStatement("$L.set(KIND_LIST8)", enumRW(kindTypeName))
                                .addStatement("list8RW.wrap(buffer(), $L.limit(), maxLimit())", enumRW(kindTypeName))
                                .addStatement("list8RW.fields(list32.fieldCount(), this::setList32Fields)")
                                .addStatement("limit(list8RW.build().limit())")
                                .addStatement("break");
                        }
                        builder.endControlFlow();
                        break;
                    case 32:
                        builder.beginControlFlow("case 1:")
                            .endControlFlow()
                            .beginControlFlow("case 2:")
                            .endControlFlow()
                            .beginControlFlow("case 3:")
                            .addStatement("limit(list32.limit())")
                            .addStatement("break")
                            .endControlFlow();
                        break;
                    }
                }

                if (sizes.get(0) == 0)
                {
                    if (largestListTypeName.equals(AstType.LIST0))
                    {
                        builder.addStatement("limit(list0.limit())");
                    }
                    else
                    {
                        builder.beginControlFlow("case 8:")
                            .addStatement("$L.wrap(buffer(), offset(), maxLimit())", enumRW(kindTypeName))
                            .addStatement("$L.set(KIND_LIST0)", enumRW(kindTypeName))
                            .addStatement("list0RW.wrap(buffer(), $L.limit(), maxLimit())", enumRW(kindTypeName))
                            .addStatement("limit(list0RW.build().limit())")
                            .addStatement("break")
                            .endControlFlow();
                    }
                }
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isListType(ofType) || isArrayType(ofType) || isMapType(ofType))
                {
                    super.mixin(builder);
                }
            }
        }

        private final class FieldMethodGenerator extends MethodSpecGenerator
        {
            private final AstType ofType;
            private final List<Integer> listSize = new ArrayList<>();

            private FieldMethodGenerator(
                ClassName builderRawType,
                AstType ofType)
            {
                super(methodBuilder("field")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(thisName)
                    .addParameter(builderRawType.nestedClass("Visitor"), "mutator"));
                this.ofType = ofType;
            }

            public FieldMethodGenerator addMember(
                AstType memberType)
            {
                if (isListType(ofType))
                {
                    listSize.add(typeSize(memberType));
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                Collections.sort(listSize);
                int largestListSize = listSize.get(listSize.size() - 1);
                return builder.addStatement("list$LRW.field(mutator)", largestListSize)
                    .addStatement("limit(list$LRW.limit())", largestListSize)
                    .addStatement("return this")
                    .build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isListType(ofType))
                {
                    super.mixin(builder);
                }
            }
        }

        private static final class FieldsMethodGenerator extends ClassSpecMixinGenerator
        {
            private final AstType ofType;
            private final List<Integer> listSize = new ArrayList<>();
            private final ClassName flyweightBuilderType;

            private FieldsMethodGenerator(
                ClassName thisType,
                AstType ofType,
                ClassName flyweightBuilderType,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                this.ofType = ofType;
                this.flyweightBuilderType = flyweightBuilderType;
            }

            public FieldsMethodGenerator addMember(
                AstType memberType)
            {
                if (isListType(ofType))
                {
                    listSize.add(typeSize(memberType));
                }
                return this;
            }

            @Override
            public TypeSpec.Builder build()
            {
                if (isListType(ofType))
                {
                    Collections.sort(listSize);
                    int largestListSize = listSize.get(listSize.size() - 1);

                    builder
                        .addMethod(methodBuilder("fields")
                            .addAnnotation(Override.class)
                            .addModifiers(PUBLIC)
                            .returns(thisType)
                            .addParameter(int.class, "fieldCount")
                            .addParameter(flyweightBuilderType.nestedClass("Visitor"), "visitor")
                            .addStatement("list$LRW.fields(fieldCount, visitor)", largestListSize)
                            .addStatement("limit(list$LRW.limit())", largestListSize)
                            .addStatement("return this")
                            .build())
                        .addMethod(methodBuilder("fields")
                            .addAnnotation(Override.class)
                            .addModifiers(PUBLIC)
                            .returns(thisType)
                            .addParameter(int.class, "fieldCount")
                            .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                            .addParameter(int.class, "index")
                            .addParameter(int.class, "length")
                            .addStatement("list$LRW.fields(fieldCount, buffer, index, length)", largestListSize)
                            .addStatement("limit(list$LRW.limit())", largestListSize)
                            .addStatement("return this")
                            .build());
                }
                return super.build();
            }
        }

        private final class KindMethodGenerator extends MethodSpecGenerator
        {
            private final AstType ofType;

            private KindMethodGenerator(
                TypeName kindTypeName,
                AstType ofType)
            {
                super(methodBuilder("kind")
                    .addModifiers(PRIVATE)
                    .returns(thisName));
                this.ofType = ofType;
                if (kindTypeName.isPrimitive())
                {
                    builder.addParameter(int.class, "value")
                        .addStatement("buffer().putByte(offset() + $L, (byte)(value & 0xFF))", offset("kind"));
                }
                else
                {
                    if (isNonPrimitiveType(ofType))
                    {
                        if (isArrayType(ofType))
                        {
                            builder.returns(ParameterizedTypeName.get(thisName, typeVarB, typeVarV));
                        }
                        else if (isMapType(ofType))
                        {
                            builder.returns(ParameterizedTypeName.get(thisName, typeVarKV, typeVarVV, typeVarKB, typeVarVB));
                        }
                    }
                    builder.addParameter(enumClassName(kindTypeName), "value")
                        .addStatement("$L.wrap(buffer(), offset(), maxLimit())", enumRW(kindTypeName))
                        .addStatement("$L.set(value)", enumRW(kindTypeName))
                        .addStatement("limit($L.build().limit())", enumRW(kindTypeName));
                }
                builder.addStatement("return this");
            }

            @Override
            public MethodSpec generate()
            {
                return builder.build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (ofType != null)
                {
                    super.mixin(builder);
                }
            }
        }

        private final class MaxKindMethodGenerator extends MethodSpecGenerator
        {
            private final TypeName kindTypeName;
            private final AstType ofType;
            private int maxKindSize;
            private String maxMemberName;

            private MaxKindMethodGenerator(
                TypeName kindTypeName,
                AstType ofType)
            {
                super(methodBuilder("maxKind")
                    .addModifiers(PUBLIC));
                this.kindTypeName = kindTypeName;
                this.ofType = ofType;
            }

            public MaxKindMethodGenerator addMember(
                String memberName)
            {
                if (isStringType(ofType) && !kindTypeName.isPrimitive() && memberName != null)
                {
                    if (!Character.isDigit(memberName.charAt(0)))
                    {
                        int kindSize = Integer.parseInt(memberName.replaceAll("\\D+", ""));
                        if (maxKindSize < kindSize)
                        {
                            maxMemberName = memberName;
                            maxKindSize = kindSize;
                        }
                    }
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                return builder.returns(enumClassName(kindTypeName))
                    .addStatement("return $L", kind(maxMemberName))
                    .build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    super.mixin(builder);
                }
            }
        }

        private final class MinKindMethodGenerator extends MethodSpecGenerator
        {
            private final Set<TypeWidth> kindTypeSet = new TreeSet<>();
            private final TypeName kindTypeName;
            private final AstType ofType;

            private MinKindMethodGenerator(
                TypeName kindTypeName,
                AstType ofType)
            {
                super(methodBuilder("minKind")
                    .addModifiers(PRIVATE)
                    .addParameter(int.class, "length"));
                this.kindTypeName = kindTypeName;
                this.ofType = ofType;
            }

            public MinKindMethodGenerator addMember(
                String kindTypeName,
                TypeName kindType,
                TypeName unsignedKindType)
            {
                if (isStringType(ofType))
                {
                    int memberWidth = Integer.parseInt(kindTypeName.replaceAll("\\D+", ""));
                    kindTypeSet.add(new TypeWidth(kindType, unsignedKindType, kindTypeName, memberWidth, Integer.MAX_VALUE));
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                builder.addStatement("int highestByteIndex = Integer.numberOfTrailingZeros(Integer.highestOneBit(length)) >> 3")
                    .beginControlFlow("switch (highestByteIndex)");
                int lastCaseSet = -1;
                for (TypeWidth type : kindTypeSet)
                {
                    int width = type.width();
                    switch (width)
                    {
                    case 8:
                        addCase8(type);
                        lastCaseSet = 0;
                        break;
                    case 16:
                        addCase16(type, lastCaseSet);
                        lastCaseSet = 1;
                        break;
                    case 32:
                        addCase32(type, lastCaseSet);
                        lastCaseSet = 3;
                        break;
                    }
                }
                return builder.beginControlFlow("default:")
                    .addStatement("throw new IllegalArgumentException(\"Illegal length: \" + length)")
                    .endControlFlow()
                    .endControlFlow()
                    .returns(enumClassName(kindTypeName))
                    .build();
            }

            private void addCase8(
                TypeWidth type)
            {
                builder.beginControlFlow("case 0:")
                    .addStatement("return $L", kind(type.kindTypeName()))
                    .endControlFlow();
            }

            private void addCase16(
                TypeWidth type,
                int lastCaseSet)
            {
                if (lastCaseSet < 0)
                {
                    builder.beginControlFlow("case 0:")
                        .endControlFlow();
                }
                builder.beginControlFlow("case 1:")
                    .addStatement("return $L", kind(type.kindTypeName()))
                    .endControlFlow();
            }

            private void addCase32(
                TypeWidth type,
                int lastCaseSet)
            {
                if (lastCaseSet < 0)
                {
                    builder.beginControlFlow("case 0:")
                        .endControlFlow();
                }
                if (lastCaseSet < 1)
                {
                    builder.beginControlFlow("case 1:")
                        .endControlFlow();
                }
                builder.beginControlFlow("case 2:")
                    .endControlFlow()
                    .beginControlFlow("case 3:")
                    .addStatement("return $L", kind(type.kindTypeName()))
                    .endControlFlow();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    super.mixin(builder);
                }
            }
        }

        private final class ItemMethodGenerator extends ClassSpecMixinGenerator
        {
            private final AstType ofType;
            private final List<Integer> size = new ArrayList<>();

            private ItemMethodGenerator(
                ClassName thisType,
                TypeSpec.Builder builder,
                AstType ofType)
            {
                super(thisType, builder);
                this.ofType = ofType;
            }

            public ItemMethodGenerator addMember(
                AstType memberType)
            {
                if (isArrayType(ofType))
                {
                    size.add(typeSize(memberType));
                }
                return this;
            }

            @Override
            public TypeSpec.Builder build()
            {
                if (isArrayType(ofType))
                {
                    Collections.sort(size);
                    int largestListSize = size.get(size.size() - 1);
                    TypeName consumerType = ParameterizedTypeName.get(ClassName.get(Consumer.class), typeVarB);
                    TypeName returnType = ParameterizedTypeName.get(thisName, typeVarB, typeVarV);
                    return builder
                        .addMethod(methodBuilder("item")
                            .addAnnotation(Override.class)
                            .addModifiers(PUBLIC)
                            .returns(returnType)
                            .addParameter(consumerType, "consumer")
                            .addStatement("array$LRW.item(consumer)", largestListSize)
                            .addStatement("limit(array$LRW.limit())", largestListSize)
                            .addStatement("return this")
                            .build())
                        .addMethod(methodBuilder("items")
                            .addAnnotation(Override.class)
                            .addModifiers(PUBLIC)
                            .returns(returnType)
                            .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                            .addParameter(int.class, "srcOffset")
                            .addParameter(int.class, "length")
                            .addParameter(int.class, "fieldCount")
                            .addParameter(int.class, "maxLength")
                            .addStatement("array$LRW.items(buffer, srcOffset, length, fieldCount, maxLength)", largestListSize)
                            .addStatement("limit(array$LRW.limit())", largestListSize)
                            .addStatement("return this")
                            .build())
                        .addMethod(methodBuilder("fieldsOffset")
                            .addAnnotation(Override.class)
                            .addModifiers(PUBLIC)
                            .returns(int.class)
                            .addStatement("return array$LRW.fieldsOffset()", largestListSize)
                            .build());
                }
                return super.build();
            }
        }

        private final class EntryMethodGenerator extends MethodSpecGenerator
        {
            private final AstType ofType;
            private final List<Integer> size = new ArrayList<>();

            private EntryMethodGenerator(
                AstType ofType)
            {
                super(methodBuilder("entry"));
                this.ofType = ofType;
            }

            public EntryMethodGenerator addMember(
                AstType memberType)
            {
                if (isMapType(ofType))
                {
                    size.add(typeSize(memberType));
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                ClassName consumerRawType = ClassName.get(Consumer.class);
                TypeName consumerKeyType = ParameterizedTypeName.get(consumerRawType, typeVarKB);
                TypeName consumerValueType = ParameterizedTypeName.get(consumerRawType, typeVarVB);
                Collections.sort(size);
                int largestListSize = size.get(size.size() - 1);
                return builder
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(ParameterizedTypeName.get(thisName, typeVarKV, typeVarVV, typeVarKB, typeVarVB))
                    .addParameter(consumerKeyType, "key")
                    .addParameter(consumerValueType, "value")
                    .addStatement("map$LRW.entry(key, value)", largestListSize)
                    .addStatement("limit(map$LRW.limit())", largestListSize)
                    .addStatement("return this")
                    .build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isMapType(ofType))
                {
                    super.mixin(builder);
                }
            }
        }

        private final class EntriesMethodGenerator extends MethodSpecGenerator
        {
            private final AstType ofType;
            private final List<Integer> size = new ArrayList<>();

            private EntriesMethodGenerator(
                AstType ofType)
            {
                super(methodBuilder("entries"));
                this.ofType = ofType;
            }

            public EntriesMethodGenerator addMember(
                AstType memberType)
            {
                if (isMapType(ofType))
                {
                    size.add(typeSize(memberType));
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                Collections.sort(size);
                int largestListSize = size.get(size.size() - 1);
                return builder
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(ParameterizedTypeName.get(thisName, typeVarKV, typeVarVV, typeVarKB, typeVarVB))
                    .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                    .addParameter(int.class, "index")
                    .addParameter(int.class, "length")
                    .addParameter(int.class, "fieldCount")
                    .addStatement("map$LRW.entries(buffer, index, length, fieldCount)", largestListSize)
                    .addStatement("limit(map$LRW.limit())", largestListSize)
                    .addStatement("return this")
                    .build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isMapType(ofType))
                {
                    super.mixin(builder);
                }
            }
        }

        private final class WrapMethodGenerator extends MethodSpecGenerator
        {
            private final AstType ofType;
            private final TypeName kindTypeName;
            private final List<Integer> size = new ArrayList<>();

            private WrapMethodGenerator(
                AstType ofType,
                TypeName kindTypeName)
            {
                super(methodBuilder("wrap")
                    .addModifiers(PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(thisName)
                    .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "maxLimit")
                    .addStatement("super.wrap(buffer, offset, maxLimit)"));
                this.ofType = ofType;
                this.kindTypeName = kindTypeName;
            }

            public WrapMethodGenerator addMember(
                AstType memberType)
            {
                if (isListType(ofType) || isArrayType(ofType) || isMapType(ofType))
                {
                    size.add(typeSize(memberType));
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    builder.addStatement("this.array = null");
                }
                if (!isListType(ofType) && !isArrayType(ofType) && !isMapType(ofType))
                {
                    return builder.addStatement("return this").build();
                }
                Collections.sort(size);
                int largestListSize = size.get(size.size() - 1);
                if (isArrayType(ofType))
                {
                    builder.returns(ParameterizedTypeName.get(thisName, typeVarB, typeVarV))
                        .addStatement("kind($L)", kind(String.format("array%d", largestListSize)))
                        .addStatement("array$LRW.wrap(buffer, limit(), maxLimit)", largestListSize);
                }
                else if (isMapType(ofType))
                {
                    builder.returns(ParameterizedTypeName.get(thisName, typeVarKV, typeVarVV, typeVarKB, typeVarVB))
                        .addStatement("kind($L)", kind(String.format("map%d", largestListSize)))
                        .addStatement("map$LRW.wrap(buffer, limit(), maxLimit)", largestListSize);
                }
                else
                {
                    builder.addStatement("kind($L)", kind(String.format("list%d", largestListSize)))
                        .addStatement("list$LRW.wrap(buffer, limit(), maxLimit)", largestListSize);
                }
                return builder.addStatement("return this")
                    .build();
            }
        }

        private final class WrapMethodWithArrayGenerator extends MethodSpecGenerator
        {
            private final AstType ofType;
            private final TypeName kindTypeName;
            private final TypeResolver resolver;

            private WrapMethodWithArrayGenerator(
                AstType ofType,
                TypeName kindTypeName,
                TypeResolver resolver)
            {
                super(methodBuilder("wrap")
                    .addModifiers(PUBLIC)
                    .addAnnotation(Override.class)
                    .returns(thisName));
                this.ofType = ofType;
                this.kindTypeName = kindTypeName;
                this.resolver = resolver;
            }

            @Override
            public MethodSpec generate()
            {
                ClassName arrayClassName = resolver.resolveClass(AstType.ARRAY);
                ClassName arrayBuilderClassName = arrayClassName.nestedClass("Builder");
                ParameterizedTypeName arrayBuilderType = ParameterizedTypeName.get(arrayBuilderClassName,
                        WildcardTypeName.subtypeOf(Object.class),
                        WildcardTypeName.subtypeOf(Object.class),
                        WildcardTypeName.subtypeOf(Object.class));
                return builder.addParameter(arrayBuilderType, "array")
                    .addStatement("super.wrap(array.buffer(), array.fieldsOffset(), array.maxLimit())")
                    .addStatement("limit(array.limit())")
                    .addStatement("this.array = array")
                    .addStatement("return this")
                    .build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    super.mixin(builder);
                }
            }
        }

        private final class ResetMethodGenerator extends MethodSpecGenerator
        {
            private final AstType ofType;
            private final TypeName kindTypeName;
            private final TypeResolver resolver;

            private ResetMethodGenerator(
                AstType ofType,
                TypeName kindTypeName,
                TypeResolver resolver)
            {
                super(methodBuilder("reset")
                    .addModifiers(PUBLIC)
                    .addAnnotation(Override.class));
                this.ofType = ofType;
                this.kindTypeName = kindTypeName;
                this.resolver = resolver;
            }

            @Override
            public MethodSpec generate()
            {
                ClassName arrayClassName = resolver.resolveClass(AstType.ARRAY);
                ClassName arrayBuilderClassName = arrayClassName.nestedClass("Builder");
                ParameterizedTypeName arrayBuilderType = ParameterizedTypeName.get(arrayBuilderClassName,
                        WildcardTypeName.subtypeOf(Object.class),
                        WildcardTypeName.subtypeOf(Object.class),
                        WildcardTypeName.subtypeOf(Object.class));
                return builder.addParameter(arrayBuilderType, "array")
                    .addStatement("flyweight().kind = null")
                    .build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    super.mixin(builder);
                }
            }
        }

        private static final class SizeOfMethodGenerator extends ClassSpecMixinGenerator
        {
            private SizeOfMethodGenerator(
                ClassName thisType,
                TypeName kindTypeName,
                AstType ofType,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    builder.addMethod(methodBuilder("sizeof")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(int.class)
                        .addStatement("final int offset = array == null || array.limit() == array.fieldsOffset() ? " +
                            "$LRW.limit() : array.limit()", enumFWName(kindTypeName))
                        .addStatement("return limit() - offset")
                        .build());
                }
            }
        }

        private static final class RebuildMethodGenerator extends ClassSpecMixinGenerator
        {
            private RebuildMethodGenerator(
                ClassName thisType,
                TypeName kindTypeName,
                AstType ofType,
                TypeName ofTypeName,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    builder.addMethod(methodBuilder("rebuild")
                        .addAnnotation(Override.class)
                        .addModifiers(PUBLIC)
                        .returns(thisType)
                        .addParameter(thisType, "item")
                        .addParameter(int.class, "maxLength")
                        .addStatement("assert array != null")
                        .addStatement("$T rebuildKind = minKind(maxLength)", enumClassName(kindTypeName))
                        .addStatement("$T newItem = item", thisType)
                        .addStatement("$T value = item.get()", ofTypeName)
                        .addStatement("final int valueOffset = array.limit() == array.fieldsOffset() ?\n" +
                            "array.fieldsOffset() + item.$LRO.sizeof() : array.limit()", enumFWName(kindTypeName))
                        .beginControlFlow("if (value.offset() != valueOffset || !item.kind().equals(rebuildKind))")
                        .addStatement("setAs(rebuildKind, value)")
                        .addStatement("newItem = flyweight().wrap(buffer(), array.limit(), limit(), array.flyweight())")
                        .endControlFlow()
                        .addStatement("return newItem")
                        .build());
                }
            }
        }

        private static final class SetList32FieldsMethodGenerator extends MethodSpecGenerator
        {
            private final AstType ofType;

            private SetList32FieldsMethodGenerator(
                AstType ofType)
            {
                super(methodBuilder("setList32Fields")
                    .addModifiers(PRIVATE)
                    .returns(int.class)
                    .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "maxLimit"));
                this.ofType = ofType;
            }

            public SetList32FieldsMethodGenerator addMember(
                AstType memberType)
            {
                if (AstType.LIST32.equals(memberType))
                {
                    builder.addStatement("List32FW list32 = list32RW.build()")
                        .addStatement("final DirectBuffer fields = list32.fields()")
                        .addStatement("buffer.putBytes(offset, fields, 0, fields.capacity())")
                        .addStatement("return fields.capacity()");
                }
                return this;
            }

            @Override
            public MethodSpec generate()
            {
                return builder.build();
            }

            @Override
            public void mixin(
                TypeSpec.Builder builder)
            {
                if (isListType(ofType))
                {
                    super.mixin(builder);
                }
            }
        }

        private static final class MemberFieldGenerator extends ClassSpecMixinGenerator
        {
            private final AstType ofType;
            private final TypeVariableName typeVarB;
            private final TypeVariableName typeVarV;
            private final TypeVariableName typeVarKB;
            private final TypeVariableName typeVarKV;
            private final TypeVariableName typeVarVB;
            private final TypeVariableName typeVarVV;
            private final TypeResolver resolver;
            private final AstByteOrder byteOrder;

            private MemberFieldGenerator(
                ClassName thisType,
                TypeName kindTypeName,
                TypeVariableName typeVarB,
                TypeVariableName typeVarV,
                TypeVariableName typeVarKB,
                TypeVariableName typeVarKV,
                TypeVariableName typeVarVB,
                TypeVariableName typeVarVV,
                AstType ofType,
                TypeSpec.Builder builder,
                TypeResolver resolver,
                AstByteOrder byteOrder)
            {
                super(thisType, builder);
                this.ofType = ofType;
                this.typeVarB = typeVarB;
                this.typeVarV = typeVarV;
                this.typeVarKB = typeVarKB;
                this.typeVarKV = typeVarKV;
                this.typeVarVB = typeVarVB;
                this.typeVarVV = typeVarVV;
                this.resolver = resolver;
                this.byteOrder = byteOrder;
                if (!kindTypeName.isPrimitive() && ofType != null)
                {
                    ClassName classType = (ClassName) kindTypeName;
                    TypeName builderType = classType.nestedClass("Builder");
                    builder.addField(FieldSpec.builder(builderType, enumRW(kindTypeName), PRIVATE, FINAL)
                           .initializer("new $T()", builderType)
                           .build());
                }
            }

            public MemberFieldGenerator addMember(
                String memberName,
                AstType memberType,
                TypeName memberTypeName,
                AstType mapKeyType,
                AstType mapValueType)
            {
                if (memberTypeName != null && !memberTypeName.isPrimitive())
                {
                    String fieldRW = String.format("%sRW", !memberType.isDynamicType() ? memberName : fieldName(memberTypeName));
                    ClassName classType = (ClassName) memberTypeName;
                    ClassName builderType = classType.nestedClass("Builder");
                    if (isArrayType(ofType))
                    {
                        TypeName parameterizedBuilderType = ParameterizedTypeName.get(builderType, typeVarB,
                            typeVarV);
                        builder.addField(FieldSpec.builder(parameterizedBuilderType, fieldRW, PRIVATE, FINAL).build());
                    }
                    else if (isMapType(ofType))
                    {
                        TypeName parameterizedBuilderType = ParameterizedTypeName.get(builderType, typeVarKV, typeVarVV,
                            typeVarKB, typeVarVB);
                        builder.addField(FieldSpec.builder(parameterizedBuilderType, fieldRW, PRIVATE, FINAL).build());
                    }
                    else if (mapKeyType != null)
                    {
                        final ClassName mapKeyName = resolver.resolveClass(mapKeyType);
                        final ClassName mapValueName = resolver.resolveClass(mapValueType);
                        TypeName parameterizedBuilderType = ParameterizedTypeName.get(builderType,
                            mapKeyName, mapValueName, mapKeyName.nestedClass("Builder"),
                            mapValueName.nestedClass("Builder"));
                        builder.addField(FieldSpec.builder(parameterizedBuilderType, fieldRW, PRIVATE)
                            .build());
                        builder.addMethod(methodBuilder(fieldRW)
                            .addModifiers(PRIVATE)
                            .returns(parameterizedBuilderType)
                            .beginControlFlow("if ($L == null)", fieldRW)
                            .addStatement("$L = new $T<>(new $T(), new " +
                                "$T(), new $T.Builder(), new $T.Builder())", fieldRW,
                                builderType, mapKeyName, mapValueName, mapKeyName, mapValueName)
                            .endControlFlow()
                            .addStatement("return $L", fieldRW)
                            .build());
                    }
                    else if (isListType(ofType) || isBoundedOctetsType(ofType) || isStringType(ofType))
                    {
                        if (byteOrder == NATIVE || memberType.equals(AstType.LIST0) || memberType.equals(AstType.LIST8) ||
                            memberType.equals(AstType.BOUNDED_OCTETS8) || memberType.equals(AstType.STRING8))
                        {
                            builder.addField(FieldSpec.builder(builderType, fieldRW, PRIVATE, FINAL)
                                .initializer("new $T()", builderType)
                                .build());
                        }
                        else
                        {
                            builder.addField(FieldSpec.builder(builderType, fieldRW, PRIVATE, FINAL)
                                .initializer("new $T($T.BIG_ENDIAN)", builderType, ByteOrder.class)
                                .build());
                        }
                    }
                    else
                    {
                        builder.addField(FieldSpec.builder(builderType, fieldRW, PRIVATE, FINAL)
                            .initializer("new $T()", builderType)
                            .build());
                    }
                }
                return this;
            }
        }

        private static final class ArrayFieldGenerator extends ClassSpecMixinGenerator
        {
            private ArrayFieldGenerator(
                ClassName thisType,
                ClassName flyweightBuilderRawType,
                TypeName kindTypeName,
                AstType ofType,
                TypeResolver resolver,
                TypeSpec.Builder builder)
            {
                super(thisType, builder);
                if (isStringType(ofType) && !kindTypeName.isPrimitive())
                {
                    ClassName arrayClassName = resolver.resolveClass(AstType.ARRAY);
                    ClassName arrayBuilderClassName = arrayClassName.nestedClass("Builder");
                    ParameterizedTypeName arrayBuilderType = ParameterizedTypeName.get(arrayBuilderClassName,
                            WildcardTypeName.subtypeOf(Object.class),
                            WildcardTypeName.subtypeOf(Object.class),
                            WildcardTypeName.subtypeOf(Object.class));
                    builder.addField(FieldSpec.builder(arrayBuilderType, "array", PRIVATE).build());
                }
            }
        }
    }

    private static boolean isNonPrimitiveType(
        AstType type)
    {
        return isListType(type) || isStringType(type) || isArrayType(type) || isMapType(type) || isBoundedOctetsType(type);
    }

    private static boolean isListType(
        AstType type)
    {
        return AstType.LIST.equals(type);
    }

    private static boolean isArrayType(
        AstType type)
    {
        return AstType.ARRAY.equals(type) || AstType.ARRAY8.equals(type) ||
            AstType.ARRAY16.equals(type) || AstType.ARRAY32.equals(type);
    }

    private static boolean isMapType(
        AstType type)
    {
        return AstType.MAP.equals(type);
    }

    private static boolean isBoundedOctetsType(
        AstType type)
    {
        return AstType.BOUNDED_OCTETS.equals(type);
    }

    private static boolean isStringType(
        AstType type)
    {
        return AstType.STRING.equals(type) || AstType.STRING8.equals(type) || AstType.STRING16.equals(type) ||
            AstType.STRING32.equals(type);
    }

    private static boolean isStringType(
        ClassName classType)
    {
        return isString8Type(classType) || isString16Type(classType) || isString32Type(classType);
    }

    private static boolean isString8Type(
        ClassName classType)
    {
        String name = classType.simpleName();
        return "String8FW".equals(name);
    }

    private static boolean isString16Type(
        ClassName classType)
    {
        String name = classType.simpleName();
        return "String16FW".equals(name);
    }

    private static boolean isString32Type(
        ClassName classType)
    {
        String name = classType.simpleName();
        return "String32FW".equals(name);
    }

    private static ClassName enumClassName(
        TypeName enumFWTypeName)
    {
        String enumFWName = ((ClassName) enumFWTypeName).simpleName();
        return ClassName.bestGuess(enumFWName.substring(0, enumFWName.length() - 2));
    }

    private static String enumFWName(
        TypeName enumFWTypeName)
    {
        String enumFWName = ((ClassName) enumFWTypeName).simpleName();
        return String.format("%s%s", Character.toLowerCase(enumFWName.charAt(0)),
            enumFWName.substring(1, enumFWName.length() - 2));
    }

    private static String fieldName(
        TypeName type)
    {
        String fieldName =  ((ClassName) type).simpleName();
        return String.format("%s%s", Character.toLowerCase(fieldName.charAt(0)), fieldName.substring(1, fieldName.length() - 2));
    }

    private static String enumRO(
        TypeName enumFWTypeName)
    {
        String enumFWName = ((ClassName) enumFWTypeName).simpleName();
        return String.format("%s%sRO", Character.toLowerCase(enumFWName.charAt(0)),
            enumFWName.substring(1, enumFWName.length() - 2));
    }

    private static String enumRW(
        TypeName enumFWTypeName)
    {
        String enumFWName = ((ClassName) enumFWTypeName).simpleName();
        return String.format("%s%sRW", Character.toLowerCase(enumFWName.charAt(0)),
            enumFWName.substring(1, enumFWName.length() - 2));
    }

    private static String listClassName(
        String listTypeName)
    {
        return String.format("%s%sFW", Character.toUpperCase(listTypeName.charAt(0)), listTypeName.substring(1));
    }

    private static int typeSize(
        AstType type)
    {
        return Integer.parseInt(type.name().replaceAll("\\D+", ""));
    }

    private static String bitMask(
        String fieldName)
    {
        return String.format("BIT_MASK_%s", constant(fieldName));
    }

    private static String value(
        String fieldName)
    {
        String filteredName = NUMBER_WORDS.get(fieldName) == null ? fieldName : NUMBER_WORDS.get(fieldName);
        return String.format("FIELD_VALUE_%s", constant(filteredName));
    }

    private static String kind(
        String fieldName)
    {
        String filteredName = NUMBER_WORDS.get(fieldName) == null ? fieldName : NUMBER_WORDS.get(fieldName);
        return String.format("KIND_%s", constant(filteredName));
    }

    private static String offset(
        String fieldName)
    {
        return String.format("FIELD_OFFSET_%s", constant(fieldName));
    }

    private static String size(
        String fieldName)
    {
        String filteredName = NUMBER_WORDS.get(fieldName) == null ? fieldName : NUMBER_WORDS.get(fieldName);
        return String.format("FIELD_SIZE_%s", constant(filteredName));
    }

    private static String getAs(
        String fieldName)
    {
        String filteredName = NUMBER_WORDS.get(fieldName) == null ? fieldName : NUMBER_WORDS.get(fieldName);
        return String.format("getAs%s%s", Character.toUpperCase(filteredName.charAt(0)), filteredName.substring(1));
    }

    private static String setAs(
        String fieldName)
    {
        String filteredName = NUMBER_WORDS.get(fieldName) == null ? fieldName : NUMBER_WORDS.get(fieldName);
        return String.format("setAs%s%s", Character.toUpperCase(filteredName.charAt(0)), filteredName.substring(1));
    }

    private static String constant(
        String fieldName)
    {
        return fieldName.replaceAll("([^_A-Z])([A-Z])", "$1_$2").toUpperCase();
    }
}
