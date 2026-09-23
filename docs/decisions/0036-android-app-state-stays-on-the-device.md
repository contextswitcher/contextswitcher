---
status: accepted
date: 2026-09-17
decision-makers: Oliver Kopp
---

# Android App State Stays on the Device

## Context and Problem Statement

The Android app keeps state of its own besides the synced task repo: the repo URL and tokens, the SSH private key, known hosts, and unsent message drafts (`dsn~android-message-drafts~3`).
All of it lives in app-private storage (`SharedPreferences`, `filesDir`), which Android deletes when the app is uninstalled.
Reinstalling the app — needed once when builds were signed with different debug keys — lost the credentials, and the drafts go the same way.
Should this state survive an uninstall, or move between the user's devices?

## Considered Options

* Keep it in app-private storage; an update installs over the app and keeps it
* Sync drafts through the task repo, like the desktop's queue
* Rely on Android's Auto Backup (`allowBackup`) to restore it after a reinstall

## Decision Outcome

Chosen option: "Keep it in app-private storage", because since every build is signed with the committed debug key (`android/debug.keystore`), updates install over the app and keep its state, so an uninstall is no longer part of the normal flow.
Drafts are a phone's scratch text for a message not sent yet; the queue that matters across machines is the desktop's, which the task repo already carries.
Oliver, 2026-09-17, asked whether drafts would sync through the repo: "no, thanks".

### Consequences

* Good, because nothing new is written to the task repo, and no merge between two devices' drafts is needed.
* Good, because tokens and the SSH key never leave the device.
* Bad, because an uninstall, a new device, or clearing the app's data loses the drafts and requires entering the credentials again.
* Bad, because phone and tablet each have their own drafts.
* Neutral, because `allowBackup` stays `true`, but nothing depends on a restore: whether Android backs up a sideloaded app at all is device-dependent, and it did not restore the credentials after Oliver's reinstall.

## Pros and Cons of the Options

### Sync drafts through the task repo

* Good, because drafts would survive an uninstall and show on every device.
* Bad, because every keystroke-level change would have to be committed and pushed, or batched with the risk of losing the batch.
* Bad, because two devices editing the same draft need a merge rule.

### Rely on Android's Auto Backup

* Good, because no code: the manifest already allows it.
* Bad, because it is not under the app's control (a device setting, backup timing, sideloaded apps), and it would copy the tokens and the private key into the user's cloud backup.
