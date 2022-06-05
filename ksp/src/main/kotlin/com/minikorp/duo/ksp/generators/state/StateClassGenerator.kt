package com.minikorp.duo.ksp.generators.state

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.minikorp.duo.ActionContext
import com.minikorp.duo.Reducer
import com.minikorp.duo.IdentityReducer
import com.minikorp.duo.MemoizedSelector
import com.minikorp.duo.State
import com.minikorp.duo.ksp.*
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName

private val stateClasses = HashMap<String, StateAnnotatedClass>()

data class StateClassGenerator(val stateAnnotatedClass: StateAnnotatedClass) : Generator {

    companion object {
        private const val PARAMETER_COUNT = 8

        fun resolveGenerators(): List<Generator> {
            val stateSymbols =
                resolver.getSymbolsWithAnnotation(State::class.qualifiedName.toString()).toList()
            if (stateSymbols.isEmpty()) return emptyList()

            stateSymbols.forEach { symbol ->
                symbol.accept(
                    object : KSDefaultVisitor<Unit, Unit>() {
                        override fun defaultHandler(node: KSNode, data: Unit) = Unit
                        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
                            val id = StateAnnotatedClass.id(classDeclaration)
                            if (stateClasses[id] == null) {
                                stateClasses[id] = StateAnnotatedClass(classDeclaration)
                            }
                        }
                    },
                    Unit,
                )
            }
            stateClasses.values.forEach { it.link() }

            val rootStates = stateClasses.values.toMutableList()
            stateClasses.values.forEach { stateAnnotatedClass ->
                stateAnnotatedClass.nodes.forEach { rootStates.remove(it.stateAnnotatedClass) }
            }
            return rootStates.map { StateClassGenerator(it) }
        }
    }

    data class Slice(
        val path: List<String>,
        val stateAnnotatedClass: StateAnnotatedClass
    ) {
        val parameterName = path.joinToString("_").ifBlank { "root" }
        val isRoot = path.isEmpty()
        val getterChain = getChain(path)
        val setterChain = copyChain(pastPath = emptyList(), path = path)

        private fun getChain(path: List<String>): String {
            return "ctx.state.${path.joinToString(".")}"
        }

        private fun copyChain(pastPath: List<String>, path: List<String>): String {
            if (path.isEmpty()) return "newState"
            val head = path.first()
            val tail = path.drop(1)
            val pastGet = if (pastPath.isEmpty()) "" else pastPath.joinToString(".") + "."
            return "ctx.state.${pastGet}copy($head = ${copyChain(pastPath + head, tail)})"
        }
    }

    private val slices = ArrayList<Slice>()

    private val fileSpec =
        FileSpec.builder(
            stateAnnotatedClass.syntheticHandlerClassName.packageName,
            stateAnnotatedClass.syntheticHandlerClassName.simpleName,
        )

    override fun buildModel() {
        flattenIntoSlices(emptyList(), stateAnnotatedClass)
    }

    private fun flattenIntoSlices(path: List<String>, clazz: StateAnnotatedClass) {
        slices.add(Slice(path, clazz))
        clazz.nodes.forEach { node ->
            flattenIntoSlices(path + node.name, node.stateAnnotatedClass)
        }
    }

    override fun emit() {
        emitReducer()
        emitTypedSelectors()

        codeGenerator.createNewFile(
            fileSpec
                .addAnnotation(
                    AnnotationSpec.builder(Suppress::class).addMember("%S", "UNCHECKED_CAST")
                        .build(),
                )
                .build(),
            Dependencies(
                aggregating = true,
                *slices.map { it.stateAnnotatedClass.classDeclaration.containingFile!! }.toTypedArray(),
            ),
        )
    }

    private fun emitReducer() {
        val body = CodeBlock.builder()

        val creator = FunSpec.builder(
            "create${stateAnnotatedClass.stateClassName.simpleName}Reducer",
        ).returns(stateAnnotatedClass.reducerSupertypeClassName)
            .apply {
                slices.forEach { slice ->
                    addParameter(
                        ParameterSpec.builder(
                            slice.parameterName,
                            slice.stateAnnotatedClass.reducerSupertypeClassName,
                        ).defaultValue("%T()", IdentityReducer::class).build(),
                    )

                    if (slice.isRoot) {
                        body.addStatement("${slice.parameterName}.reduce(ctx)")
                    } else {
                        val getter = "{ ${slice.getterChain} }"
                        val setter =
                            "{ newState -> ctx.mutableState = ${slice.setterChain} }"
                        body.addStatement("${slice.parameterName}.reduce(ctx.slice(\n$getter,\n$setter))")
                    }
                }
            }

        creator.addCode(
            CodeBlock.builder()
                .add("return object : %T {\n", stateAnnotatedClass.reducerSupertypeClassName)
                .indent()
                .add(
                    "override suspend fun reduce(ctx: %T) {\n",
                    ActionContext::class.asClassName()
                        .parameterizedBy(stateAnnotatedClass.stateClassName),
                )
                .indent()
                .add(body.build())
                .unindent()
                .add("}")
                .unindent()
                .add("\n}")
                .build(),
        )

        fileSpec.addFunction(creator.build())
    }

    private fun emitTypedSelectors() {
        repeat(PARAMETER_COUNT) { parameterCount ->

            val returnType = TypeVariableName("R")
            val parameters = (1..parameterCount + 1).map { index ->
                val typeVariable = TypeVariableName("P$index")
                val functionType = LambdaTypeName.get(
                    parameters = arrayOf(stateAnnotatedClass.stateClassName),
                    returnType = typeVariable,
                )
                ParameterSpec.builder("p$index", functionType)
                    .tag(TypeVariableName::class, typeVariable)
                    .build()
            }

            val paramsAsList = parameters.joinToString(", ") { it.name }
            val paramsCasted =
                parameters.mapIndexed { index, spec -> "args[$index] as ${spec.tag(TypeVariableName::class)!!.name}" }
                    .joinToString(", ")
            val body = CodeBlock.builder()
                .add(
                    "return %T(arrayOf($paramsAsList)) { args -> selector($paramsCasted) }",
                    MemoizedSelector::class,
                )

            val selectorLambda = LambdaTypeName.get(
                parameters = parameters.map { it.tag(TypeVariableName::class)!! }.toTypedArray(),
                returnType = returnType,
            )


            fileSpec.addFunction(
                FunSpec.builder("create${stateAnnotatedClass.stateClassName.simpleName}Selector")
                    .returns(
                        MemoizedSelector::class.asClassName().parameterizedBy(
                            stateAnnotatedClass.stateClassName, returnType,
                        ),
                    )
                    .addTypeVariables(parameters.map { it.tag(TypeVariableName::class)!! })
                    .addParameters(parameters)
                    .addTypeVariable(returnType)
                    .addParameter(ParameterSpec("selector", selectorLambda))
                    .addCode(body.build())
                    .build(),
            )
        }
    }
}

