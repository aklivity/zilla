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
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.BIT_UTIL_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.cog.internal.generate.TypeNames.UNSAFE_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public final class String8FlyweightGenerator extends ClassSpecGenerator
{
    private final TypeSpec.Builder classBuilder;
    private final BuilderClassBuilder builderClassBuilder;

    public String8FlyweightGenerator(
        ClassName stringType)
    {
        super(stringType.peerClass("String8FW"));

        this.classBuilder = classBuilder(thisName).superclass(stringType).addModifiers(PUBLIC, FINAL);
        this.builderClassBuilder = new BuilderClassBuilder(stringType, thisName, stringType.nestedClass("Builder"));
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder
            .addField(fieldSizeLengthConstant())
            .addField(valueField())
            .addMethod(constructor())
            .addMethod(constructorString())
            .addMethod(constructorStringAndCharset())
            .addMethod(fieldSizeLengthMethod())
            .addMethod(limitMethod())
            .addMethod(asStringMethod())
            .addMethod(tryWrapMethod())
            .addMethod(wrapMethod())
            .addMethod(valueMethod())
            .addMethod(toStringMethod())
            .addMethod(lengthMethod())
            .addType(builderClassBuilder.build())
            .build();
    }

    private FieldSpec fieldSizeLengthConstant()
    {
        return FieldSpec.builder(int.class, "FIELD_SIZE_LENGTH", PRIVATE, STATIC, FINAL)
                .initializer("$T.SIZE_OF_BYTE", BIT_UTIL_TYPE)
                .build();
    }

    private FieldSpec valueField()
    {
        return FieldSpec.builder(DIRECT_BUFFER_TYPE, "valueRO", PRIVATE, FINAL)
                .initializer("new $T(0L, 0)", UNSAFE_BUFFER_TYPE)
                .build();
    }

    private MethodSpec constructor()
    {
        return MethodSpec.constructorBuilder()
                         .addModifiers(PUBLIC)
                         .build();
    }

    private MethodSpec constructorString()
    {
        return MethodSpec.constructorBuilder()
                         .addModifiers(PUBLIC)
                         .addParameter(String.class, "value")
                         .addStatement("this(value, $T.UTF_8)", StandardCharsets.class)
                         .build();
    }

    private MethodSpec constructorStringAndCharset()
    {
        return MethodSpec.constructorBuilder()
                         .addModifiers(PUBLIC)
                         .addParameter(String.class, "value")
                         .addParameter(Charset.class, "charset")
                         .beginControlFlow("if (value != null)")
                         .addStatement("final byte[] encoded = value.getBytes(charset)")
                         .addStatement("final $T buffer = new $T(new byte[FIELD_SIZE_LENGTH + Math.max(encoded.length, 1)])",
                                 MUTABLE_DIRECT_BUFFER_TYPE, UNSAFE_BUFFER_TYPE)
                         .addStatement("buffer.putByte(0, (byte) (encoded.length & 0xFF))")
                         .addStatement("buffer.putBytes(FIELD_SIZE_LENGTH, encoded)")
                         .addStatement("wrap(buffer, 0, buffer.capacity())")
                         .nextControlFlow("else")
                         .addStatement("final $T buffer = new $T(new byte[FIELD_SIZE_LENGTH + 1])",
                                 MUTABLE_DIRECT_BUFFER_TYPE, UNSAFE_BUFFER_TYPE)
                         .addStatement("buffer.putByte(0, (byte) -1)")
                         .addStatement("wrap(buffer, 0, buffer.capacity())")
                         .endControlFlow()
                         .build();
    }

    private MethodSpec fieldSizeLengthMethod()
    {
        return methodBuilder("fieldSizeLength")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return FIELD_SIZE_LENGTH")
            .build();
    }

    private MethodSpec limitMethod()
    {
        return methodBuilder("limit")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement("return offset() + FIELD_SIZE_LENGTH + Math.max(length(), 0)")
                .build();
    }

    private MethodSpec asStringMethod()
    {
        return methodBuilder("asString")
                .addModifiers(PUBLIC)
                .addAnnotation(Override.class)
                .returns(String.class)
                .beginControlFlow("if (maxLimit() == offset() || length() == -1)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("return buffer().getStringWithoutLengthUtf8(offset() + FIELD_SIZE_LENGTH, length())")
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
                .beginControlFlow("if (null == super.tryWrap(buffer, offset, maxLimit) || " +
                                  "offset + FIELD_SIZE_LENGTH > maxLimit() || " +
                                  "limit() > maxLimit)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("int length = length()")
                .beginControlFlow("if (length != -1)")
                .addStatement("valueRO.wrap(buffer, offset + FIELD_SIZE_LENGTH, length)")
                .endControlFlow()
                .addStatement("return this")
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
                .addStatement("checkLimit(offset + FIELD_SIZE_LENGTH, maxLimit)")
                .addStatement("checkLimit(limit(), maxLimit)")
                .addStatement("int length = length()")
                .beginControlFlow("if (length != -1)")
                .addStatement("valueRO.wrap(buffer, offset + FIELD_SIZE_LENGTH, length)")
                .endControlFlow()
                .addStatement("return this")
                .build();
    }

    private MethodSpec valueMethod()
    {
        return methodBuilder("value")
                .addModifiers(PUBLIC)
                .returns(DIRECT_BUFFER_TYPE)
                .addStatement("return length() == -1 ? null : valueRO")
                .build();
    }

    private MethodSpec toStringMethod()
    {
        return methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(String.class)
                .addStatement("return String.format(\"\\\"%s\\\"\", asString())")
                .build();
    }

    private MethodSpec lengthMethod()
    {
        return methodBuilder("length")
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement("int length = buffer().getByte(offset()) & 0xFF")
                .addStatement("return length == 255 ? -1 : length")
                .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final ClassName classType;
        private final ClassName stringType;
        private final ClassName string8Type;

        private BuilderClassBuilder(
            ClassName stringType,
            ClassName string8Type,
            ClassName builderRawType)
        {
            TypeName builderType = ParameterizedTypeName.get(builderRawType, string8Type);

            this.stringType = stringType;
            this.string8Type = string8Type;
            this.classType = string8Type.nestedClass("Builder");
            this.classBuilder = classBuilder(classType.simpleName())
                    .addModifiers(PUBLIC, STATIC, FINAL)
                    .superclass(builderType);
        }

        public TypeSpec build()
        {
            return classBuilder
                .addField(valueSetField())
                .addMethod(constructor())
                .addMethod(wrapMethod())
                .addMethod(setMethod())
                .addMethod(setDirectBufferMethod())
                .addMethod(setStringMethod())
                .addMethod(checkLengthMethod())
                .addMethod(buildMethod())
                .build();
        }

        private FieldSpec valueSetField()
        {
            return FieldSpec.builder(boolean.class, "valueSet", PRIVATE)
                .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                    .addModifiers(PUBLIC)
                    .addStatement("super(new $T())", string8Type)
                    .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(classType)
                .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .addStatement("checkLimit(offset + FIELD_SIZE_LENGTH, maxLimit)")
                .addStatement("super.wrap(buffer, offset, maxLimit)")
                .addStatement("this.valueSet = false")
                .addStatement("return this")
                .build();
        }

        private MethodSpec setMethod()
        {
            return methodBuilder("set")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(classType)
                .addParameter(stringType, "value")
                .beginControlFlow("if (value.length() == -1)")
                .addStatement("int newLimit = offset() + FIELD_SIZE_LENGTH")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("buffer().putByte(offset(), (byte) -1)")
                .addStatement("limit(newLimit)")
                .nextControlFlow("else")
                .addStatement("int newLimit = offset() + FIELD_SIZE_LENGTH + value.length()")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("buffer().putByte(offset(), (byte) value.length())")
                .addStatement("buffer().putBytes(offset() + 1, value.buffer(), value.offset() + value.fieldSizeLength(), " +
                    "value.length())")
                .addStatement("limit(newLimit)")
                .endControlFlow()
                .addStatement("valueSet = true")
                .addStatement("return this")
                .build();
        }

        private MethodSpec setDirectBufferMethod()
        {
            return methodBuilder("set")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(classType)
                .addParameter(DIRECT_BUFFER_TYPE, "srcBuffer")
                .addParameter(int.class, "srcOffset")
                .addParameter(int.class, "length")
                .addStatement("checkLength(length)")
                .addStatement("int offset = offset()")
                .addStatement("int newLimit = offset + length + FIELD_SIZE_LENGTH")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("buffer().putByte(offset, (byte) length)")
                .addStatement("buffer().putBytes(offset + 1, srcBuffer, srcOffset, length)")
                .addStatement("limit(newLimit)")
                .addStatement("valueSet = true")
                .addStatement("return this")
                .build();
        }

        private MethodSpec setStringMethod()
        {
            return methodBuilder("set")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(classType)
                .addParameter(String.class, "value")
                .addParameter(Charset.class, "charset")
                .beginControlFlow("if (value == null)")
                .addStatement("int newLimit = offset() + FIELD_SIZE_LENGTH")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("buffer().putByte(offset(), (byte) -1)")
                .addStatement("limit(newLimit)")
                .nextControlFlow("else")
                .addStatement("byte[] charBytes = value.getBytes(charset)")
                .addStatement("checkLength(charBytes.length)")
                .addStatement("int newLimit = offset() + FIELD_SIZE_LENGTH + charBytes.length")
                .addStatement("checkLimit(newLimit, maxLimit())")
                .addStatement("buffer().putByte(offset(), (byte) charBytes.length)")
                .addStatement("buffer().putBytes(offset() + 1, charBytes)")
                .addStatement("limit(newLimit)")
                .endControlFlow()
                .addStatement("valueSet = true")
                .addStatement("return this")
                .build();
        }

        private MethodSpec checkLengthMethod()
        {
            return methodBuilder("checkLength")
                    .addModifiers(PRIVATE, STATIC)
                    .addParameter(int.class, "length")
                    .addStatement("final int maxLength = 254")
                    .beginControlFlow("if (length > maxLength)")
                    .addStatement(
                            "final String msg = String.format(\"length=%d is beyond maximum length=%d\", length, maxLength)")
                    .addStatement("throw new IllegalArgumentException(msg)")
                    .endControlFlow()
                    .build();
        }

        private MethodSpec buildMethod()
        {
            return methodBuilder("build")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(string8Type)
                .beginControlFlow("if (!valueSet)")
                .addStatement("set(null, $T.UTF_8)", StandardCharsets.class)
                .endControlFlow()
                .addStatement("return super.build()")
                .build();
        }
    }
}
