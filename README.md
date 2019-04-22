# ContextSwitcher

> ContextSwitcher is [Mylyn](https://www.eclipse.org/mylyn/) for the Windows desktop.

More user information in [docs/](docs/).

## Development phases

The intended development phases for this project are as follows:

1. Skeleton for core module to store context and to switch context ("core")
2. Firefox plugin for getting/setting context in Firefox and in the core
3. Microsoft Word plugin for getting/setting context in Firefox and in the core
4. UI for the core
5. Integrate cool stuff such as [ZEIº](https://timeular.com)

## Implementation

The chosen language to implement “Context Switcher” is free.

Following alternatives should be considered:

- AutoHotkey
- [AutoIt](https://www.autoitscript.com/site/) with [autoitx4java](https://github.com/sixtoad/autoitx4java)
- See also <https://alternativeto.net/software/autoit/>
- Python 3.0
- C#
- Java
  - Reuse code of [Eclipse Jubula](https://www.eclipse.org/jubula/)

It might be required to write plugins on the side of the supported application.
