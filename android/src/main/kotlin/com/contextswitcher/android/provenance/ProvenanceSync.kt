package com.contextswitcher.android.provenance

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

/// The phone's copy of the provenance repository (`dsn~provenance-repository~1`).
/// Unlike the task clone it is not a mirror: records are written into it and
/// must survive a sync without network. One round clones when there is no copy,
/// commits what was recorded, merges the remote (records are new files, so the
/// merge is clean) and pushes; a failure leaves the commit for the next round.
/// Null on success, else the error.
// [impl->dsn~provenance-repository~1]
class ProvenanceSync {

    @Synchronized
    fun sync(dir: File, url: String, username: String, token: String): String? {
        val credentials = UsernamePasswordCredentialsProvider(username, token)
        return try {
            if (!File(dir, ".git").isDirectory) {
                dir.mkdirs()
                val refs = Git.lsRemoteRepository().setRemote(url).setCredentialsProvider(credentials).setHeads(true).call()
                if (refs.isEmpty()) {
                    // JGit cannot clone an empty repository (system git can): start
                    // the copy on `main`, and the first push creates the branch.
                    Git.init().setDirectory(dir).setInitialBranch("main").call().use { git ->
                        git.remoteAdd().setName("origin").setUri(URIish(url)).call()
                    }
                } else {
                    Git.cloneRepository().setURI(url).setDirectory(dir).setCredentialsProvider(credentials).call().close()
                }
            }
            Git.open(dir).use { git ->
                if (!git.status().call().isClean) {
                    git.add().addFilepattern(".").call()
                    val author = PersonIdent("ContextSwitcher Android", "android@contextswitcher.invalid")
                    git.commit().setMessage("ContextSwitcher provenance").setAuthor(author).setCommitter(author).call()
                }
                val branch = git.repository.branch
                git.fetch().setCredentialsProvider(credentials).call()
                val remote = git.repository.resolve("refs/remotes/origin/$branch")
                if (remote != null) {
                    val merged = git.merge().include(remote).setMessage("Merge provenance from origin").call()
                    if (!merged.mergeStatus.isSuccessful) {
                        return "Cannot merge the provenance repository: ${merged.mergeStatus}"
                    }
                }
                if (git.repository.resolve("HEAD") == null) {
                    return null
                }
                val update = git.push().setCredentialsProvider(credentials)
                    .setRefSpecs(RefSpec("HEAD:refs/heads/$branch")).call()
                    .flatMap { it.remoteUpdates }.single()
                if (update.status == RemoteRefUpdate.Status.OK || update.status == RemoteRefUpdate.Status.UP_TO_DATE) null
                else "Push of the provenance repository rejected: ${update.message ?: update.status}"
            }
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }
}
