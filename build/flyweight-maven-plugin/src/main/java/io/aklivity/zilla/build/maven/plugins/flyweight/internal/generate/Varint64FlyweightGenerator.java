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
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.DIRECT_BUFFER_TYPE;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.MUTABLE_DIRECT_BUFFER_TYPE;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public final class Varint64FlyweightGenerator extends ClassSpecGenerator
{
    private final TypeSpec.Builder classBuilder;
    private final BuilderClassBuilder builderClassBuilder;

    public Varint64FlyweightGenerator(
        ClassName flyweightType)
    {
        super(flyweightType.peerClass("Varint64FW"));

        this.classBuilder = classBuilder(thisName).superclass(flyweightType).addModifiers(PUBLIC, FINAL);
        this.builderClassBuilder = new BuilderClassBuilder(thisName, flyweightType.nestedClass("Builder"));
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder.addField(fieldSize())
                           .addMethod(limitMethod())
                           .addMethod(valueMethod())
                           .addMethod(tryWrapMethod())
                           .addMethod(wrapMethod())
                           .addMethod(toStringMethod())
                           .addMethod(length0Method())
                           .addType(builderClassBuilder.build())
                           .build();
    }

    private FieldSpec fieldSize()
    {
        return FieldSpec.builder(int.class, "size", PRIVATE)
                .build();
    }

    private MethodSpec limitMethod()
    {
        return methodBuilder("limit")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement("return offset() + size")
                .build();
    }

    private MethodSpec valueMethod()
    {
        return methodBuilder("value")
                .addModifiers(PUBLIC)
                .returns(long.class)
                .addStatement("long value = 0L")
                .addStatement("int i = 0;")
                .addStatement("long b")
                .addStatement("int pos  = offset()")
                .beginControlFlow("while (((b = buffer().getByte(pos++)) & 0x80L) != 0)")
                .addStatement("value |= (b & 0x7F) << i")
                .addStatement("i += 7")
                .beginControlFlow("if (i > 65)")
                .addStatement("throw new $T($S)", IllegalArgumentException.class, "varint64 value too long")
                .endControlFlow()
                .endControlFlow()
                .addStatement("long unsigned = value | (b << i);")
                .addStatement("long result = (((unsigned << 63) >> 63) ^ unsigned) >> 1")
                .addStatement("result = result ^ (unsigned & (1L << 63))")
                .addStatement("return result")
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
                .beginControlFlow("if (null == super.tryWrap(buffer, offset, maxLimit) || maxLimit - offset  < 1)")
                .addStatement("return null")
                .nextControlFlow("else if (maxLimit - offset >= 10 && " +
                        "(buffer.getLong(offset) & 0x80808080_80808080L) == 0x80808080_80808080L && " +
                        "(buffer.getByte(offset + Long.BYTES) & 0x80) == 0x80 && " +
                        "(buffer.getByte(offset + Long.BYTES + Byte.BYTES) & 0xfe) != 0)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("size = length0()")
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
                .addModifiers(PUBLIC)
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .returns(thisName)
                .addStatement("super.wrap(buffer, offset, maxLimit)")
                .addStatement("checkLimit(offset + 1, maxLimit)")
                .addStatement("size = length0()")
                .addStatement("checkLimit(limit(), maxLimit)")
                .addStatement("return this")
                .build();
    }

    private MethodSpec toStringMethod()
    {
        return methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(String.class)
                .addStatement("return Long.toString(value())")
                .build();
    }

    private MethodSpec length0Method()
    {
        return methodBuilder("length0")
                .addModifiers(PRIVATE)
                .returns(int.class)
                .addStatement("int pos = offset()")
                .addStatement("byte b = (byte) 0")
                .addStatement("final int maxPos = Math.min(pos + 10,  maxLimit())")
                .beginControlFlow("while (pos < maxPos && ((b = buffer().getByte(pos)) & 0x80L) != 0)")
                .addStatement("pos++")
                .endControlFlow()
                .addStatement("int size = 1 + pos - offset()")
                .addStatement("int mask = size < 10 ? 0x80 : 0xfe") // 64 % 7 = 1 bit allowed only in 10th byte
                .beginControlFlow("if ((b & mask) != 0 && size >= 10)")
                .addStatement("throw new $T(String.format($S, offset()))", IllegalArgumentException.class,
                        "varint64 value at offset %d exceeds 64 bits")
                .endControlFlow()
                .addStatement("return size")
                .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final ClassName classType;
        private final ClassName flyweightType;

        private BuilderClassBuilder(
            ClassName flyweightType,
            ClassName builderRawType)
        {
            TypeName builderType = ParameterizedTypeName.get(builderRawType, flyweightType);

            this.flyweightType = flyweightType;
            this.classType = flyweightType.nestedClass("Builder");
            this.classBuilder = classBuilder(classType.simpleName())
                    .addModifiers(PUBLIC, STATIC, FINAL)
                    .superclass(builderType);
        }

        public TypeSpec build()
        {
            return classBuilder.addField(fieldValueSet())
                    .addMethod(constructor())
                    .addMethod(wrapMethod())
                    .addMethod(setMethod())
                    .addMethod(buildMethod())
                    .build();
        }

        private FieldSpec fieldValueSet()
        {
            return FieldSpec.builder(boolean.class, "valueSet", PRIVATE)
                    .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                    .addModifiers(PUBLIC)
                    .addStatement("super(new $T())", flyweightType)
                    .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .returns(flyweightType.nestedClass("Builder"))
                    .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                    .addParameter(int.class, "offset")
                    .addParameter(int.class, "maxLimit")
                    .addStatement("checkLimit(offset + 1, maxLimit)")
                    .addStatement("super.wrap(buffer, offset, maxLimit)")
                    .addStatement("this.valueSet = false")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec setMethod()
        {
            return methodBuilder("set")
                    .addModifiers(PUBLIC)
                    .returns(flyweightType.nestedClass("Builder"))
                    .addParameter(long.class, "value")
                    .addStatement("long zigzagged = (value << 1) ^ (value >> 63)")
                    .addStatement("int pos = offset()")
                    .addStatement("int bits = zigzagged == 0L ? 1 : 1 + $1T.numberOfTrailingZeros($1T.highestOneBit(zigzagged))",
                            java.lang.Long.class)
                    .addStatement("int size = bits / 7")
                    .beginControlFlow("if (size * 7 < bits)")
                        .addStatement("size++")
                    .endControlFlow()
                    .addStatement("int newLimit = pos + size")
                    .addStatement("checkLimit(newLimit, maxLimit())")
                    .beginControlFlow("while ((zigzagged & 0xFFFFFFFF_FFFFFF80L) != 0L)")
                        .addStatement("buffer().putByte(pos++, (byte) ((zigzagged & 0x7FL) | 0x80L))")
                        .addStatement("zigzagged >>>= 7")
                    .endControlFlow()
                    .addStatement("buffer().putByte(pos, (byte) (zigzagged & 0x7FL))")
                    .addStatement("limit(newLimit)")
                    .addStatement("valueSet = true")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec buildMethod()
        {
            return methodBuilder("build")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .beginControlFlow("if (!valueSet)")
                        .addStatement("throw new $T($S)", IllegalArgumentException.class, "value not set")
                    .endControlFlow()
                    .addStatement("return super.build()")
                    .returns(flyweightType)
                    .build();
        }
    }
}
