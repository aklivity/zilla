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
package io.aklivity.zilla.build.maven.plugins.cog.internal.generate;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.BIT_UTIL_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.UNSAFE_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.nio.ByteOrder;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

public final class Array16FWGenerator extends ParameterizedTypeSpecGenerator
{
    private final TypeSpec.Builder classBuilder;
    private final TypeVariableName typeVarV;
    private final BuilderClassBuilder builderClassBuilder;

    public Array16FWGenerator(
        ClassName flyweightType,
        ParameterizedTypeName arrayType)
    {
        super(flyweightType.peerClass("Array16FW"), TypeVariableName.get("V", flyweightType));
        this.typeVarV = (TypeVariableName) thisName.typeArguments.get(0);
        this.classBuilder = classBuilder(thisRawName)
            .superclass(arrayType)
            .addModifiers(PUBLIC, FINAL)
            .addTypeVariable(typeVarV);

        this.builderClassBuilder = new BuilderClassBuilder(flyweightType, arrayType, thisName);
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
            .addField(lengthMaxValueConstant())
            .addField(emptyBufferConstant())
            .addField(byteOrderField())
            .addField(itemField())
            .addField(itemsField())
            .addField(maxLengthField())
            .addMethod(constructor())
            .addMethod(constructorWithByteOrder())
            .addMethod(lengthMethod())
            .addMethod(fieldsOffsetMethod())
            .addMethod(fieldCountMethod())
            .addMethod(maxLengthMethod())
            .addMethod(forEachMethod())
            .addMethod(anyMatchMethod())
            .addMethod(matchFirstMethod())
            .addMethod(isEmptyMethod())
            .addMethod(itemsMethod())
            .addMethod(wrapMethod())
            .addMethod(tryWrapMethod())
            .addMethod(limitMethod())
            .addMethod(toStringMethod())
            .addMethod(maxLengthSetterMethod())
            .addType(builderClassBuilder.build())
            .build();
    }

    private FieldSpec lengthSizeConstant()
    {
        return FieldSpec.builder(int.class, "LENGTH_SIZE", PRIVATE, STATIC, FINAL)
            .initializer("$T.SIZE_OF_SHORT", BIT_UTIL_TYPE)
            .build();
    }

