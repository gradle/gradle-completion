# gradle-completion
Bash and Zsh completion support for [Gradle](https://gradle.org).

This provides _fast_ tab completion for:

 * Gradle tasks for the current project and sub-projects
 * Gradle CLI switches (e.g. `--parallel`)
 * Common Gradle properties (e.g. `-Dorg.gradle.debug`)

It also handles custom default build files, so `rootProject.buildFileName = 'build.gradle.kts'` is supported.

See instructions [for bash](https://github.com/gradle/gradle-completion/blob/master/README.md#installation-for-bash-32) or [for zsh](https://github.com/gradle/gradle-completion/blob/master/README.md#installation-for-zsh-50), then consider optional [additional configuration](https://github.com/gradle/gradle-completion/blob/master/README.md#additional-configuration).

Here's a demo for the gradle project itself:
![Completion demo](gradle-completion-4.0.gif)

## Installation for Zsh 5.0+

### Install via [Homebrew](https://brew.sh)

```
brew install gradle-completion

# Ensure /usr/local/share/zsh/site-functions is on $fpath. You should get a result from the following
echo $fpath | grep "/usr/local/share/zsh/site-functions"
```

### Install as [oh-my-zsh](https://ohmyz.sh/) plugin

Download and place the plugin and completion script into your oh-my-zsh plugins directory. 
```
git clone git://github.com/gradle/gradle-completion ~/.oh-my-zsh/plugins/gradle-completion
```

Add `gradle-completion` to the plugins array in your '.zshrc' file.
```
plugins+=(gradle-completion)
```

### Install manually

Download and place `_gradle` on your `$fpath`. I recommend `$HOME/.zsh/gradle-completion`:
```
git clone git://github.com/gradle/gradle-completion ~/.zsh/gradle-completion
```

Add the following do your '.zshrc' file:
```
echo "\nfpath=($HOME/.zsh/gradle-completion \$fpath)" >> ~/.zshrc
```

Start a new terminal session. You may need to disable the `gradle` plugin for `oh-my-zsh`.

#### (Optional) Manual Completion Cache Initialization
Completion cache initialization happens the first time you invoke completion,
and usually takes a few seconds, depending on the size of your project. You can
manually initialize the cache and avoid interrupting your development mojo by
running:

```bash
cd path/to/your-project
source ~/.zsh/gradle-completion/_gradle 1>&2 2>/dev/null; __gradle-completion-init
```

## Installation for Bash 3.2+

### Install via [Homebrew](https://brew.sh)

```
brew install gradle-completion

# Source completion scripts from bash-completion in your bash profile
echo '[[ -r "/usr/local/etc/profile.d/bash_completion.sh" ]] && . "/usr/local/etc/profile.d/bash_completion.sh"' >> ~/.bash_profile

# Load changes to bash profile
source ~/.bash_profile
```

### Install manually

Download and place `gradle-completion.bash` in your `bash_completion.d` folder, usually `/etc/bash_completion.d`, `/usr/local/etc/bash_completion.d`, or `$HOME/bash_completion.d`:
```
mkdir $HOME/bash_completion.d
curl -LA gradle-completion https://edub.me/gradle-completion-bash -o $HOME/bash_completion.d/gradle-completion.bash
```

NOTE: Ensure `bash-completion` 1.2+ is installed using your favorite package manager or by following the [bash-completion installation instructions](https://github.com/scop/bash-completion/blob/master/README.md#installation).

Add the following to your `.bash_profile` (macOS) or `.bashrc` (Linux) file:
```
source $HOME/bash_completion.d/gradle-completion.bash
```

Start a new terminal session.

#### (Optional) Manual Completion Cache Initialization
Completion cache initialization happens the first time you invoke completion,
and usually takes a few seconds, depending on the size of your project. You can
manually initialize the cache and avoid interrupting your development mojo by
running:

```bash
cd path/to/your-project
__gradle-completion-init
```

## Additional Configuration

#### Excluding build scripts from UP-TO-DATE check
Tab completion checks known build scripts to see if they've been changed and refreshes the task cache if so.
You can exclude build scripts from this check (and speed up completion) by specifying:

```bash
# Default is `"/(build|integTest|out)/"`
export GRADLE_COMPLETION_EXCLUDE_PATTERN="/(build|integTest|samples|smokeTest|testFixtures|templates|out|features)/"
# Essentially turn off checking for changed scripts
export GRADLE_COMPLETION_EXCLUDE_PATTERN="gradle"
```

#### Completion cache
One can manually (re)generate the completion cache by invoking `__gradle-completion-init` after the
completion script has been sourced. This graciously avoids an unexpected cache build when invoking
completion.

The build script cache is invalidated if any *.gradle or *.gradle.kts files change.
However, these completion scripts do not search for new build scripts every time completion is invoked, because
that would make completion ~20x slower (unless you have so really good ideas on this).

By default, the build script cache is invalidated every 3 weeks (30240 minutes).
You can configure this value by exporting a new value for `$GRADLE_CACHE_TTL_MINUTES`:
```bash
export GRADLE_CACHE_TTL_MINUTES=$(expr 1440 \* number_of_days_you_want)
```

#### Implicit Tasks
Gradle allows you to access tasks of subprojects from the project root implicitly.
For example, given these tasks:
```
:foo
:help
:bar:baz
```

You can execute `gradle baz` from the project root and it will execute `:bar:baz`.

gradle-completion will not tab complete these tasks by default because it adds a
significant number of completion options, which may not be what you want and
negatively impacts completion speed.

To allow completion of implicit tasks, set `$GRADLE_COMPLETION_UNQUALIFIED_TASKS=true`:

```bash
export GRADLE_COMPLETION_UNQUALIFIED_TASKS="true"
```

You may need to invalidate the cache using the cache config above or by executing `touch build.gradle`.

## Troubleshooting
If zsh completion isn't working, first try checking your `$fpath` with `echo $fpath`.

zsh completion using `./gradlew` may not work on Linux if you don't have `.` on your `$PATH`,
so I recommend adding it in your `~/.zshrc` file:
```bash
export PATH=".:$PATH"
```

> HEADS UP: If you get an error 'parse error near `]]"', please [upgrade zsh](http://stackoverflow.com/questions/17648621/how-do-i-update-zsh-to-the-latest-version).
zsh 5.0.5 has a bug in script parsing that is fixed as of zsh 5.0.8. See issues #4 and #7 for more details.

If zsh completion reports "Generating Gradle task cache..." every time, the zsh completion cache
may be disabled. Enable it by adding the following to your `~/.zshrc` file:
```bash
zstyle ':completion:*' use-cache on
```

## Contributing

See the [contributing guide](CONTRIBUTING.md).

## Acknowledgements
Bash completion is inspired by [Nolan Lawson's Gradle tab completion for bash](https://gist.github.com/nolanlawson/8694399).

Zsh completion is an improved version of [zsh](https://github.com/zsh-users/zsh)'s built-in Gradle completion.

Current improvements over built-in support:
 - Subproject tasks are completed
 - Gradle CLI options are current as of Gradle 6.4
 - Common Gradle properties are completed
 - Handles default build file as specified in settings.gradle
 - ~20x faster completion speed for medium to large projects
 - Completion cache updates in the background after first invocation
