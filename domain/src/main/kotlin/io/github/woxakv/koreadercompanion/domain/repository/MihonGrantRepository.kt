package io.github.woxakv.koreadercompanion.domain.repository

/**
 * Narrow, Mihon-specific read on the shape of the granted Mihon tree, used
 * only to surface a re-grant hint in Config (see StorageTarget.MIHON's doc
 * comment and .claude/plans/.../03-widen-mihon-grant.md). Kept separate
 * from [StorageAccessRepository] since that interface is deliberately
 * target-agnostic (plain grant/revoke/has-access by [StorageTarget]); this
 * one bakes in knowledge of Mihon's own folder layout, which doesn't belong
 * there.
 */
interface MihonGrantRepository {
    /**
     * True when a Mihon grant exists but is an old, narrow grant made
     * before this app widened the Mihon grant from `mihon/autobackup/` to
     * `mihon/` - i.e. it's rooted directly at `autobackup/` and won't reach
     * the sibling `downloads/` folder needed for CBZ cover art. False when
     * nothing is granted, or the grant is already the wider `mihon/` root.
     */
    suspend fun isGrantNarrow(): Boolean
}
