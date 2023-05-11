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
import static com.squareup.javapoet.TypeSpec.enumBuilder;
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.LONG_2_OBJECT_HASH_MAP_TYPE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.lang.model.element.Modifier;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

public final class EnumTypeGenerator extends ClassSpecGenerator
{
    private final TypeSpec.Builder builder;
    private final NameConstantGenerator nameConstant;
    private final ValueOfMethodGenerator valueOfMethod;
    private final TypeName valueTypeName;
    private final TypeName unsignedValueTypeName;
    private ValueMethodGenerator valueMethod;
    private ConstructorGenerator constructor;
    private LongHashMapGenerator longHashMap;

    public EnumTypeGenerator(
        ClassName enumTypeName,
        TypeName valueTypeName,
        TypeName unsignedValueTypeName)
    {
        super(enumTypeName);
        this.builder = enumBuilder(enumTypeName).addModifiers(PUBLIC);
        this.nameConstant = new NameConstantGenerator(enumTypeName, valueTypeName, unsignedValueTypeName, builder);
        this.valueOfMethod = new ValueOfMethodGenerator(enumTypeName);
        this.valueTypeName = valueTypeName;
        this.unsignedValueTypeName = unsignedValueTypeName;
        if (isParameterizedType())
        {
            this.valueMethod = new ValueMethodGenerator();
            this.constructor = new ConstructorGenerator();
        }
        if (isValueTypeLong())
        {
            this.longHashMap = new LongHashMapGenerator(enumTypeName, builder);
        }
    }

    public TypeSpecGenerator<ClassName> addValue(
        String name,
        Object value)
    {
        nameConstant.addValue(name, value);
        valueOfMethod.addValue(name, value);

        if (isValueTypeLong())
        {
            longHashMap.addValue(name, value);
        }
        return this;
    }

    @Override
    public TypeSpec generate()
    {
        nameConstant.build();
        if (isParameterizedType())
        {
            if (isValueTypeLong())
            {
                longHashMap.generate();
            }
            if (valueTypeName.isPrimitive())
            {
                builder.addField(isTypeUnsignedInt() ? unsignedValueTypeName : valueTypeName, "value", Modifier.PRIVATE,
                    Modifier.FINAL);
            }
            else
            {
                builder.addField(String.class, "value", Modifier.PRIVATE, Modifier.FINAL);
            }
            builder.addMethod(constructor.generate())
                   .addMethod(valueMethod.generate());
        }

        return builder.addMethod(valueOfMethod.generate())
                      .build();
    }

    private static final class NameConstantGenerator extends ClassSpecMixinGenerator
    {
        private final TypeName valueTypeName;
        private final TypeName unsignedValueTypeName;
        private NameConstantGenerator(
            ClassName thisType,
            TypeName valueTypeName,
            TypeName unsignedValueTypeName,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            this.valueTypeName = valueTypeName;
            this.unsignedValueTypeName = unsignedValueTypeName;
        }

        public NameConstantGenerator addValue(
            String name,
            Object value)
        {
            if (value == null)
            {
                builder.addEnumConstant(name);
            }
            else
            {
                TypeName valueType = unsignedValueTypeName == null ? valueTypeName : unsignedValueTypeName;
                if (valueType.equals(TypeName.BYTE) || valueType.equals(TypeName.SHORT))
                {
                    builder.addEnumConstant(name,
                        TypeSpec.anonymousClassBuilder("($L) $L", valueType, value).build());
                }
                else if (valueType.equals(TypeName.LONG))
                {
                    String longValue = value instanceof String ? "$L" : "$LL";
                    builder.addEnumConstant(name, TypeSpec.anonymousClassBuilder(longValue, value).build());
                }
                else
                {
                    builder.addEnumConstant(name, TypeSpec.anonymousClassBuilder("$L", value).build());
                }
            }
            return this;
        }
    }

    private static final class LongHashMapGenerator extends ClassSpecMixinGenerator
    {
        private final CodeBlock.Builder putStatementsBuilder;
        private int count;

