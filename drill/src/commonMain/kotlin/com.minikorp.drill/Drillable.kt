package com.minikorp.drill

/**
 * Special token to mark property as unset
 */
@Suppress("ClassName")
object UNSET_VALUE {
    override fun toString(): String = "unset"
}

/**
 * Base interface for mutable types, with method properties to avoid name collisions with user classes.
 */
interface Drillable<T> {

    fun ref(): T

    /** Parent mutable type, dirty state will propagate upwards when mutating and object. */
    fun parent(): Drillable<*>?

    /** Indicates if this object has been mutated and needs to be recreated when frozen. */
    fun dirty(): Boolean

    /** Mark the object as dirty and propagate to parent. */
    fun markDirty()

    /**
     * Update the underlying value with a new reference that will be kept when freezing.
     * unless modified later
     */
    fun set(value: T)

    /** Rebuild immutable the object. */
    fun freeze(): T
}

/**
 * Basic implementation with actual backing properties.
 */
abstract class DefaultDrillable<T>(
    private var ref: T,
    private val parent: Drillable<*>?
) : Drillable<T> {

    private var dirty: Boolean = false

    override fun dirty() = dirty
    override fun ref() = ref
    override fun parent() = parent

    override fun set(value: T) {
        dirty = false
        ref = value
    }

    override fun markDirty() {
        if (!dirty()) {
            dirty = true
            parent?.markDirty()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val casted = other as? DefaultDrillable<*>
        if (ref != casted?.ref) return false
        return true
    }

    override fun hashCode(): Int {
        return ref?.hashCode() ?: 0
    }
}