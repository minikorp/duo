package com.minikorp.duo.ksp.generators.drill

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.minikorp.drill.DefaultDrillType
import com.minikorp.drill.DrillProperty
import com.minikorp.drill.DrillType
import com.minikorp.duo.State
import com.minikorp.duo.ksp.*
import com.minikorp.duo.ksp.generators.drill.DrillClassModel.Companion.DIRTY_PROPERTY
import com.minikorp.duo.ksp.generators.drill.DrillClassModel.Companion.SOURCE_PROPERTY
import com.minikorp.duo.ksp.generators.drill.adapters.AbstractPropertyAdapter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toTypeName

val drillClasses = HashMap<String, DrillClassModel>()

class DrillClassGenerator(val drillClass: DrillClassModel) : Generator {

    companion object {
        fun resolveGenerators(): List<Generator> {
            val drillSymbols =
                resolver.getSymbolsWithAnnotation(State::class.qualifiedName.toString()).toList()
            if (drillSymbols.isEmpty()) return emptyList()

            drillSymbols.forEach { symbol ->
                symbol.accept(
                    object : KSDefaultVisitor<Unit, Unit>() {
                        override fun defaultHandler(node: KSNode, data: Unit) = Unit
                        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
                            val id = DrillClassModel.id(classDeclaration)
                            if (drillClasses[id] == null) {
                                drillClasses[id] = DrillClassModel(classDeclaration)
                            }
                        }
                    },
                    Unit,
                )
            }

            return drillClasses.values.map { DrillClassGenerator(it) }
        }

        fun findMutableClass(type: TypeName): DrillClassModel? {
            return drillClasses.values.find { it.originalClassName == type }
        }
    }

    private val fileSpec = FileSpec.builder(
        drillClass.mutableClassName.packageName,
        drillClass.mutableClassName.simpleName,
    )

    private val classSpec = TypeSpec.classBuilder(drillClass.mutableClassName)

    override fun initialize() {
    }

    private fun generateMutableClass(classSpec: TypeSpec.Builder) {
        val adapters = drillClass.properties.map { prop ->
            AbstractPropertyAdapter.createAdapter(prop)
        }

        classSpec
            .addKdoc("[%T]", drillClass.originalClassName)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(DrillType<*>::ref.name, drillClass.originalClassName)
                    .addParameter(
                        DrillType<*>::parent.name,
                        DrillClassModel.nullableParentType
                    )
                    .build()
            )
            .superclass(DrillClassModel.baseType.parameterizedBy(drillClass.originalClassName))
            .addSuperclassConstructorParameter("ref")
            .addSuperclassConstructorParameter("parent")

        val callCodeBlock = CodeBlock.builder()
        adapters.forEach {
            callCodeBlock.add(it.freezeExpression)
        }
        val callArgs = adapters.joinToString(separator = ",\n") {
            "${it.sourceProp.name} = ${it.freezeExpression}"
        }

        val freezeFun = FunSpec.builder("freeze")
            .returns(drillClass.originalClassName)
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(suppressAnnotation(UNCHECKED_ANNOTATION))
            .beginControlFlow("if ($DIRTY_PROPERTY)")
            .addStatement("return %T($callArgs)", drillClass.originalClassName)
            .endControlFlow()
            .addStatement("return $SOURCE_PROPERTY")
            .build()

        val toStringFun = FunSpec.builder("toString")
            .addModifiers(KModifier.OVERRIDE)
            .returns(String::class)
            .addStatement("val sb = %T()", StringBuilder::class)
            .apply {
                adapters
                    .filter { !it.sourceProp.ignore }//Don't generate for ignored fields
                    .forEach { adapter ->
                        beginControlFlow("if (${adapter.isDirtyExpression})")
                        addStatement("sb.append(\"\\n\", \"${adapter.sourceProp.name}:·${adapter.stringExpression}\")")
                        endControlFlow()
                    }
            }
            .addStatement("""return "{" + sb.toString().prependIndent("  ") + "\n}"""")
            .build()

        classSpec.addFunction(toStringFun)
        classSpec.addFunction(freezeFun)
        adapters.map { it.generate(classSpec) }
    }

    private fun generateExtensions(fileSpec: FileSpec.Builder) {
        val toMutableExtension = FunSpec.builder("toMutable")
            .returns(drillClass.mutableClassName)
            .receiver(drillClass.originalClassName)
            .addParameter(
                ParameterSpec.builder(
                    "parent",
                    DrillClassModel.nullableParentType
                )
                    .defaultValue("null")
                    .build()
            )
            .addStatement("return %T(this, parent)", drillClass.mutableClassName)
            .build()

        val produceExtension = FunSpec.builder("produce")
            .addModifiers(KModifier.INLINE)
            .addParameter(
                ParameterSpec
                    .builder(
                        "block", LambdaTypeName.get(
                            receiver = drillClass.mutableClassName,
                            returnType = Unit::class.asClassName()
                        )
                    )
                    .addModifiers(KModifier.CROSSINLINE)
                    .build()
            )
            .returns(drillClass.mutableClassName)
            .receiver(drillClass.originalClassName)
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
                            receiver = drillClass.mutableClassName,
                            returnType = Unit::class.asClassName()
                        )
                    )
                    .addModifiers(KModifier.CROSSINLINE)
                    .build()
            )
            .returns(drillClass.originalClassName)
            .receiver(drillClass.originalClassName)
            .addStatement("return this.${produceExtension.name}(block).freeze()")
            .build()

        fileSpec.addFunction(produceExtension)
        fileSpec.addFunction(mutateExtension)
        fileSpec.addFunction(toMutableExtension)
        fileSpec.addImport(DrillType::class.asClassName().packageName, "toMutable")
    }

    override fun emit() {
        generateMutableClass(classSpec)
        generateExtensions(fileSpec)
        fileSpec.addType(classSpec.build())
        codeGenerator.createNewFile(
            fileSpec.build(),
            Dependencies(
                aggregating = false,
                drillClass.classDeclaration.containingFile!!
            ),
        )
    }
}

class DrillClassModel(
    val classDeclaration: KSClassDeclaration
) {
    companion object {
        fun id(classDeclaration: KSClassDeclaration): String {
            return classDeclaration.qualifiedName!!.asString()
        }

        val SOURCE_PROPERTY = "${DrillType<*>::ref.name}()" //"ref"
        val DIRTY_PROPERTY = "${DrillType<*>::dirty.name}()" //"diry"

        val baseType = DefaultDrillType::class.asClassName()
        val parentType = DrillType::class.asClassName().parameterizedBy(STAR)
        val nullableParentType = parentType.copy(nullable = true)
    }

    val originalClassName: ClassName = classDeclaration.asClassName()
    val mutableClassName = ClassName(
        originalClassName.packageName,
        originalClassName.simpleName + "Mutable"
    )

    val properties: List<DrillPropertyModel> =
        classDeclaration.getAllProperties()
            .map { DrillPropertyModel(this, it) }
            .toList()
}

data class DrillPropertyModel(
    val container: DrillClassModel,
    val sourceProp: KSPropertyDeclaration
) {
    @OptIn(KspExperimental::class)
    private val annotation: DrillProperty? = sourceProp.getAnnotationsByType(DrillProperty::class).firstOrNull()

    val ignore: Boolean get() = annotation?.ignore ?: false
    val asReference: Boolean get() = annotation?.asReference ?: false

    val type: KSType = sourceProp.type.resolve()

    @OptIn(KotlinPoetKspPreview::class)
    val typeName: TypeName = sourceProp.type.toTypeName()
    val name: String = sourceProp.simpleName.getShortName()
}