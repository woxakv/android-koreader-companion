package io.github.woxakv.koreadercompanion.core.error

/**
 * Base type for domain-specific error hierarchies. Deliberately not `sealed`:
 * Kotlin only allows a sealed type's subtypes to live in the same module, but
 * concrete error hierarchies (e.g. domain's KoreaderError) are defined in
 * downstream modules.
 */
interface AppError {
    val message: String
}
