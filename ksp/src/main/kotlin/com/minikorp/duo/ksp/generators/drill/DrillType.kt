@file:OptIn(KotlinPoetKspPreview::class)

package com.minikorp.duo.ksp.generators.drill

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.minikorp.drill.DefaultDrillable
import com.minikorp.drill.Drillable
import com.minikorp.duo.ksp.CompilationException
import com.minikorp.duo.ksp.asClassName
import com.minikorp.duo.ksp.codeGenerator
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.writeTo

object DrillTypeUtils {
    val defaultDrillableClassName = DefaultDrillable::class.asClassName()
    val drillableNullableClassName = Drillable::class.asClassName().parameterizedBy(STAR).copy(nullable = true)
}

/**
 * A data class that is being made mutable
 */
class DrillType(val classDeclaration: KSClassDeclaration) {
    var compiled: Boolean = false
    var emitted: Boolean = false

    val dependencies: MutableList<DrillType> = ArrayList()
    val properties: MutableMap<String, DrillProperty> = HashMap()
    val immutableClassName = classDeclaration.asClassName()
    val mutableClassName: ClassName = ClassName(
        immutableClassName.packageName, immutableClassName.simpleName + "Mutable"
    )

    fun compile() {
        if (compiled) return; compiled = true

        if (!classDeclaration.modifiers.contains(Modifier.DATA)) {
            throw CompilationException(classDeclaration, "Drill classes must be data classes")
        }

        classDeclaration.getAllProperties().forEach {
            val prop = DrillProperty(this, it)
            prop.compile()
            properties[prop.name] = prop
        }
    }

    fun emit() {
        if (emitted) return; emitted = true

        val fileSpec = FileSpec.builder(
            mutableClassName.packageName, mutableClassName.simpleName
        )

        val classSpec = TypeSpec.classBuilder(mutableClassName)
            .superclass(DrillTypeUtils.defaultDrillableClassName.parameterizedBy(immutableClassName))
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(Drillable<*>::ref.name, immutableClassName)
                    .addParameter(
                        Drillable<*>::parent.name, DrillTypeUtils.drillableNullableClassName
                    ).build()
            ).addSuperclassConstructorParameter(Drillable<*>::ref.name)
            .addSuperclassConstructorParameter(Drillable<*>::parent.name)

        classSpec.addFunction(
            FunSpec.builder(Drillable<*>::freeze.name).addModifiers(KModifier.OVERRIDE).returns(immutableClassName)
                .beginControlFlow("if (${Drillable<*>::dirty.name}())")
                .addCode(CodeBlock.builder().addStatement("return ${Drillable<*>::ref.name}().copy(").indent().apply {
                    properties.values.forEach { prop ->
                        add("${prop.name} = ")
                        add(prop.freeze)
                        add(",\n")
                    }
                }.unindent().addStatement(")").build()).endControlFlow()
                .addStatement("return ${Drillable<*>::ref.name}()").build()
        )

        val toMutableExtension = FunSpec.builder("toMutable")
            .returns(mutableClassName)
            .receiver(immutableClassName)
            .addParameter(
                ParameterSpec.builder(
                    "parent",
                    DrillTypeUtils.drillableNullableClassName
                ).defaultValue("null").build()
            )
            .addStatement("return %T(this, parent)", mutableClassName)
            .build()

        val produceExtension = FunSpec.builder("produce")
            .addModifiers(KModifier.INLINE)
            .addParameter(
                ParameterSpec
                    .builder(
                        "block", LambdaTypeName.get(
                            receiver = mutableClassName,
                            returnType = Unit::class.asClassName()
                        )
                    )
                    .addModifiers(KModifier.CROSSINLINE)
                    .build()
            )
            .returns(mutableClassName)
            .receiver(immutableClassName)
            .addStatement("val mutable = this.${toMutableExtension.name}()")
            .addStatement("mutable.block()")
            .addStatement("return mutable")
            .build()

        val mutateExtension = FunSpec.builder("mutate")
            .addModifiers(KModifier.INLINE)
            .addParameter(
                ParameterSpec
                    .builder(
                        "block", LambdaTypeName.get(
                            receiver = mutableClassName,
                            returnType = Unit::class.asClassName()
                        )
                    )
                    .addModifiers(KModifier.CROSSINLINE)
                    .build()
            )
            .returns(immutableClassName)
            .receiver(immutableClassName)
            .addStatement("return this.${produceExtension.name}(block).freeze()")
            .build()

        fileSpec.addFunction(produceExtension)
        fileSpec.addFunction(mutateExtension)
        fileSpec.addFunction(toMutableExtension)
        fileSpec.addImport(Drillable::class.asClassName().packageName, "toMutable")
        dependencies.forEach {
            fileSpec.addImport(it.immutableClassName.packageName, "toMutable")
        }

        properties.values.forEach { prop ->
            prop.emitProperty(classSpec)
        }

        fileSpec.addType(classSpec.build())
        fileSpec.build().writeTo(
            codeGenerator,
            aggregating = false,
            originatingKSFiles = (dependencies + this).mapNotNull {
                it.classDeclaration.containingFile
            }
        )
    }
}

