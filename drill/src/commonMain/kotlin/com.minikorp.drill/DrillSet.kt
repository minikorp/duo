package com.minikorp.drill


@Suppress("UNCHECKED_CAST")
class DrillSet<SetType : Set<Immutable>, Immutable, Mutable>(
    ref: Set<Immutable>,
    parent: Drillable<*>?,
    private val factory: (Sequence<Immutable>) -> SetType,
    private val mutate: (container: Drillable<*>, Immutable) -> Mutable,
    private val freeze: (Mutable) -> Immutable
) : MutableSet<Mutable>, DefaultDrillable<Set<Immutable>>(ref, parent) {

    private inner class Entry(ref: Immutable) : DefaultDrillable<Immutable>(ref, this) {
        var backing: Any? = UNSET_VALUE
        var value: Mutable
            get() {
                if (backing === UNSET_VALUE) {
                    backing = ref().run { mutate(this@Entry, this) }
                }
                return backing as Mutable
            }
            set(value) {
                backing = value
                markDirty()
            }

        override fun freeze(): Immutable {
            if (dirty()) return freeze(value)
            return ref()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            val casted = other as? DrillSet<*, *, *>.Entry
            if (ref() != casted?.ref()) return false
            if (backing != casted?.backing) return false
            return true
        }

        override fun hashCode(): Int {
            var result = ref()?.hashCode() ?: 0
            result = 31 * result + (backing?.hashCode() ?: 0)
            return result
        }
    }

    private val items: MutableSet<Entry> by lazy(LazyThreadSafetyMode.NONE) {
        ref.mapTo(LinkedHashSet()) { Entry(it) }
    }

    override fun freeze(): Set<Immutable> {
        return if (dirty()) items.mapTo(LinkedHashSet()) { it.freeze() } else ref()
    }

    override val size: Int get() = items.size

    override fun isEmpty(): Boolean = items.isEmpty()

    override fun iterator(): MutableIterator<Mutable> = object : MutableIterator<Mutable> {
        val iterator = items.iterator()
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): Mutable {
            return iterator.next().value
        }

        override fun remove() {
            iterator.remove()
            markDirty()
        }
    }

    override fun clear() = items.clear().also { markDirty() }

    override fun remove(element: Mutable): Boolean {
        return items.removeAll { it.value == element }.also { markDirty() }
    }

    override fun removeAll(elements: Collection<Mutable>): Boolean {
        val removed = elements.map { remove(it) }
        return removed.any()
    }

    override fun retainAll(elements: Collection<Mutable>): Boolean {
        return items.retainAll { elements.contains(it.value) }.also { markDirty() }
    }

    override fun add(element: Mutable): Boolean {
        return items.add(Entry(freeze(element)).also { markDirty() })
    }

    override fun addAll(elements: Collection<Mutable>): Boolean {
        return elements.map { add(it) }.any()
    }

    override fun contains(element: Mutable): Boolean {
        return items.find { it.backing == element } != null
    }

    override fun containsAll(elements: Collection<Mutable>): Boolean {
        return elements.all { contains(it) }
    }

    fun retainAllElements(elements: Collection<Immutable>) {
        items.retainAll { elements.contains(it.ref()) }.also { markDirty() }
    }

    fun removeElement(element: Immutable) {
        items.removeAll { it.ref() == element }.also { markDirty() }
    }

    /** Similar to [add] can't share name due to type erasure generating same signature */
    fun addElement(element: Immutable): Boolean {
        return items.add(Entry(element)).also { markDirty() }
    }

    override fun toString(): String {
        return items.toString()
    }
}

fun <SetType : Set<Immutable>, Immutable, Mutable> SetType.toMutable(
    parent: Drillable<*>? = null,
    factory: (Sequence<Immutable>) -> SetType,
    mutate: (container: Drillable<*>, Immutable) -> Mutable,
    freeze: (Mutable) -> Immutable
): DrillSet<SetType, Immutable, Mutable> {
    return DrillSet(this, parent, factory, mutate, freeze)
}
