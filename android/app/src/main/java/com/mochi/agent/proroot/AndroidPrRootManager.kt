package com.mochi.agent.proroot

import android.content.Context
import com.mochi.agent.core.security.TokenStore
import java.io.File

/**
 * Manages the PRoot-based Alpine Linux environment.
 *
 * Responsibilities:
 *  * copy bundled scripts out of assets/ into app-private storage,
 *  * provision git credentials from the active GitHub token,
 *  * resolve the native PRoot binary (``libproot.so`` is embedded under
 *    ``jniLibs/<abi>`` and exposed via the standard dynamic-loader trick),
 *  * expose a small exec API for the Terminal Service.
 */
class AndroidPrRootManager(
    private val context: Context,
    private val tokenStore: TokenStore,
) {

    private val appDir: File
        get() = File(context.filesDir, "prroot")

    val homeDir: File get() = File(appDir, "home")
    val rootFsDir: File get() = File(appDir, "rootfs")
    val binDir: File get() = File(appDir, "bin")

    init {
        ensureLayout()
    }

    private fun ensureLayout() {
        listOf(appDir, homeDir, rootFsDir, binDir).forEach { it.mkdirs() }
        copyAsset("preroot/proot-init.sh")
        copyAsset("preroot/bootstrap-alpine.sh")
    }

    private fun copyAsset(relative: String) {
        val target = File(appDir, File(relative).name)
        if (target.exists()) return
        context.assets.open(relative).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.setExecutable(true, false)
    }

    /** Native PRoot binary; missing in a stock build → terminal stays local. */
    val nativeProot: File?
        get() {
            // The .so is copied into the native lib dir at install time; use
            // its absolute path as an executable.
            val candidates = listOf(
                File(context.applicationInfo.nativeLibraryDir, "libproot.so"),
                File(appDir, "proot"),
            )
            return candidates.firstOrNull { it.exists() }
        }

    /** Path to the token file consumed by proot-init.sh. */
    private fun writeTokenFile(): File {
        val file = File(appDir, "mochi-github-token")
        file.writeText(tokenStore.effectiveGitHubToken)
        file.setReadable(false, false)
        return file
    }

    /**
     * Builds the `proot` command line (or a plain shell when PRoot is missing).
     */
    fun buildCommand(cwd: File = homeDir): Pair<File, List<String>> {
        val shell = File(appDir, "proot-init.sh")
        val proot = nativeProot
        val workDir = cwd.takeIf { it.exists() } ?: homeDir
        return if (proot != null && rootFsDir.exists()) {
            proot to listOf(
                "-R", rootFsDir.absolutePath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-w", workDir.absolutePath,
                "-0",
                shell.absolutePath,
                appDir.absolutePath,
            )
        } else {
            shell to listOf(appDir.absolutePath)
        }
    }

    /**
     * Refreshes git credentials inside the PRoot environment. Cheap to call
     * whenever a new token becomes active.
     */
    fun provisionGitCredentials() {
        writeTokenFile()
        executeNonBlocking("sh", "-c", "MOCHI_GITHUB_TOKEN='${tokenStore.effectiveGitHubToken}' ${appDir}/proot-init.sh ${appDir}")
    }

    private fun executeNonBlocking(vararg command: String) {
        runCatching {
            ProcessBuilder(*command)
                .directory(homeDir)
                .redirectErrorStream(true)
                .start()
        }
    }
}