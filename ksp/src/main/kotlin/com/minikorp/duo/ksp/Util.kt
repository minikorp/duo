package com.minikorp.duo.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.visitor.KSDefaultVisitor
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import java.io.OutputStream
import java.util.*
import kotlin.reflect.KClass

//Debug

private var lazyDebugFile: OutputStream? = null
fun debugPrint(obj: Any?) {
    if (lazyDebugFile == null) {
        lazyDebugFile = codeGenerator.createNewFile(Dependencies.ALL_FILES, "", "Debug")
    }
    val text = "//${obj.toString().replace("\n", "////\n")}"
    lazyDebugFile!!.write("$text\n".toByteArray())
}

fun flushDebugPrint() {
    lazyDebugFile?.flush()
    lazyDebugFile?.close()
    lazyDebugFile = null
}

fun KSName.getPackageName(): String = getQualifier().dropLast(getShortName().length)

//Kotlin Poet Utils

fun KSAnnotated.isAnnotatedWith(annotation: KClass<*>): Boolean {
    val visitor = object : KSDefaultVisitor<Unit, Unit>() {
        override fun defaultHandler(node: KSNode, data: Unit) = Unit
    }
    return annotations.find { it.shortName.asString() == annotation.simpleName } != null
}

val KSClassDeclaration.id get() = asClassName().toString()
fun KSClassDeclaration.asClassName(): ClassName {
    val qualifiedName = this.qualifiedName!!.asString()
    val packageName = this.packageName.asString()
    val simpleNames = qualifiedName.removePrefix("$packageName.").split('.')
    return ClassName(packageName, simpleNames)
}

val KSFunctionDeclaration.isSuspending get() = modifiers.contains(Modifier.SUSPEND)

fun CodeGenerator.createNewFile(fileSpec: FileSpec, dependencies: Dependencies) {
    createNewFile(dependencies, fileSpec.packageName, fileSpec.name).use { stream ->
        stream.writer().use {
            fileSpec.writeTo(it)
        }
        stream.flush()
    }
}


const val UNCHECKED_ANNOTATION = "UNCHECKED_CAST"
const val NAME_SHADOWING_ANNOTATION = "NAME_SHADOWING"
fun suppressAnnotation(vararg warnings: String): AnnotationSpec {
    return AnnotationSpec.builder(Suppress::class).apply {
        warnings.forEach {
            addMember("%S", it)
        }
    }.build()
}

fun String.safeCapitalize(): String {
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}