    private FieldSpec fieldCountSizeConstant()
    {
        return FieldSpec.builder(int.class, "FIELD_COUNT_SIZE", PRIVATE, STATIC, FINAL)
            .initializer("$T.SIZE_OF_SHORT", BIT_UTIL_TYPE)
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

    private FieldSpec lengthMaxValueConstant()
    {
        return FieldSpec.builder(int.class, "LENGTH_MAX_VALUE", PRIVATE, STATIC, FINAL)
            .initializer("0xFFFF")
            .build();
    }

    private FieldSpec emptyBufferConstant()
    {
        return FieldSpec.builder(DIRECT_BUFFER_TYPE, "EMPTY_BUFFER", PRIVATE, STATIC, FINAL)
            .initializer("new $T()", UNSAFE_BUFFER_TYPE)
            .build();
    }

    private FieldSpec byteOrderField()
    {
        return FieldSpec.builder(ByteOrder.class, "byteOrder", PRIVATE, FINAL)
            .build();
    }

    private FieldSpec itemField()
    {
        return FieldSpec.builder(typeVarV, "itemRO", PRIVATE, FINAL)
            .build();
    }

    private FieldSpec itemsField()
    {
        return FieldSpec.builder(DIRECT_BUFFER_TYPE, "itemsRO", PRIVATE, FINAL)
            .initializer("new $T(0L, 0)", UNSAFE_BUFFER_TYPE)
            .build();
    }

    private FieldSpec maxLengthField()
    {
        return FieldSpec.builder(int.class, "maxLength", PRIVATE)
            .build();
    }

    private MethodSpec constructor()
    {
        return constructorBuilder()
            .addModifiers(PUBLIC)
            .addParameter(typeVarV, "itemRO")
            .addStatement("this.itemRO = itemRO")
            .addStatement("this.byteOrder = $T.nativeOrder()", ByteOrder.class)
            .build();
    }

    private MethodSpec constructorWithByteOrder()
    {
        return constructorBuilder()
            .addModifiers(PUBLIC)
            .addParameter(typeVarV, "itemRO")
            .addParameter(ByteOrder.class, "byteOrder")
            .addStatement("this.itemRO = itemRO")
            .addStatement("this.byteOrder = byteOrder")
            .build();
    }

    private MethodSpec lengthMethod()
    {
        return methodBuilder("length")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return buffer().getShort(offset() + LENGTH_OFFSET, byteOrder)")
            .build();
    }

    private MethodSpec fieldsOffsetMethod()
    {
        return methodBuilder("fieldsOffset")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return offset() + FIELDS_OFFSET")
            .build();
    }

    private MethodSpec fieldCountMethod()
    {
        return methodBuilder("fieldCount")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return buffer().getShort(offset() + FIELD_COUNT_OFFSET, byteOrder)")
            .build();
    }

    private MethodSpec maxLengthMethod()
    {
        return methodBuilder("maxLength")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return maxLength")
            .build();
    }

    private MethodSpec forEachMethod()
    {
        TypeName itemType = WildcardTypeName.supertypeOf(typeVarV);
        TypeName consumerType = ParameterizedTypeName.get(ClassName.get(Consumer.class), itemType);

        return methodBuilder("forEach")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(consumerType, "consumer")
            .returns(void.class)
            .addStatement("int offset = offset() + FIELDS_OFFSET")
            .beginControlFlow("for (int i = 0; i < fieldCount(); i++)")
            .addStatement("itemRO.wrap(buffer(), offset, limit(), this)")
            .addStatement("consumer.accept(itemRO)")
            .addStatement("offset = itemRO.limit()")
            .endControlFlow()
            .build();
    }

    private MethodSpec anyMatchMethod()
    {
        TypeName itemType = WildcardTypeName.supertypeOf(typeVarV);
        TypeName predicateType = ParameterizedTypeName.get(ClassName.get(Predicate.class), itemType);

        return methodBuilder("anyMatch")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(predicateType, "predicate")
            .returns(boolean.class)
            .addStatement("int offset = offset() + FIELDS_OFFSET")
            .beginControlFlow("for (int i = 0; i < fieldCount(); i++)")
            .addStatement("itemRO.wrap(buffer(), offset, maxLimit(), this)")
            .beginControlFlow("if (predicate.test(itemRO))")
            .addStatement("return true")
            .endControlFlow()
            .addStatement("offset = itemRO.limit()")
            .endControlFlow()
            .addStatement("return false")
            .build();
    }

    private MethodSpec matchFirstMethod()
    {
        TypeName itemType = WildcardTypeName.supertypeOf(typeVarV);
        TypeName predicateType = ParameterizedTypeName.get(ClassName.get(Predicate.class), itemType);

        return methodBuilder("matchFirst")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(predicateType, "predicate")
            .returns(typeVarV)
            .addStatement("int offset = offset() + FIELDS_OFFSET")
            .beginControlFlow("for (int i = 0; i < fieldCount(); i++)")
            .addStatement("itemRO.wrap(buffer(), offset, maxLimit(), this)")
            .beginControlFlow("if (predicate.test(itemRO))")
            .addStatement("return itemRO")
            .endControlFlow()
            .addStatement("offset = itemRO.limit()")
            .endControlFlow()
            .addStatement("return null")
            .build();
    }

    private MethodSpec isEmptyMethod()
    {
        return methodBuilder("isEmpty")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(boolean.class)
            .addStatement("return fieldCount() == 0")
            .build();
    }

    private MethodSpec itemsMethod()
    {
        return methodBuilder("items")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(DIRECT_BUFFER_TYPE)
            .addStatement("return itemsRO")
            .build();
    }

    private MethodSpec wrapMethod()
    {
        return methodBuilder("wrap")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(DIRECT_BUFFER_TYPE, "buffer")
            .addParameter(int.class, "offset")
            .addParameter(int.class, "maxLimit")
            .returns(thisName)
            .addStatement("super.wrap(buffer, offset, maxLimit)")
            .addStatement("final int itemsSize = limit() - fieldsOffset()")
            .beginControlFlow("if (itemsSize == 0)")
            .addStatement("itemsRO.wrap(EMPTY_BUFFER, 0, 0)")
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement("itemsRO.wrap(buffer, offset + FIELDS_OFFSET, itemsSize)")
            .endControlFlow()
            .addStatement("checkLimit(limit(), maxLimit)")
            .addStatement("return this")
            .build();
    }

    private MethodSpec tryWrapMethod()
    {
        return methodBuilder("tryWrap")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(DIRECT_BUFFER_TYPE, "buffer")
            .addParameter(int.class, "offset")
            .addParameter(int.class, "maxLimit")
            .returns(thisName)
            .beginControlFlow("if (offset + FIELDS_OFFSET > maxLimit)")
            .addStatement("return null")
            .endControlFlow()
            .beginControlFlow("if (super.tryWrap(buffer, offset, maxLimit) == null)")
            .addStatement("return null")
            .endControlFlow()
            .addStatement("final int itemsSize = limit() - fieldsOffset()")
            .beginControlFlow("if (itemsSize == 0)")
            .addStatement("itemsRO.wrap(EMPTY_BUFFER, 0, 0)")
            .endControlFlow()
            .beginControlFlow("else")
            .addStatement("itemsRO.wrap(buffer, offset + FIELDS_OFFSET, itemsSize)")
            .endControlFlow()
            .beginControlFlow("if (limit() > maxLimit)")
            .addStatement("return null")
            .endControlFlow()
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
            .addStatement("return String.format(\"array16<%d, %d>\", length(), fieldCount())")
            .build();
    }

    private MethodSpec maxLengthSetterMethod()
    {
        return methodBuilder("maxLength")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .addParameter(int.class, "maxLength")
            .returns(void.class)
            .addStatement("this.maxLength = maxLength")
            .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final TypeVariableName typeVarB;
        private final TypeVariableName typeVarV;
        private final TypeName array16Type;
        private final TypeName array16BuilderType;

        private BuilderClassBuilder(
            ClassName flyweight,
            ParameterizedTypeName arrayType,
            ParameterizedTypeName array16Type)
        {
            ClassName array8BuilderRawType = array16Type.rawType.nestedClass("Builder");
            ClassName flyweightBuilderRawType = flyweight.nestedClass("Builder");
            ClassName arrayBuilderRawType = arrayType.rawType.nestedClass("Builder");
            this.typeVarV = TypeVariableName.get("V", flyweight);
            this.typeVarB = TypeVariableName.get("B", ParameterizedTypeName.get(flyweightBuilderRawType, typeVarV));
            this.array16Type = array16Type;
            this.array16BuilderType = ParameterizedTypeName.get(array8BuilderRawType, typeVarB, typeVarV);
            TypeName superClassType = ParameterizedTypeName.get(arrayBuilderRawType, array16Type, typeVarB,
                typeVarV);
            this.classBuilder = classBuilder(array8BuilderRawType.simpleName())
                .addModifiers(PUBLIC, STATIC, FINAL)
                .superclass(superClassType)
                .addTypeVariable(typeVarB)
                .addTypeVariable(typeVarV);
        }

        public TypeSpec build()
        {
            return classBuilder
                .addField(byteOrderField())
                .addField(itemRWField())
                .addField(itemROField())
                .addField(fieldCountField())
                .addField(maxLengthField())
                .addMethod(constructor())
                .addMethod(constructorWithByteOrder())
                .addMethod(fieldsOffsetMethod())
                .addMethod(itemMethod())
                .addMethod(itemsMethod())
                .addMethod(wrapMethod())
                .addMethod(buildMethod())
                .build();
        }

        private FieldSpec byteOrderField()
        {
            return FieldSpec.builder(ByteOrder.class, "byteOrder", PRIVATE, FINAL)
                .build();
        }

        private FieldSpec itemRWField()
        {
            return FieldSpec.builder(typeVarB, "itemRW", PRIVATE, FINAL)
                .build();
        }

        private FieldSpec itemROField()
        {
            return FieldSpec.builder(typeVarV, "itemRO", PRIVATE, FINAL)
                .build();
        }

        private FieldSpec fieldCountField()
        {
            return FieldSpec.builder(int.class, "fieldCount", PRIVATE)
                .build();
        }

        private FieldSpec maxLengthField()
        {
            return FieldSpec.builder(int.class, "maxLength", PRIVATE)
                .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                .addModifiers(PUBLIC)
                .addParameter(typeVarB, "itemRW")
                .addParameter(typeVarV, "itemRO")
                .addStatement("super(new Array16FW<>(itemRO))")
                .addStatement("this.byteOrder = $T.nativeOrder()", ByteOrder.class)
                .addStatement("this.itemRW = itemRW")
                .addStatement("this.itemRO = itemRO")
                .build();
        }

        private MethodSpec constructorWithByteOrder()
        {
            return constructorBuilder()
                .addModifiers(PUBLIC)
                .addParameter(typeVarB, "itemRW")
                .addParameter(typeVarV, "itemRO")
                .addParameter(ByteOrder.class, "byteOrder")
                .addStatement("super(new Array16FW<>(itemRO, byteOrder))")
                .addStatement("this.byteOrder = byteOrder")
                .addStatement("this.itemRW = itemRW")
                .addStatement("this.itemRO = itemRO")
                .build();
        }

        private MethodSpec fieldsOffsetMethod()
        {
            return methodBuilder("fieldsOffset")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement("return offset() + FIELDS_OFFSET")
                .build();
        }

        private MethodSpec itemMethod()
        {
            TypeName consumerType = ParameterizedTypeName.get(ClassName.get(Consumer.class), typeVarB);
            return methodBuilder("item")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(array16BuilderType)
                .addParameter(consumerType, "consumer")
                .addStatement("itemRW.wrap(this)")
                .addStatement("consumer.accept(itemRW)")
                .addStatement("itemRW.build()")
                .addStatement("maxLength = Math.max(maxLength, itemRW.sizeof())")
                .addStatement("checkLimit(itemRW.limit(), maxLimit())")
                .addStatement("limit(itemRW.limit())")
                .addStatement("fieldCount++")
                .addStatement("return this")
                .build();
        }

        private MethodSpec itemsMethod()
        {
            return methodBuilder("items")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(array16BuilderType)
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "srcOffset")
                .addParameter(int.class, "length")
                .addParameter(int.class, "fieldCount")
                .addParameter(int.class, "maxLength")
                .addStatement("buffer().putBytes(offset() + FIELDS_OFFSET, buffer, srcOffset, length)")
                .addStatement("int newLimit = offset() + FIELDS_OFFSET + length")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("limit(newLimit)")
                .addStatement("this.fieldCount = fieldCount")
                .addStatement("this.maxLength = maxLength")
                .addStatement("assert length <= LENGTH_MAX_VALUE : \"Length is too large\"")
                .addStatement("assert fieldCount <= LENGTH_MAX_VALUE : \"Field count is too large\"")
                .addStatement("buffer().putShort(offset() + LENGTH_OFFSET, (short) length, byteOrder)")
                .addStatement("buffer().putShort(offset() + FIELD_COUNT_OFFSET, (short) (fieldCount  + FIELD_COUNT_SIZE)" +
                    ", byteOrder)")
                .addStatement("return this")
                .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(array16BuilderType)
                .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .addStatement("super.wrap(buffer, offset, maxLimit)")
                .addStatement("int newLimit = offset + FIELDS_OFFSET")
                .addStatement("checkLimit(newLimit, maxLimit)")
                .addStatement("limit(newLimit)")
                .addStatement("fieldCount = 0")
                .addStatement("maxLength = 0")
                .addStatement("return this")
                .build();
        }

        private MethodSpec buildMethod()
        {
            return methodBuilder("build")
                .addAnnotation(Override.class)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "\"unchecked\"").build())
                .addModifiers(PUBLIC)
                .returns(array16Type)
                .addStatement("int length = limit() - offset() - FIELD_COUNT_OFFSET")
                .addStatement("assert length <= LENGTH_MAX_VALUE : \"Length is too large\"")
                .addStatement("assert fieldCount <= LENGTH_MAX_VALUE : \"Field count is too large\"")
                .addStatement("buffer().putShort(offset() + LENGTH_OFFSET, (short) length, byteOrder)")
                .addStatement("buffer().putShort(offset() + FIELD_COUNT_OFFSET, (short) fieldCount, byteOrder)")
                .addStatement("final ArrayFW<V> array = super.build()")
                .addStatement("final int maxLimit = maxLimit()")
                .addStatement("limit(fieldsOffset())")
                .addStatement("int itemOffset = fieldsOffset()")
                .addStatement("itemRW.reset(this)")
                .beginControlFlow("for (int i = 0; i < fieldCount; i++)")
                .addStatement("final Flyweight item = itemRO.wrap(buffer(), itemOffset, maxLimit, array)")
                .addStatement("itemOffset = item.limit()")
                .addStatement("final Flyweight newItem = itemRW.wrap(this).rebuild((V) item, maxLength)")
                .addStatement("final int newLimit = newItem.limit()")
                .addStatement("assert newLimit <= itemOffset")
                .addStatement("limit(newLimit)")
                .endControlFlow()
                .addStatement("length = limit() - offset() - FIELD_COUNT_OFFSET")
                .addStatement("buffer().putShort(offset() + LENGTH_OFFSET, (short) length, byteOrder)")
                .addStatement("final Array16FW<V> array16 = super.build()")
                .addStatement("array16.maxLength(maxLength)")
                .addStatement("return array16")
                .build();
        }
    }
}
