# ContextSwitcher

> ContextSwitcher is [Mylyn](https://www.eclipse.org/mylyn/) for the Windows desktop.

Suppose you are working on a task.
You have opened IntelliJ, some browser tabs, and a Word document.
Now, an urgent email comes in.
This requires you to switch the context to another task.
When you left that task, you had different browser windows and different Word documents opened.
ContextSwitcher restores these at a finger type.

## Example tasks and opened applications

* “Search for city tours in Leipzig”: Chrome and OneNote (for taking notes)
* “Hold meeting with screen sharing”: Chrome (meeting application), Word (Documents), Excel (Documents), Explorer (SVN directory listing)
* “Collection information about a topic”: Chrome and JabRef (for storing the bibliography entries)
* “Meeting with students about their project”: Outlook (with all relevant emails), Notepad++ (for meeting minutes), Explorer (showing the project directory), Eclipse (with the current workspace and the linked Mylyn task being active)

In each opened application, only the relevant files (tabs) should be opened.
When switching a task, the already opened applications should be left opened, not required should be closed and new ones have to be opened.
The same applies to files (tabs) in the applications.
If possible, the last focused position (line in Word documents, cell in Excel, slide in PowerPoint) should be restored.

## Goal

In this project, a framework with an UI and ten plugins for task-focused applications on Windows should be implemented.
The main UI should be kept simple:
A list of tasks with the possibility to add a new task, remove a task and focus a task.

Following Applications should be supported:

* Chrome
* Firefox
* Word
* Excel
* PowerPoint
* Windows Explorer (with QTTabBar extension)
* Outlook
* Notepad++
* IntelliJ
* Eclipse (with Mylyn plugin)

## Background and related work

When working with code in Eclipse, one has opened many files by the time.
Often, not all opened files are required for a task.
What if only the relevant files are opened and even the most touched lines are highlighted?
What if one can give a colleague a reference to the current state of the IDE to enable a simpler reproduction of an issue?
For this, the concept of a “[task-focused interface](https://en.wikipedia.org/wiki/Task-focused_interface)” has been invented and turned into software as Eclipse [Mylyn](https://www.eclipse.org/mylyn/). 
Regarding supporting the task-focused interface across multiple applications, the [Tasktop Dev Standalone Application](http://www.tasktop.com/node/1176/) is available.
It was announced as [complete desktop task-focused interface for everyone](https://www.infoq.com/news/2008/02/tasktop-10), but it does not support arbitrary applications.

* [Plasma Activities](https://wiki.ubuntuusers.de/Plasma/Aktivit%C3%A4ten/)
* [Switch](https://github.com/numist/Switch)
* Windows Timline. See <https://winaero.com/blog/enable-use-timeline-windows-10/>, <https://www.digitaltrends.com/computing/how-to-use-windows-timeline/>, <http://winfuture.de/videos/Software/Windows-Timeline-So-funktioniert-die-Zeitleiste-von-Windows-10-18708.html>, and [Windows Timeline Introduction Video](https://youtu.be/jV09HpVj4gg?t=123)
* IntelliJ 2017.3 restores the current editors when changing branches in git. See https://blog.jetbrains.com/idea/2017/10/intellij-idea-2017-3-vcs-enhancements-and-more/.

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

It might be required to write plugins on the side of the supported application.
