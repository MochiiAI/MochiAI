# PRoot assets

These shell scripts bootstrap the on-device Alpine Linux terminal used by Mochi
to run `clang`, `python3` and `git` without root.

| File                   | Purpose                                                        |
| ---------------------- | -------------------------------------------------------------- |
| `proot-init.sh`        | Configures git credentials from the stored PAT and prepares $HOME. |
| `bootstrap-alpine.sh`  | Downloads + verifies a minirootfs tarball (network required).  |
| `README.md`            | This file.                                                     |

## How it works

Android apps cannot `chroot`. Mochi embeds the [PRoot](https://proot-me.github.io/)
binary and runs `/bin/sh` with `-R rootfs -b /dev -b /proc` inside app-private
storage. The terminal Service streams `PTY` output back through Kotlin Flows.

## Shipping a rootfs

For a self-contained offline terminal, download a minirootfs and bundle it:

```bash
curl -fsSL https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/aarch64/sha256sums
# ... then place the .tar.gz here as rootfs-alpine-aarch64.tar.gz
```

The `.gitignore` deliberately excludes `*.tar.gz` and `rootfs*` from version
control — those builds are large binaries. Uncomment the ignore in the app
module's build file or add a Gradle task to stamp them at build time.

## Git credential flow

1. User saves a PAT (or the default `SecretConfig.DEFAULT_GITHUB_TOKEN` is used).
2. `TokenStore.effectiveGitHubToken` yields the active token.
3. `AndroidPrRootManager.execute("sh proot-init.sh")` runs the script, which:
   - writes `~/.netrc` (machine github.com / login MochiiAI / password \<token\>)
   - writes `~/.git-credentials` for Git's `credential.helper store`
   - sets `user.name=MochiiAI`, `user.email=MochiAII@protonmail.com`

`git clone`, `git commit` and `git push` then work unauthenticated thanks to
the embedded credentials.