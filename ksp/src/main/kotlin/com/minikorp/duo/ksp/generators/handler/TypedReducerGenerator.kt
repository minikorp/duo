package com.minikorp.duo.ksp.generators.handler

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.minikorp.duo.ActionContext
import com.minikorp.duo.TypedReducer
import com.minikorp.duo.ksp.CompilationException
import com.minikorp.duo.ksp.Generator
import com.minikorp.duo.ksp.asClassName
import com.minikorp.duo.ksp.codeGenerator
import com.minikorp.duo.ksp.createNewFile
import com.minikorp.duo.ksp.isAnnotatedWith
import com.minikorp.duo.ksp.isSuspending
import com.minikorp.duo.ksp.resolver
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toTypeName

private val reducerClasses = HashMap<String, TypedHandlerGenerator>()

fun resolveGenerators(): List<Generator> {
    val reducerSymbols =
        resolver.getSymbolsWithAnnotation(TypedReducer.Root::class.qualifiedName.toString())
            .toList()
    if (reducerSymbols.isEmpty()) return emptyList()

    reducerSymbols.forEach { symbol ->
        symbol.accept(
            object : KSDefaultVisitor<Unit, Unit>() {
                override fun defaultHandler(node: KSNode, data: Unit) = Unit
                override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
                    val container = function.parentDeclaration as? KSClassDeclaration
                        ?: throw CompilationException(
                            function,
                            "@${TypedReducer::class.simpleName} functions must be declared inside a class",
                        )
                    val id = TypedHandlerGenerator.id(container)
                    if (reducerClasses[id] == null) {
                        reducerClasses[id] = TypedHandlerGenerator(container)
                    }
                }
            },
            Unit,
        )
    }

    return reducerClasses.values.toList()
}

@OptIn(KotlinPoetKspPreview::class)
class TypedHandlerGenerator(private val classDeclaration: KSClassDeclaration) : Generator {

    companion object {
        fun id(declaration: KSClassDeclaration): String = declaration.qualifiedName!!.asString()

        fun resolveGenerators(): List<Generator> {
            val reducerSymbols =
                resolver.getSymbolsWithAnnotation(TypedReducer.Root::class.qualifiedName.toString()).toList()

            if (reducerSymbols.isEmpty()) return emptyList()

            reducerSymbols.forEach { symbol ->
                symbol.accept(
                    object : KSDefaultVisitor<Unit, Unit>() {
                        override fun defaultHandler(node: KSNode, data: Unit) = Unit
                        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
                            val container = function.parentDeclaration as? KSClassDeclaration
                                ?: throw CompilationException(
                                    function,
                                    "@${TypedReducer::class.simpleName} functions must be declared inside a class",
                                )
                            val id = id(container)
                            if (reducerClasses[id] == null) {
                                reducerClasses[id] = TypedHandlerGenerator(container)
                            }
                        }
                    },
                    Unit,
                )
            }

            return reducerClasses.values.toList()
        }
    }

    private val handlerClassName = classDeclaration.asClassName()
    private val fileSpec = FileSpec.builder(
        handlerClassName.packageName,
        handlerClassName.simpleName + "TypedReducer",
    )

    private val rootFunction = classDeclaration
        .getAllFunctions()
        .filter { it.isAnnotatedWith(TypedReducer.Root::class) }
        .toList()
        .let { roots ->
            if (roots.size != 1) {
                throw CompilationException(
                    classDeclaration,
                    "Expected exactly one function annotated with @${TypedReducer.Root::class.simpleName}, " +
                            "found ${roots.size}",
                )
            }
            roots[0]
        }

    private val parameter = run {
        rootFunction.parameters.firstOrNull()?.type?.toTypeName() ?: throw CompilationException(
            rootFunction,
            "The root function must have a parameter of type ${ActionContext::class.simpleName}<T> ",
        )
    }

    private val functions = classDeclaration.getAllFunctions()
        .filter { it.isAnnotatedWith(TypedReducer.Fun::class) }
        .map { ReducerAnnotatedFunction(it) }

    private val extensionFunctionName = "${rootFunction.simpleName.asString()}Typed"

    override fun initialize() {
    }

    override fun emit() {
        val whenCase = CodeBlock.builder()
            .beginControlFlow("val out = when (val action = ctx.action)")
            .apply {
                functions.forEach { function ->
                    add("is %T -> ", function.actionParam.type)
                    add(function.callCodeBlock)
                }
            }
            .add("else -> null\n")
            .endControlFlow()
            .addStatement("return out")
            .build()

        val returnType = functions.firstOrNull()?.returnType?.copy(nullable = true)

        fileSpec.addFunction(
            FunSpec.builder(extensionFunctionName)
                .receiver(handlerClassName)
                .addCode(whenCase)
                .addModifiers(if (rootFunction.isSuspending) listOf(KModifier.SUSPEND) else emptyList())
                .addParameter("ctx", parameter)
                .apply {
                    if (returnType != null) {
                        returns(returnType)
                    }
                }
                .build(),
        )

        codeGenerator.createNewFile(
            fileSpec.build(),
            Dependencies(aggregating = false, classDeclaration.containingFile!!),
        )
    }

    data class ReducerAnnotatedFunction(val functionDeclaration: KSFunctionDeclaration) {

        val name: String = functionDeclaration.simpleName.asString()

        val returnType: TypeName =
            (functionDeclaration.returnType?.resolve()?.declaration as? KSClassDeclaration)
                ?.asClassName()
                ?: throw CompilationException(functionDeclaration, "Couldn't resolve return type")

        val params = functionDeclaration.parameters.map { Param(it) }

        val actionParam: Param = params.find { it.name == "action" }
            ?: throw CompilationException(functionDeclaration, "Missing action parameter")

        val suspending = functionDeclaration.modifiers.contains(Modifier.SUSPEND)

        val callCodeBlock = CodeBlock.builder()
            .addStatement(
                "$name(${params.joinToString(separator = ", ") { "${it.name} = ${it.name}" }})",
                *params.map { it.type }.toTypedArray(),
            )
            .build()
    }

    data class Param(val declaration: KSValueParameter) {
        val name = declaration.name?.getShortName() ?: "param${hashCode()}"
        val type = declaration.type.toTypeName()
    }
}