data class StateAnnotatedClass(
    val classDeclaration: KSClassDeclaration
) {
    companion object {
        fun id(classDeclaration: KSClassDeclaration): String {
            return classDeclaration.qualifiedName!!.asString()
        }
    }

    data class Node(
        val name: String,
        val stateAnnotatedClass: StateAnnotatedClass
    )

    val nodes = ArrayList<Node>()
    val stateClassName = classDeclaration.asClassName()
    val reducerSupertypeClassName =
        Reducer::class.asClassName().parameterizedBy(stateClassName)
    val syntheticHandlerClassName =
        ClassName(stateClassName.packageName, stateClassName.simpleName + "SyntheticHandler")

    fun link() {
        classDeclaration.getAllProperties().forEach { property ->
            property.type.resolve().declaration.accept(
                object : KSDefaultVisitor<Unit, Unit>() {
                    override fun defaultHandler(node: KSNode, data: Unit) = Unit
                    override fun visitClassDeclaration(
                        classDeclaration: KSClassDeclaration,
                        data: Unit
                    ) {
                        val slice = stateClasses[classDeclaration.id]
                        if (slice != null) {
                            val name = property.simpleName.asString()
                            nodes.add(
                                Node(
                                    name = name,
                                    stateAnnotatedClass = slice,
                                ),
                            )
                        }
                    }
                },
                Unit,
            )
        }
    }
}
