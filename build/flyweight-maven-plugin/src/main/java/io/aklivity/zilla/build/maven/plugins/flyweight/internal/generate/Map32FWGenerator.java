/*
 * Copyright 2021-2023 Aklivity Inc.
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

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.BIT_UTIL_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.UNSAFE_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.nio.ByteOrder;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

public final class Map32FWGenerator extends ParameterizedTypeSpecGenerator
{
    private final TypeSpec.Builder classBuilder;
    private final TypeVariableName typeVarK;
    private final TypeVariableName typeVarV;
    private final Map32FWGenerator.BuilderClassBuilder builderClassBuilder;

    public Map32FWGenerator(
        ClassName flyweightType,
        ParameterizedTypeName mapType)
    {
        super(ParameterizedTypeName.get(flyweightType.peerClass("Map32FW"),
                TypeVariableName.get("K", flyweightType), TypeVariableName.get("V", flyweightType)));
        this.typeVarK = (TypeVariableName) thisName.typeArguments.get(0);
        this.typeVarV = (TypeVariableName) thisName.typeArguments.get(1);
        this.classBuilder = classBuilder(thisRawName)
            .superclass(mapType)
            .addModifiers(PUBLIC, FINAL)
            .addTypeVariable(typeVarK)
            .addTypeVariable(typeVarV);

        this.builderClassBuilder = new Map32FWGenerator.BuilderClassBuilder(thisName, mapType, flyweightType);
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder
            .addField(lengthSizeConstant())
            .addField(fieldCountSizeConstant())
            .addField(lengthOffsetConstant())
            .addField(fieldCountOffsetConstant())
            .addField(fieldsOffsetConstant())
            .addField(byteOrderField())
            .addField(keyField())
            .addField(valueField())
            .addField(entriesField())
            .addMethod(constructor())
            .addMethod(constructorWithByteOrder())
            .addMethod(lengthMethod())
            .addMethod(fieldCountMethod())
            .addMethod(entriesMethod())
            .addMethod(forEachMethod())
            .addMethod(tryWrapMethod())
            .addMethod(wrapMethod())
            .addMethod(limitMethod())
            .addMethod(toStringMethod())
            .addType(builderClassBuilder.build())
            .build();
    }

    private FieldSpec lengthSizeConstant()
    {
        return FieldSpec.builder(int.class, "LENGTH_SIZE", PRIVATE, STATIC, FINAL)
            .initializer("$T.SIZE_OF_INT", BIT_UTIL_TYPE)
            .build();
    }

    private FieldSpec fieldCountSizeConstant()
    {
        return FieldSpec.builder(int.class, "FIELD_COUNT_SIZE", PRIVATE, STATIC, FINAL)
            .initializer("$T.SIZE_OF_INT", BIT_UTIL_TYPE)
            .build();
    }

    private FieldSpec lengthOffsetConstant()
    {
        return FieldSpec.builder(int.class, "LENGTH_OFFSET", PRIVATE, STATIC, FINAL)
            .initializer("0")
            .build();
    }

    private FieldSpec fieldCountOffsetConstant()
    {
        return FieldSpec.builder(int.class, "FIELD_COUNT_OFFSET", PRIVATE, STATIC, FINAL)
            .initializer("LENGTH_OFFSET + LENGTH_SIZE")
            .build();
    }

    private FieldSpec fieldsOffsetConstant()
    {
        return FieldSpec.builder(int.class, "FIELDS_OFFSET", PRIVATE, STATIC, FINAL)
            .initializer("FIELD_COUNT_OFFSET + FIELD_COUNT_SIZE")
            .build();
    }

    private FieldSpec byteOrderField()
    {
        return FieldSpec.builder(ByteOrder.class, "byteOrder", PRIVATE, FINAL)
            .build();
    }

    private FieldSpec keyField()
    {
        return FieldSpec.builder(typeVarK, "keyRO", PRIVATE, FINAL)
            .build();
    }

    private FieldSpec valueField()
    {
        return FieldSpec.builder(typeVarV, "valueRO", PRIVATE, FINAL)
            .build();
    }

    private FieldSpec entriesField()
    {
        return FieldSpec.builder(DIRECT_BUFFER_TYPE, "entriesRO", PRIVATE, FINAL)
            .initializer("new $T(0L, 0)", UNSAFE_BUFFER_TYPE)
            .build();
    }

    private MethodSpec constructor()
    {
        return constructorBuilder()
            .addModifiers(PUBLIC)
            .addParameter(typeVarK, "keyRO")
            .addParameter(typeVarV, "valueRO")
            .addStatement("this.keyRO = keyRO")
            .addStatement("this.valueRO = valueRO")
            .addStatement("this.byteOrder = $T.nativeOrder()", ByteOrder.class)
            .build();
    }

    private MethodSpec constructorWithByteOrder()
    {
        return constructorBuilder()
            .addModifiers(PUBLIC)
            .addParameter(typeVarK, "keyRO")
            .addParameter(typeVarV, "valueRO")
            .addParameter(ByteOrder.class, "byteOrder")
            .addStatement("this.keyRO = keyRO")
            .addStatement("this.valueRO = valueRO")
            .addStatement("this.byteOrder = byteOrder")
            .build();
    }

    private MethodSpec lengthMethod()
    {
        return methodBuilder("length")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return buffer().getInt(offset() + LENGTH_OFFSET, byteOrder)")
            .build();
    }

    private MethodSpec fieldCountMethod()
    {
        return methodBuilder("fieldCount")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return buffer().getInt(offset() + FIELD_COUNT_OFFSET, byteOrder)")
            .build();
    }

    private MethodSpec entriesMethod()
    {
        return methodBuilder("entries")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(DIRECT_BUFFER_TYPE)
            .addStatement("return entriesRO")
            .build();
    }

    private MethodSpec forEachMethod()
    {
        TypeName parameterizedBiConsumerType = ParameterizedTypeName.get(ClassName.get(BiConsumer.class), typeVarK, typeVarV);

        return methodBuilder("forEach")
            .addAnnotation(Override.class)
            .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "\"unchecked\"").build())
            .addModifiers(PUBLIC)
            .addParameter(parameterizedBiConsumerType, "consumer")
            .addStatement("int offset = offset() + FIELDS_OFFSET")
            .addStatement("int fieldCount = fieldCount()")
            .beginControlFlow("for (int i = 0; i < fieldCount; i += 2)")
            .addStatement("K key = (K) keyRO.wrap(buffer(), offset, limit())")
            .addStatement("V value = (V) valueRO.wrap(buffer(), key.limit(), limit())")
            .addStatement("consumer.accept(key, value)")
            .addStatement("offset = value.limit()")
            .endControlFlow()
            .build();
    }

    private MethodSpec tryWrapMethod()
    {
        return methodBuilder("tryWrap")
            .addAnnotation(Override.class)
            .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "\"unchecked\"").build())
            .addModifiers(PUBLIC)
            .addParameter(DIRECT_BUFFER_TYPE, "buffer")
            .addParameter(int.class, "offset")
            .addParameter(int.class, "maxLimit")
            .returns(thisName)
            .beginControlFlow("if (super.tryWrap(buffer, offset, maxLimit) == null)")
            .addStatement("return null")
            .endControlFlow()
            .addStatement("int entryOffset = offset + FIELDS_OFFSET")
            .addStatement("int fieldCount = fieldCount()")
            .beginControlFlow("for (int i = 0; i < fieldCount; i += 2)")
            .addStatement("K key = (K) keyRO.tryWrap(buffer, entryOffset, maxLimit)")
            .beginControlFlow("if (key == null)")
            .addStatement("return null")
            .endControlFlow()
            .addStatement("V value = (V) valueRO.tryWrap(buffer, key.limit(), maxLimit)")
            .beginControlFlow("if (value == null)")
            .addStatement("return null")
            .endControlFlow()
            .addStatement("entryOffset = value.limit()")
            .endControlFlow()
            .addStatement("final int itemsSize = length() - FIELD_COUNT_SIZE")
            .addStatement("entriesRO.wrap(buffer, offset + FIELDS_OFFSET, itemsSize)")
            .beginControlFlow("if (limit() > maxLimit)")
            .addStatement("return null")
            .endControlFlow()
            .addStatement("return this")
            .build();
    }

    private MethodSpec wrapMethod()
    {
        return methodBuilder("wrap")
            .addAnnotation(Override.class)
            .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "\"unchecked\"").build())
            .addModifiers(PUBLIC)
            .addParameter(DIRECT_BUFFER_TYPE, "buffer")
            .addParameter(int.class, "offset")
            .addParameter(int.class, "maxLimit")
            .returns(thisName)
            .addStatement("super.wrap(buffer, offset, maxLimit)")
            .addStatement("int entryOffset = offset + FIELDS_OFFSET")
            .addStatement("int fieldCount = fieldCount()")
            .beginControlFlow("for (int i = 0; i < fieldCount; i += 2)")
            .addStatement("K key = (K) keyRO.wrap(buffer, entryOffset, maxLimit)")
            .addStatement("V value = (V) valueRO.wrap(buffer, key.limit(), maxLimit)")
            .addStatement("entryOffset = value.limit()")
            .endControlFlow()
            .addStatement("final int itemsSize = length() - FIELD_COUNT_SIZE")
            .addStatement("entriesRO.wrap(buffer, offset + FIELDS_OFFSET, itemsSize)")
            .addStatement("checkLimit(limit(), maxLimit)")
            .addStatement("return this")
            .build();
    }

    private MethodSpec limitMethod()
    {
        return methodBuilder("limit")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return offset() + LENGTH_SIZE + length()")
            .build();
    }

    private MethodSpec toStringMethod()
    {
        return methodBuilder("toString")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(String.class)
            .addStatement("return String.format(\"map32<%d, %d>\", length(), fieldCount())")
            .build();
    }

    private static final class BuilderClassBuilder
    {
        private final ParameterizedTypeName map32Type;
        private final TypeSpec.Builder classBuilder;
        private final TypeVariableName typeVarKB;
        private final TypeVariableName typeVarK;
        private final TypeVariableName typeVarVB;
        private final TypeVariableName typeVarV;
        private final TypeName parameterizedBuilderType;

        private BuilderClassBuilder(
            ParameterizedTypeName map32Type,
            ParameterizedTypeName mapType,
            ClassName flyweightType)
        {
            ClassName map32BuilderType = map32Type.rawType.nestedClass("Builder");
            ClassName mapBuilderType = mapType.rawType.nestedClass("Builder");
            ClassName flyweightBuilderType = flyweightType.nestedClass("Builder");
            this.map32Type = map32Type;
            this.typeVarK = TypeVariableName.get("K", flyweightType);
            this.typeVarKB = TypeVariableName.get("KB", ParameterizedTypeName.get(flyweightBuilderType, typeVarK));
            this.typeVarV = TypeVariableName.get("V", flyweightType);
            this.typeVarVB = TypeVariableName.get("VB", ParameterizedTypeName.get(flyweightBuilderType, typeVarV));
            TypeName parameterizedMapBuilderType = ParameterizedTypeName.get(mapBuilderType, map32Type, typeVarK, typeVarV,
                typeVarKB, typeVarVB);

            this.parameterizedBuilderType = ParameterizedTypeName.get(map32BuilderType, typeVarK, typeVarV, typeVarKB, typeVarVB);

            this.classBuilder = classBuilder(map32BuilderType.simpleName())
                .addModifiers(PUBLIC, STATIC, FINAL)
                .superclass(parameterizedMapBuilderType)
                .addTypeVariable(typeVarK)
                .addTypeVariable(typeVarV)
                .addTypeVariable(typeVarKB)
                .addTypeVariable(typeVarVB);
        }

        public TypeSpec build()
        {
            return classBuilder
                .addField(byteOrderField())
                .addField(keyRWField())
                .addField(valueRWField())
                .addField(fieldCountField())
                .addMethod(constructor())
                .addMethod(constructorWithByteOrder())
                .addMethod(wrapMethod())
                .addMethod(entryMethod())
                .addMethod(entriesMethod())
                .addMethod(buildMethod())
                .build();
        }

        private FieldSpec byteOrderField()
        {
            return FieldSpec.builder(ByteOrder.class, "byteOrder", PRIVATE, FINAL).build();
        }

        private FieldSpec keyRWField()
        {
            return FieldSpec.builder(typeVarKB, "keyRW", PRIVATE, FINAL).build();
        }

        private FieldSpec valueRWField()
        {
            return FieldSpec.builder(typeVarVB, "valueRW", PRIVATE, FINAL).build();
        }

        private FieldSpec fieldCountField()
        {
            return FieldSpec.builder(int.class, "fieldCount", PRIVATE).build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                .addModifiers(PUBLIC)
                .addParameter(typeVarK, "keyRO")
                .addParameter(typeVarV, "valueRO")
                .addParameter(typeVarKB, "keyRW")
                .addParameter(typeVarVB, "valueRW")
                .addStatement("super(new Map32FW<>(keyRO, valueRO))")
                .addStatement("this.keyRW = keyRW")
                .addStatement("this.valueRW = valueRW")
                .addStatement("this.byteOrder = $T.nativeOrder()", ByteOrder.class)
                .build();
        }

        private MethodSpec constructorWithByteOrder()
        {
            return constructorBuilder()
                .addModifiers(PUBLIC)
                .addParameter(typeVarK, "keyRO")
                .addParameter(typeVarV, "valueRO")
                .addParameter(typeVarKB, "keyRW")
                .addParameter(typeVarVB, "valueRW")
                .addParameter(ByteOrder.class, "byteOrder")
                .addStatement("super(new Map32FW<>(keyRO, valueRO, byteOrder))")
                .addStatement("this.keyRW = keyRW")
                .addStatement("this.valueRW = valueRW")
                .addStatement("this.byteOrder = byteOrder")
                .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(parameterizedBuilderType)
                .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .addStatement("super.wrap(buffer, offset, maxLimit)")
                .addStatement("int newLimit = offset + FIELDS_OFFSET")
                .addStatement("checkLimit(newLimit, maxLimit)")
                .addStatement("limit(newLimit)")
                .addStatement("fieldCount = 0")
                .addStatement("return this")
                .build();
        }

        private MethodSpec entryMethod()
        {
            TypeName parameterizedConsumerTypeKey = ParameterizedTypeName.get(ClassName.get(Consumer.class), typeVarKB);
            TypeName parameterizedConsumerTypeValue = ParameterizedTypeName.get(ClassName.get(Consumer.class), typeVarVB);
            return methodBuilder("entry")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(parameterizedBuilderType)
                .addParameter(parameterizedConsumerTypeKey, "key")
                .addParameter(parameterizedConsumerTypeValue, "value")
                .addStatement("keyRW.wrap(buffer(), limit(), maxLimit())")
                .addStatement("key.accept(keyRW)")
                .addStatement("checkLimit(keyRW.limit(), maxLimit())")
                .addStatement("limit(keyRW.limit())")
                .addStatement("fieldCount++")
                .addStatement("valueRW.wrap(buffer(), limit(), maxLimit())")
                .addStatement("value.accept(valueRW)")
                .addStatement("checkLimit(valueRW.limit(), maxLimit())")
                .addStatement("limit(valueRW.limit())")
                .addStatement("fieldCount++")
                .addStatement("return this")
                .build();
        }

        private MethodSpec entriesMethod()
        {
            return methodBuilder("entries")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(parameterizedBuilderType)
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "srcOffset")
                .addParameter(int.class, "length")
                .addParameter(int.class, "fieldCount")
                .addStatement("buffer().putBytes(offset() + FIELDS_OFFSET, buffer, srcOffset, length)")
                .addStatement("int newLimit = offset() + FIELDS_OFFSET + length")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("limit(newLimit)")
                .addStatement("this.fieldCount = fieldCount")
                .addStatement("return this")
                .build();
        }

        private MethodSpec buildMethod()
        {
            return methodBuilder("build")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(map32Type)
                .addStatement("int length = limit() - offset() - FIELD_COUNT_OFFSET")
                .addStatement("buffer().putInt(offset() + LENGTH_OFFSET, length, byteOrder)")
                .addStatement("buffer().putInt(offset() + FIELD_COUNT_OFFSET, fieldCount, byteOrder)")
                .addStatement("return super.build()")
                .build();
        }
    }
}
