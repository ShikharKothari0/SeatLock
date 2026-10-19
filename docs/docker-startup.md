# Windows Docker Desktop startup recovery

On 18 October 2026, Docker Desktop 4.80.0 on this Windows machine repeatedly failed when opening `Docker/run/dockerInference` and `docker-secrets-engine/engine.sock` under LocalAppData. Logs reported `The file cannot be accessed by the system`. These are stranded Windows AF_UNIX socket files, not SeatLock database corruption.

Docker's open reports describe the same failure, including reports after newer releases: [desktop-feedback#460](https://github.com/docker/desktop-feedback/issues/460) and [for-win#15064](https://github.com/docker/for-win/issues/15064). Disabling Model Runner is not a dependable fix for socket initialization. This is a persistent local workaround, not a patch to Docker's underlying Windows socket handling.

## Installed launcher

The user's Desktop and Start Menu **Docker Desktop** shortcuts now invoke `%LOCALAPPDATA%/SeatLockTools/Start-DockerDesktop.ps1`. Its source is versioned at `scripts/Start-DockerDesktop.ps1`; the installed copy remains usable if the repository moves. Original shortcuts are backed up in the same tools folder.

The launcher serializes concurrent launches, checks the local `desktop-linux` engine, and changes runtime directories only when neither Docker Desktop nor its backend is running. It checks absolute paths and refuses redirected directories or unexpected content. Only directories containing zero-byte reparse-point socket files are renamed with a unique `.stale-*` suffix. Their contents are preserved. Containers, images, volumes, settings and WSL distributions are untouched.

It waits up to three minutes for engine readiness and logs results to `%LOCALAPPDATA%/SeatLockTools/docker-startup.log`. If an existing Docker process is unhealthy, it waits rather than force-killing it. Quit Docker Desktop normally, then launch again using the shortcut. Prefer **Quit Docker Desktop** or `docker desktop stop` over terminating backend processes.

Directly launching the original executable, `docker desktop start`, Docker's internal restart action or an old pinned taskbar shortcut bypasses this wrapper. Re-pin the repaired Desktop shortcut if using a taskbar shortcut. Automatic login startup was already disabled and remains disabled. Future Docker installers may replace shortcuts; reapply the wrapper if that happens. Preserved stale directories are not automatically deleted.

To undo the workaround, restore the original `.lnk` files from the tools folder to the Desktop/Start Menu. No Docker configuration rollback is needed.

## Verification on this machine

The installed launcher started Docker successfully, then passed a graceful stop/start cycle that left both runtime socket folders stranded. It rotated both folders and the engine became ready. Invoking it again while Docker was healthy made no socket changes. The Desktop and Start Menu links point to the installed copy.

The existing SeatLock live regression had completed successfully before these restart checks. When bringing the project up afterward, Compose found no existing SeatLock containers and recreated the six services. The socket workaround performs no container or volume deletion. The Compose PostgreSQL configuration currently uses anonymous storage; durable named database storage and migration of any existing data remain a separate deployment task.

After recovery, all six Compose services were running, the backend health endpoint reported UP, Flyway V1 through V7 succeeded, and all four live catalog scenarios passed again. This verifies a Docker stop/start cycle, not a full Windows reboot.
