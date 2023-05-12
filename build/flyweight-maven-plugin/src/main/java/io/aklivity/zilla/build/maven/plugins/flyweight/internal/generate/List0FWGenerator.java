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
import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.generate.TypeNames.UNSAFE_BUFFER_TYPE;
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

public final class List0FWGenerator extends ClassSpecGenerator
{
    private final TypeSpec.Builder classBuilder;
    private final BuilderClassBuilder builderClassBuilder;

    public List0FWGenerator(
        ClassName flyweightType,
        ClassName listType)
    {
        super(listType.peerClass("List0FW"));

        this.classBuilder = classBuilder(thisName).superclass(listType).addModifiers(PUBLIC, FINAL);
        this.builderClassBuilder = new BuilderClassBuilder(thisName, flyweightType.nestedClass("Builder"),
            listType.nestedClass("Builder"));
    }

    @Override
    public TypeSpec generate()
    {
        return classBuilder.addField(fieldsEmptyValueField())
            .addField(lengthSizeConstant())
            .addField(lengthValueConstant())
            .addField(fieldCountValueConstant())
            .addMethod(limitMethod())
            .addMethod(lengthMethod())
            .addMethod(fieldCountMethod())
            .addMethod(fieldsMethod())
            .addMethod(tryWrapMethod())
            .addMethod(wrapMethod())
            .addMethod(toStringMethod())
            .addType(builderClassBuilder.build())
            .build();
    }

    private FieldSpec fieldsEmptyValueField()
    {
        return FieldSpec.builder(DIRECT_BUFFER_TYPE, "FIELDS_EMPTY_VALUE", PRIVATE, STATIC, FINAL)
            .initializer("new $T(0L, 0)", UNSAFE_BUFFER_TYPE)
            .build();
    }

    private FieldSpec lengthSizeConstant()
    {
        return FieldSpec.builder(int.class, "LENGTH_SIZE", PRIVATE, STATIC, FINAL)
            .initializer("0")
            .build();
    }

    private FieldSpec lengthValueConstant()
    {
        return FieldSpec.builder(int.class, "LENGTH_VALUE", PRIVATE, STATIC, FINAL)
            .initializer("0")
            .build();
    }

    private FieldSpec fieldCountValueConstant()
    {
        return FieldSpec.builder(int.class, "FIELD_COUNT_VALUE", PRIVATE, STATIC, FINAL)
            .initializer("0")
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

    private MethodSpec lengthMethod()
    {
        return methodBuilder("length")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return LENGTH_VALUE")
            .build();
    }

    private MethodSpec fieldCountMethod()
    {
        return methodBuilder("fieldCount")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(int.class)
            .addStatement("return FIELD_COUNT_VALUE")
            .build();
    }

    private MethodSpec fieldsMethod()
    {
        return methodBuilder("fields")
            .addAnnotation(Override.class)
            .addModifiers(PUBLIC)
            .returns(DIRECT_BUFFER_TYPE)
            .addStatement("return FIELDS_EMPTY_VALUE")
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
            .beginControlFlow("if (super.tryWrap(buffer, offset, maxLimit) == null)")
            .addStatement("return null")
            .endControlFlow()
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
            .addStatement("return String.format(\"list0<%d, %d>\", length(), fieldCount())")
            .build();
    }

    private static final class BuilderClassBuilder
    {
        private final TypeSpec.Builder classBuilder;
        private final ClassName classType;
        private final ClassName listType;
        private final ClassName visitorType;

        private BuilderClassBuilder(
            ClassName listType,
            ClassName flyweightBuilderRawType,
            ClassName listBuilderRawType)
        {
            TypeName builderType = ParameterizedTypeName.get(listBuilderRawType, listType);
            this.listType = listType;
            this.classType = listType.nestedClass("Builder");
            this.classBuilder = classBuilder(classType.simpleName())
                .addModifiers(PUBLIC, STATIC, FINAL)
                .superclass(builderType);
            this.visitorType = flyweightBuilderRawType.nestedClass("Visitor");
        }

        public TypeSpec build()
        {
            return classBuilder
                .addMethod(constructor())
                .addMethod(fieldMethod())
                .addMethod(fieldsMethodViaVisitor())
                .addMethod(fieldsMethodViaBuffer())
                .addMethod(wrapMethod())
                .addMethod(buildMethod())
                .build();
        }

        private MethodSpec constructor()
        {
            return constructorBuilder()
                .addModifiers(PUBLIC)
                .addStatement("super(new List0FW())")
                .build();
        }

        private MethodSpec fieldMethod()
        {
            return methodBuilder("field")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(listType.nestedClass("Builder"))
                .addParameter(visitorType, "visitor")
                .addStatement("throw new UnsupportedOperationException()")
                .build();
        }

        private MethodSpec fieldsMethodViaVisitor()
        {
            return methodBuilder("fields")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(listType.nestedClass("Builder"))
                .addParameter(int.class, "fieldCount")
                .addParameter(visitorType, "visitor")
                .addStatement("assert fieldCount == 0")
                .addStatement("return this")
                .build();
        }

        private MethodSpec fieldsMethodViaBuffer()
        {
            return methodBuilder("fields")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(listType.nestedClass("Builder"))
                .addParameter(int.class, "fieldCount")
                .addParameter(DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "index")
                .addParameter(int.class, "length")
                .addStatement("assert fieldCount == 0")
                .addStatement("return this")
                .build();
        }

        private MethodSpec wrapMethod()
        {
            return methodBuilder("wrap")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(listType.nestedClass("Builder"))
                .addParameter(MUTABLE_DIRECT_BUFFER_TYPE, "buffer")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "maxLimit")
                .addStatement("super.wrap(buffer, offset, maxLimit)")
                .addStatement("checkLimit(limit(), maxLimit)")
                .addStatement("return this")
                .build();
        }

        private MethodSpec buildMethod()
        {
            return methodBuilder("build")
                .addAnnotation(Override.class)
                .addModifiers(PUBLIC)
                .returns(listType)
                .addStatement("return super.build()")
                .build();
        }
    }
}