        private LongHashMapGenerator(
            ClassName thisType,
            TypeSpec.Builder builder)
        {
            super(thisType, builder);
            builder.addField(ParameterizedTypeName.get(LONG_2_OBJECT_HASH_MAP_TYPE, thisType), "VALUE_BY_LONG",
                Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
            putStatementsBuilder = CodeBlock.builder();
        }

        public LongHashMapGenerator addValue(
            String name,
            Object value)
        {
            String putStatement = value instanceof String ? "valueByLong.put($L, $L)" : "valueByLong.put($LL, $L)";
            putStatementsBuilder.addStatement(putStatement, value, name);
            count++;
            return this;
        }

        public TypeSpec generate()
        {
            return builder.addStaticBlock(CodeBlock.builder()
                .addStatement("$T<$T> valueByLong = new $T<>($L, 0.9f)", LONG_2_OBJECT_HASH_MAP_TYPE,
                    thisType, LONG_2_OBJECT_HASH_MAP_TYPE, count)
                .add(putStatementsBuilder.build())
                .addStatement("VALUE_BY_LONG = valueByLong")
                .build()).build();
        }
    }

    private final class ConstructorGenerator extends MethodSpecGenerator
    {
        private ConstructorGenerator()
        {
            super(constructorBuilder().addStatement("this.$L = $L", "value", "value"));
        }

        @Override
        public MethodSpec generate()
        {
            if (valueTypeName.isPrimitive())
            {
                builder.addParameter(isTypeUnsignedInt() ? unsignedValueTypeName : valueTypeName, "value");
            }
            else
            {
                builder.addParameter(String.class, "value");
            }
            return builder.build();
        }
    }

    private final class ValueMethodGenerator extends MethodSpecGenerator
    {
        private ValueMethodGenerator()
        {
            super(methodBuilder("value")
                .addModifiers(PUBLIC)
                .addStatement("return $L", "value"));
        }

        @Override
        public MethodSpec generate()
        {
            if (valueTypeName.isPrimitive())
            {
                builder.returns(isTypeUnsignedInt() ? unsignedValueTypeName : valueTypeName);
            }
            else
            {
                builder.returns(String.class);
            }
            return builder.build();
        }
    }

    private final class ValueOfMethodGenerator extends MethodSpecGenerator
    {
        private final List<String> constantNames = new LinkedList<>();
        private final Map<String, Object> valueByConstantName = new HashMap<>();

        private ValueOfMethodGenerator(
            ClassName enumName)
        {
            super(methodBuilder("valueOf")
                    .addModifiers(PUBLIC, STATIC)
                    .returns(enumName));
        }

        public ValueOfMethodGenerator addValue(
            String name,
            Object value)
        {
            constantNames.add(name);
            if (value != null)
            {
                valueByConstantName.put(name, value);
            }
            return this;
        }

        @Override
        public MethodSpec generate()
        {
            final String discriminant = isParameterizedType() ? "value" : "ordinal";

            builder.addParameter(isParameterizedType() ? (isTypeUnsignedInt() ? unsignedValueTypeName : valueTypeName) :
                TypeName.INT, discriminant);

            if (isValueTypeLong())
            {
                builder.addStatement("return VALUE_BY_LONG.get(value)");
            }
            else
            {
                if (isValueTypeString())
                {
                    builder.addStatement("String kind = $L.asString()", discriminant);
                }
                builder.beginControlFlow("switch ($L)", isValueTypeString() ? "kind" : discriminant);

                for (int index = 0; index < constantNames.size(); index++)
                {
                    String enumConstant = constantNames.get(index);

                    Object kind = valueByConstantName.get(enumConstant) == null ? index : valueByConstantName.get(enumConstant);
                    builder.beginControlFlow("case $L:", kind)
                           .addStatement("return $N", enumConstant)
                           .endControlFlow();
                }

                builder.endControlFlow().addStatement("return null");
            }
            return builder.build();
        }
    }

    private boolean isParameterizedType()
    {
        return valueTypeName != null;
    }

    private boolean isTypeUnsignedInt()
    {
        return valueTypeName != null && unsignedValueTypeName != null;
    }

    private boolean isValueTypeLong()
    {
        if (valueTypeName == null)
        {
            return false;
        }
        return Objects.requireNonNullElse(unsignedValueTypeName, valueTypeName).equals(TypeName.LONG);
    }

    private boolean isValueTypeString()
    {
        return valueTypeName != null && !valueTypeName.isPrimitive();
    }
}
