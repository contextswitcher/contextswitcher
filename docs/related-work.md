# ContextSwitcher — Related Work

When working with code in Eclipse, one has opened many files by the time.
Often, not all opened files are required for a specific project.
Moreover, when looking deeper into a project, there are different "tasks" to do.
Currently, such tasks are called "issues".
See the GitHub guidelines on issues for more information on issues: <https://guides.github.com/features/issues>.

## Windows

* Windows Timeline. See <https://winaero.com/blog/enable-use-timeline-windows-10/>, <https://www.digitaltrends.com/computing/how-to-use-windows-timeline/>, <http://winfuture.de/videos/Software/Windows-Timeline-So-funktioniert-die-Zeitleiste-von-Windows-10-18708.html>, and [Windows Timeline Introduction Video](https://youtu.be/jV09HpVj4gg?t=123)
* [Windows 10 Sets](https://insider.windows.com/de-de/articles/introducing-sets/).
  [Sundown in April 2019](https://www.heise.de/newsticker/meldung/Bedienkonzept-Microsoft-beerdigt-Sets-fuer-Windows-10-4404211.html).
* [bug.n](https://github.com/fuhsjr00/bug.n) - can provide views on opened windows.
* [Workspacer](https://github.com/rickbutton/workspacer) - tiling Window management on Windows - with multiple desktops.
* [Display Fusion](https://www.displayfusion.com/) - offers profiles, where screen resolution and window position is stored. No app starting, closing windows, ...

## Mac OS X

* [Switch](https://github.com/numist/Switch)

## Linux

* [Plasma Activities](https://docs.kde.org/trunk5/en/plasma-desktop/plasma-desktop/activities-interface.html)

## Browser

* [Session Buddy](https://chrome.google.com/webstore/detail/session-buddy/edacconmaakjimmfgnblocblbcdcpbko) Allows for grouping tabs, store the group for a later opening.
* Light Table as new IDE concept: <http://www.chris-granger.com/2012/04/12/light-table-a-new-ide-concept/>
* [ZenHub's Workspaces](https://help.zenhub.com/support/solutions/articles/43000495219). This concept is bound to GitHub.
* Firefox supports [profiles](https://developer.mozilla.org/en-US/docs/Mozilla/Firefox/Multiple_profiles) where separate Firefox configurations could be done (e.g., family or work).
  However, it requires much effort to create a Firefox profile for each context

## Special Features

* IntelliJ 2017.3 restores the current editors when changing branches in git.
  See <https://blog.jetbrains.com/idea/2017/10/intellij-idea-2017-3-vcs-enhancements-and-more/>.

  > IntelliJ IDEA saves your context (a set of opened files, the current run configuration, and breakpoints) provided that the Restore workspace on branch switching option is enabled in the Settings/Preferences dialog Ctrl+Alt+S under Version Control | Confirmation. When you switch to a branch, IntelliJ IDEA automatically restores your context associated with that branch.
  
* The [Bento Browser](https://bentobrowser.com/) by the [Carnegie Mellon Human Computer Interaction Institute](https://hcii.cmu.edu/) groups browser tabs into projects.
* [Marketer Browser](https://www.marketerbrowser.com/) supports multiple accounts for the same web page
  * Similar to [Session Box](https://sessionbox.io/)
  * Similar to [BiscuitBrowser](https://eatbiscuit.com/)
* [Workona](https://workona.com/) - workspaces in the browser
* Chrome Plugin [Simple Window Saver](https://chrome.google.com/webstore/detail/simple-window-saver/fpfmklldfnlcblofkhdeoohfppdoejdc)

  > Simple Window Saver makes it super easy to save and restore windows. Keep one window for work tabs, one for Gmail and Facebook, and one for your vacation planning or the research on that new TV you want to buy.

## Concept of "Task-Focused Interface"

The concept of a “[task-focused interface](https://en.wikipedia.org/wiki/Task-focused_interface)” has been invented and turned into software as Eclipse [Mylyn](https://www.eclipse.org/mylyn/).
It answers following questions:
What if only the relevant files are opened and even the most touched lines are highlighted?
What if one can give a colleague a reference to the current state of the IDE to enable a simpler reproduction of an issue?
One can read more about Mylyn at the [Mylyn Tutorial](https://web.archive.org/web/20170929190100/http://www.tasktop.com/mylyn/tutorial).
Concerning the availability of the task-focused interface across multiple applications, the [Tasktop Dev Standalone Application](http://www.tasktop.com/node/1176/) is available.
It was announced as [complete desktop task-focused interface for everyone](https://www.infoq.com/news/2008/02/tasktop-10), but it does not support arbitrary applications.

## Agent Orchestration

* [Orca](https://github.com/stablyai/orca) - desktop app for running several coding agents (Claude Code, Codex, ...) side by side, each in its own git worktree, tracked in one place, with a mobile companion.
  It covers the agent-and-worktree slice of a task only; browser tabs, IDE windows, and other applications stay outside.

## Research Papers

* Focusing knowledge work with task context. PhD thesis. Available at: <https://www.researchgate.net/publication/235350419_Focusing_knowledge_work_with_task_context>
* What You See Is What You Need: [WYSIWYN: Using Task Focus to Ease Collaboration](http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.99.3548). [[PDF](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.99.3548&rep=rep1&type=pdf)]
* [Beyond Integrated Development Environments: Adding Context to Software Development](https://doi.org/10.1109/ICSE-NIER.2019.00027). This paper reasons on an "Automated Assistant" and calls for a deeper investigation of the concept of "context". In contrast, the ContextSwitcher assumes that the user knows about his context and defines queries for the context by themselves.
