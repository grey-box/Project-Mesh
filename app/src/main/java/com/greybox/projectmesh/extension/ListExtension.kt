package com.greybox.projectmesh.extension

/*
 This is an extension function on Kotlin's List class, allowing to apply an update to the
 first element in a list that matches a given condition, then returning a updated list
*/

/**
 * Returns a new list with the first element that satisfies [condition] updated by [function].
 *
 * If no element matches [condition], the original list is returned unchanged.
 *
 * @param condition A predicate to identify which element to update.
 * @param function A transformation function applied to the matching element.
 * @return A new [List] with the updated element, or the original list if no element matches.
 */
inline fun <T> List<T>.updateItem(
    condition: (T) -> Boolean,
    function: (T) -> T,
): List<T> {
    // Find the index of the first element that matches the condition
    val index = indexOfFirst(condition)
    // If no such element is found, return the original list
    return if(index == -1){
        this
    }
    // Otherwise, create a new list with the element at the found index updated using the provided function
    else{
        toMutableList().also {
                newList -> newList[index] = function(this[index])
        }.toList()
    }
}
