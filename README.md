# ContextSwitcher

> ContextSwitcher is [Mylyn](https://www.eclipse.org/mylyn/) for the Windows desktop.

**Call for student development** In relation to [ICSE 2021](https://conf.researchr.org/home/icse-2021) in the context of [SCORE 2021](https://conf.researchr.org/home/icse-2021/score-2021) --> <https://conf.researchr.org/home/icse-2021/score-2021#contextswitcher-focus-on-the-thing-to-be-done>

## Motivation

Suppose you are working on a task.
You have opened IntelliJ, some browser tabs, and a Word document.
Now, an urgent email comes in.
This requires you to switch the context to another task.
When you left that task, you had different browser windows and different Word documents opened.
ContextSwitcher restores these at a finger type.

More simpler: ContextSwitcher is a workspace manager across multiple applications.

## Example tasks and opened applications

* “Search for city tours in Leipzig”: Chrome and OneNote (for taking notes)
* “Hold meeting with screen sharing”: Chrome (meeting application), Word (Documents), Excel (Documents), Explorer (SVN directory listing)
* “Collection information about a topic”: Chrome and JabRef (for storing the bibliography entries)
* “Meeting with students about their project”: Outlook (with all relevant emails), Notepad++ (for meeting minutes), Explorer (showing the project directory), Eclipse (with the current workspace and the linked Mylyn task being active)
* Working on a large LaTeX document

In each opened application, only the relevant files (tabs) should be opened.
When switching a task, the already opened applications should be left opened, not required should be closed and new ones have to be opened.
The same applies to files (tabs) in the applications.
If possible, the last focused position (line in Word documents, cell in Excel, slide in PowerPoint) should be restored.

### Comparision to Virtual Desktops

Users use virtual desktops in two ways:

* Each virtual desktop for a context
* Multiple desktops for a single context

When using each virtual desktop for a context, the first context might be "email reading and surfing" and the next one "coding".
For "Thesis writing", a third desktop "LaTeX" needs to be opened.
Thus, one needs n different desktops for n tasks.
Alternatively, virutal desktops need to be resued and maybe won't be named properly (e.g., "LaTeX" versus "Desktop 2").

When using multipe desktops for a single context, the current context might be "coding" with the virtual desktops "email reading and surfing" and "coding". When switching to the context "Thesis writing", browser tabs need to be closed and new ones opened, JabRef will be loaded, the "coding" desktop will be renamed to "LaTeX" (containing TeXstudio).

#### Application limitations

* Microsoft Office applications show all opened windows in the "Window" drop down. ContextSwitcher will display only relevent Windows for the task (because only these documents are opened)
* Skype cannot be opened in multipe virtual desktops

## Background and related work

When working with code in Eclipse, one has opened many files by the time.
Often, not all opened files are required for a task.
What if only the relevant files are opened and even the most touched lines are highlighted?
What if one can give a colleague a reference to the current state of the IDE to enable a simpler reproduction of an issue?
For this, the concept of a “[task-focused interface](https://en.wikipedia.org/wiki/Task-focused_interface)” has been invented and turned into software as Eclipse [Mylyn](https://www.eclipse.org/mylyn/). 
Regarding supporting the task-focused interface across multiple applications, the [Tasktop Dev Standalone Application](http://www.tasktop.com/node/1176/) is available.
It was announced as [complete desktop task-focused interface for everyone](https://www.infoq.com/news/2008/02/tasktop-10), but it does not support arbitrary applications.

### Windows

* Windows Timline. See <https://winaero.com/blog/enable-use-timeline-windows-10/>, <https://www.digitaltrends.com/computing/how-to-use-windows-timeline/>, <http://winfuture.de/videos/Software/Windows-Timeline-So-funktioniert-die-Zeitleiste-von-Windows-10-18708.html>, and [Windows Timeline Introduction Video](https://youtu.be/jV09HpVj4gg?t=123)
* [Windows 10 Sets](https://insider.windows.com/de-de/articles/introducing-sets/).
  [Sundown in April 2019](https://www.heise.de/newsticker/meldung/Bedienkonzept-Microsoft-beerdigt-Sets-fuer-Windows-10-4404211.html).
* [bug.n](https://github.com/fuhsjr00/bug.n) - can provide views on opened windows.

### Mac OS X

* [Switch](https://github.com/numist/Switch)

### Linux

* [Plasma Activities](https://wiki.ubuntuusers.de/Plasma/Aktivit%C3%A4ten/)

### Special Features

* IntelliJ 2017.3 restores the current editors when changing branches in git.
  See <https://blog.jetbrains.com/idea/2017/10/intellij-idea-2017-3-vcs-enhancements-and-more/>.
* The [Bento Browser](https://bentobrowser.com/) by the [Carnegie Mellon Human Computer Interaction Institute](https://hcii.cmu.edu/) groups browser tabs into projects.
* [Marketer Browser](https://www.marketerbrowser.com/) supports multiple accounts for the same web page
  * Similar to [Session Box](https://sessionbox.io/)
  * Similar to [BiscuitBrowser](https://eatbiscuit.com/)
* [Workona](https://workona.com/) - workspaces in the browser

## Goal

In this project, a framework with an UI and ten plugins for task-focused applications on Windows should be implemented.
The main UI should be kept simple:
A list of tasks with the possibility to add a new task, remove a task and focus a task.

Following Applications should be supported:

* Windows Virtual Desktop
  * The names of the virtual desktop should change according to the context (e.g., in the context "Working on a large LaTeX document": "vs.code" (having vs.code and guit gui) and "compilation result" (showing SumatraPDF)).
* Chrome
  * Not possible using [ChromeDriver](https://sites.google.com/a/chromium.org/chromedriver/downloads), because [just attaching to a running instance isn't technically possible](https://github.com/seleniumhq/selenium-google-code-issue-archive/issues/18#issuecomment-191402419) 
  * Or implement own extension based on messaging: <https://github.com/vakho10/Native-Messaging> or <http://git.javadeploy.net/coderrooftrellen/simple-chrome-extension/blob/f4d38c82c7e7cce68c89a5d008a26c7689cfe4fa/Readme.md>
* Firefox
* Word
* Excel
* PowerPoint
* Windows Explorer (with [QTTabBar](http://qttabbar.wikidot.com/) extension)
* Outlook
* Notepad++
* IntelliJ
* Eclipse (with [Mylyn](https://www.eclipse.org/mylyn/) plugin)

## Development Aspects

In the following, development aspects are given.

### Development phases

The intended development phases for this project are as follows:

1. Skeleton for core module to store context and to switch context ("core")
2. Firefox plugin for getting/setting context in Firefox and in the core
3. Microsoft Word plugin for getting/setting context in Firefox and in the core
4. UI for the core
5. Integrate cool stuff such as [ZEIº](https://timeular.com)

### Implementation

The chosen language to implement “Context Switcher” is free.

Following alternatives should be considered:

* AutoHotkey
* [AutoIt](https://www.autoitscript.com/site/) with [autoitx4java](https://github.com/sixtoad/autoitx4java)
* See also <https://alternativeto.net/software/autoit/>
* Python 3.0
* C#
* Java
  * Reuse code of [Eclipse Jubula](https://www.eclipse.org/jubula/)

It might be required to write a plugin foreach supported application.
