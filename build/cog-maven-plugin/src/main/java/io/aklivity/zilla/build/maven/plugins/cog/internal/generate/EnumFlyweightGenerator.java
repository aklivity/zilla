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
import static java.lang.String.format;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public final class EnumFlyweightGenerator extends ClassSpecGenerator
{
    private static final Map<TypeName, String> CLASS_NAMES;

    private final TypeSpec.Builder classBuilder;
    private final BuilderClassBuilder builderClassBuilder;
    private final ClassName enumTypeName;
    private final TypeName valueTypeName;
    private final TypeName unsignedValueTypeName;

    static
    {
        Map<TypeName, String> stringValueTypeByTypeName = new HashMap<>();
        stringValueTypeByTypeName.put(TypeName.BYTE, "Byte");
        stringValueTypeByTypeName.put(TypeName.SHORT, "Short");
        stringValueTypeByTypeName.put(TypeName.INT, "Int");
        stringValueTypeByTypeName.put(TypeName.LONG, "Long");
        CLASS_NAMES = stringValueTypeByTypeName;
    }

    public EnumFlyweightGenerator(
        ClassName enumName,
        ClassName flyweightName,
        ClassName enumTypeName,
        TypeName valueTypeName,
        TypeName valueVariantOfTypeName,
        TypeName unsignedValueTypeName)
    {
        super(enumName);

        this.enumTypeName = enumTypeName;
        this.classBuilder = classBuilder(thisName).superclass(flyweightName).addModifiers(PUBLIC, FINAL);
        this.builderClassBuilder = new BuilderClassBuilder(thisName, flyweightName.nestedClass("Builder"), enumTypeName,
            valueTypeName, valueVariantOfTypeName, unsignedValueTypeName);
        this.valueTypeName = valueTypeName;
        this.unsignedValueTypeName = unsignedValueTypeName;
    }

    @Override
    public TypeSpec generate()
    {
        if (isValueTypeNonPrimitive())
        {
            classBuilder.addField(nonPrimitiveField());
            if (isStringType((ClassName) valueTypeName))
            {
                classBuilder.addMethod(stringMethod());
            }
        }
        else
        {
            classBuilder.addField(fieldOffsetValueConstant())
                        .addField(fieldSizeValueConstant());
        }
        return classBuilder.addMethod(limitMethod())
                           .addMethod(getMethod())
                           .addMethod(tryWrapMethod())
                           .addMethod(wrapMethod())
                           .addMethod(toStringMethod())
                           .addType(builderClassBuilder.build())
                           .build();
    }

    private boolean isValueTypeNonPrimitive()
    {
        return valueTypeName != null && !valueTypeName.isPrimitive();
    }

    private FieldSpec nonPrimitiveField()
    {
        final String fieldName = isStringType((ClassName) valueTypeName) ? "stringRO" :
            String.format("%sRO", fieldName(valueTypeName));
        return FieldSpec.builder(valueTypeName, fieldName, PRIVATE, FINAL)
                        .initializer("new $T()", valueTypeName)
                        .build();
    }

    private FieldSpec fieldOffsetValueConstant()
    {
        return FieldSpec.builder(int.class, "FIELD_OFFSET_VALUE", PRIVATE, STATIC, FINAL)
                .initializer("0")
                .build();
    }

    private FieldSpec fieldSizeValueConstant()
    {
        String constantType = valueTypeName == null ? "BYTE" : CLASS_NAMES.get(valueTypeName).toUpperCase();
        return FieldSpec.builder(int.class, "FIELD_SIZE_VALUE", PRIVATE, STATIC, FINAL)
                .initializer(String.format("$T.SIZE_OF_%s", constantType), BIT_UTIL_TYPE)
                .build();
    }

    private MethodSpec stringMethod()
    {
        return methodBuilder("string")
                .addModifiers(PUBLIC)
                .returns(valueTypeName)
                .addStatement("return stringRO")
                .build();
    }

    private MethodSpec limitMethod()
    {
        final String limitMethod = !isValueTypeNonPrimitive() ? "" : isStringType((ClassName) valueTypeName) ?
            "stringRO.limit()" : String.format("%sRO.limit()", fieldName(valueTypeName));
        final String returnStatement =
            String.format("return %s", isValueTypeNonPrimitive() ? limitMethod : "offset() + FIELD_SIZE_VALUE");
        return methodBuilder("limit")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(int.class)
                .addStatement(returnStatement)
                .build();
    }

    private MethodSpec getMethod()
    {
        String bufferType = valueTypeName == null ? "Byte" : CLASS_NAMES.get(valueTypeName);
        String unsignedHex = "";
        if (unsignedValueTypeName != null)
        {
            if (valueTypeName.equals(TypeName.BYTE))
            {
                unsignedHex = " & 0xFF";
            }
            else if (valueTypeName.equals(TypeName.SHORT))
            {
                unsignedHex = " & 0xFFFF";
            }
            else if (valueTypeName.equals(TypeName.INT))
            {
                unsignedHex = " & 0xFFFF_FFFFL";
            }
        }

        String returnStatement = String.format("return %s", isValueTypeNonPrimitive() ? isStringType((ClassName) valueTypeName) ?
            "stringRO.asString() != null ? $T.valueOf(stringRO.asString().toUpperCase()) : null" :
            String.format("$T.valueOf(%sRO.get())", fieldName(valueTypeName)) :
            String.format("$T.valueOf(buffer().get%s(offset() + FIELD_OFFSET_VALUE)%s)", bufferType, unsignedHex));
        return methodBuilder("get")
                .addModifiers(PUBLIC)
                .returns(enumTypeName)
                .addStatement(returnStatement, enumTypeName)
                .build();
    }

    private MethodSpec tryWrapMethod()
    {
        MethodSpec.Builder builder = methodBuilder("tryWrap");
        builder.addAnnotation(Override.class)
               .addModifiers(PUBLIC)
               .addParameter(DIRECT_BUFFER_TYPE, "buffer")
               .addParameter(int.class, "offset")
               .addParameter(int.class, "maxLimit")
               .returns(thisName);
        if (isValueTypeNonPrimitive())
        {
            final String fieldName = isStringType((ClassName) valueTypeName) ? "string" : fieldName(valueTypeName);
            builder.beginControlFlow("if (super.tryWrap(buffer, offset, maxLimit) == null)")
                   .addStatement("return null")
                   .endControlFlow()
                   .beginControlFlow("if ($LRO.tryWrap(buffer, offset, maxLimit) == null)", fieldName)
                   .addStatement("return null")
                   .endControlFlow()
                   .beginControlFlow("if (limit() > maxLimit)")
                   .addStatement("return null")
                   .endControlFlow();
        }
        else
        {
            builder.beginControlFlow("if (super.tryWrap(buffer, offset, maxLimit) == null || limit() > maxLimit)")
                   .addStatement("return null")
                   .endControlFlow();
        }

        return builder.addStatement("return this")
                      .build();
    }

    private MethodSpec wrapMethod()
    {
        MethodSpec.Builder builder = methodBuilder("wrap");
        builder.addAnnotation(Override.class)
               .addModifiers(PUBLIC)
               .addParameter(DIRECT_BUFFER_TYPE, "buffer")
               .addParameter(int.class, "offset")
               .addParameter(int.class, "maxLimit")
               .returns(thisName)
               .addStatement("super.wrap(buffer, offset, maxLimit)");
        if (isValueTypeNonPrimitive())
        {
            final String fieldName = isStringType((ClassName) valueTypeName) ? "string" : fieldName(valueTypeName);
            builder.addStatement("$LRO.wrap(buffer, offset, maxLimit)", fieldName);
        }
        return builder.addStatement("checkLimit(limit(), maxLimit)")
                      .addStatement("return this")
                      .build();
    }

    private MethodSpec toStringMethod()
    {
        return methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(String.class)
                .addStatement("return maxLimit() == offset() ? \"null\" : get().toString()")
                .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final ClassName enumTypeName;
        private final ClassName classType;
        private final ClassName enumName;
        private final TypeName valueTypeName;
        private final TypeName valueVariantOfTypeName;
        private final TypeName unsignedValueTypeName;

        private BuilderClassBuilder(
            ClassName enumName,
            ClassName builderRawType,
            ClassName enumTypeName,
            TypeName valueTypeName,
            TypeName valueVariantOfTypeName,
            TypeName unsignedValueTypeName)
        {
            TypeName builderType = ParameterizedTypeName.get(builderRawType, enumName);

            this.enumName = enumName;
            this.enumTypeName = enumTypeName;
            this.classType = enumName.nestedClass("Builder");
            this.classBuilder = classBuilder(classType.simpleName())
                    .addModifiers(PUBLIC, STATIC, FINAL)
                    .superclass(builderType);
            this.valueTypeName = valueTypeName;
            this.valueVariantOfTypeName = valueVariantOfTypeName;
            this.unsignedValueTypeName = unsignedValueTypeName;
        }

        public TypeSpec build()
        {
            classBuilder.addField(fieldValueSet())
                .addMethod(constructor())
                .addMethod(wrapMethod())
                .addMethod(setMethod())
                .addMethod(setEnumMethod());
            if (isValueNonPrimitiveType())
            {
                classBuilder.addField(nonPrimitiveField());
            }
            return classBuilder.addMethod(buildMethod()).build();
        }

        private boolean isValueNonPrimitiveType()
        {
            return valueTypeName != null && !valueTypeName.isPrimitive();
        }

        private FieldSpec nonPrimitiveField()
        {
            final String fieldName = isStringType((ClassName) valueTypeName) ? "stringRW" :
                String.format("%sRW", fieldName(valueTypeName));
            ClassName classType = (ClassName) valueTypeName;
            TypeName builderType = classType.nestedClass("Builder");
            return FieldSpec.builder(builderType, fieldName, PRIVATE, FINAL)
                            .initializer("new $T.Builder()", valueTypeName)
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
                    .addStatement("super(new $T())", enumName)
                    .build();
        }

        private MethodSpec wrapMethod()
        {
            MethodSpec.Builder builder = methodBuilder("wrap");
            builder.addModifiers(PUBLIC)
                   .returns(enumName.nestedClass("Builder"))
                   .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                   .addParameter(int.class, "offset")
                   .addParameter(int.class, "maxLimit");
            if (isValueNonPrimitiveType())
            {
                final String fieldName = isStringType((ClassName) valueTypeName) ? "string" : fieldName(valueTypeName);
                builder.addStatement("$LRW.wrap(buffer, offset, maxLimit)", fieldName);
            }
            return builder
                    .addStatement("super.wrap(buffer, offset, maxLimit)")
                    .addStatement("return this")
                    .build();
        }

        private MethodSpec setMethod()
        {
            MethodSpec.Builder builder = methodBuilder("set");
            builder.addModifiers(PUBLIC)
                   .returns(enumName.nestedClass("Builder"))
                   .addParameter(enumName, "value");
            if (isValueNonPrimitiveType())
            {
                final boolean isStringType = isStringType((ClassName) valueTypeName);
                final String fieldName = isStringType ? "string" : fieldName(valueTypeName);
                final String value = isStringType ? ".string()" : ".get().value()";
                final String limit = isStringType ? ".build()" : "";
                if (valueVariantOfTypeName != valueTypeName && valueVariantOfTypeName instanceof ClassName &&
                    isStringType((ClassName) valueVariantOfTypeName))
                {
                    builder.addStatement("$LRW.set(value$L, $T.UTF_8)", fieldName, value, StandardCharsets.class);
                }
                else
                {
                    builder.addStatement("$LRW.set(value$L)", fieldName, value);
                }
                builder.addStatement("limit($LRW$L.limit())", fieldName, limit);
            }
            else
            {
                builder.addStatement("int newLimit = offset() + value.sizeof()")
                       .addStatement("checkLimit(newLimit, maxLimit())")
                       .addStatement("buffer().putBytes(offset(), value.buffer(), value.offset(), value.sizeof())")
                       .addStatement("limit(newLimit)");
            }
            return builder.addStatement("valueSet = true")
                          .addStatement("return this")
                          .build();
        }

        private MethodSpec setEnumMethod()
        {
            MethodSpec.Builder builder = methodBuilder("set");
            builder.addModifiers(PUBLIC)
                   .returns(enumName.nestedClass("Builder"))
                   .addParameter(enumTypeName, "value");
            if (isValueNonPrimitiveType())
            {
                final boolean isStringType = isStringType((ClassName) valueTypeName);
                final String fieldName = isStringType ? "string" : fieldName(valueTypeName);
                final String charset = isStringType ? ", charset" : "";
                final String limit = isStringType ? ".build()" : "";

                if (isStringType)
                {
                    builder.addParameter(Charset.class, "charset");
                }
                if (valueVariantOfTypeName != valueTypeName && valueVariantOfTypeName instanceof ClassName &&
                    isStringType((ClassName) valueVariantOfTypeName))
                {
                    builder.addStatement("$LRW.set(value.value()$L, $T.UTF_8)", fieldName, charset, StandardCharsets.class);
                }
                else
                {
                    builder.addStatement("$LRW.set(value.value()$L)", fieldName, charset);
                }
                builder.addStatement("limit($LRW$L.limit())", fieldName, limit);
            }
            else
            {
                final String methodName = isParameterizedType() ? "value" : "ordinal";
                final String bufferType = isParameterizedType() ? CLASS_NAMES.get(valueTypeName) : "Byte";
                String castType = "";
                String unsignedHex = "";
                if (!isParameterizedType())
                {
                    castType = "(byte) ";
                }
                else if (unsignedValueTypeName != null)
                {
                    if (valueTypeName.equals(TypeName.BYTE))
                    {
                        unsignedHex = " & 0xFF)";
                        castType = "(byte) (";
                    }
                    else if (valueTypeName.equals(TypeName.SHORT))
                    {
                        unsignedHex = " & 0xFFFF)";
                        castType = "(short) (";
                    }
                    else if (valueTypeName.equals(TypeName.INT))
                    {
                        unsignedHex = " & 0xFFFF_FFFFL)";
                        castType = "(int) (";
                    }
                }
                builder.addStatement("MutableDirectBuffer buffer = buffer()")
                       .addStatement("int offset = offset()")
                       .addStatement("int newLimit = offset + FIELD_SIZE_VALUE")
                       .addStatement("checkLimit(newLimit, maxLimit())")
                       .addStatement(String.format("buffer.put%s(offset, %svalue.%s()%s)", bufferType, castType, methodName,
                           unsignedHex))
                       .addStatement("limit(newLimit)");
            }
            return builder.addStatement("valueSet = true")
                          .addStatement("return this")
                          .build();
        }

        private MethodSpec buildMethod()
        {
            return methodBuilder("build")
                    .addAnnotation(Override.class)
                    .addModifiers(PUBLIC)
                    .beginControlFlow("if (!valueSet)")
                    .addStatement("throw new IllegalStateException($S)",
                                  format("%s not set", enumTypeName.simpleName()))
                    .endControlFlow()
                    .addStatement("return super.build()")
                    .returns(enumName)
                    .build();
        }

        private boolean isParameterizedType()
        {
            return valueTypeName != null;
        }
    }

    private static boolean isStringType(
        ClassName classType)
    {
        return isStringFWType(classType) || isString8Type(classType) || isString16Type(classType) || isString32Type(classType);
    }

    private static boolean isStringFWType(
        ClassName classType)
    {
        String name = classType.simpleName();
        return "StringFW".equals(name);
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

    private static String fieldName(
        TypeName type)
    {
        String fieldName =  ((ClassName) type).simpleName();
        return String.format("%s%s", Character.toLowerCase(fieldName.charAt(0)), fieldName.substring(1, fieldName.length() - 2));
    }
}
