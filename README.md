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

Download the latest release tarball and extract it into your oh-my-zsh plugins directory:

```bash
VERSION=1.4.4  # Replace with the latest version from https://github.com/gradle/gradle-completion/releases
curl -L https://github.com/gradle/gradle-completion/releases/download/v${VERSION}/gradle-completion-${VERSION}.tar.gz | tar xz -C ~/.oh-my-zsh/plugins/
```

Add `gradle-completion` to the plugins array in your '.zshrc' file:
```bash
plugins+=(gradle-completion)
```

Start a new terminal session.

### Install manually

Download the latest release tarball and extract it to your preferred location:

```bash
VERSION=1.4.4  # Replace with the latest version from https://github.com/gradle/gradle-completion/releases
mkdir -p ~/.zsh
curl -L https://github.com/gradle/gradle-completion/releases/download/v${VERSION}/gradle-completion-${VERSION}.tar.gz | tar xz -C ~/.zsh/
```

Add the following to your '.zshrc' file:
```bash
fpath=(~/.zsh/gradle-completion-${VERSION} $fpath)
autoload -Uz compinit && compinit
```

Start a new terminal session. You may need to disable the `gradle` plugin for `oh-my-zsh`.

#### (Optional) Manual Completion Cache Initialization
Completion cache initialization happens the first time you invoke completion,
and usually takes a few seconds, depending on the size of your project. You can
manually initialize the cache and avoid interrupting your development mojo by
running:

```bash
cd path/to/your-project
# For oh-my-zsh installation:
source ~/.oh-my-zsh/plugins/gradle-completion-VERSION/_gradle 1>&2 2>/dev/null; __gradle-completion-init
# OR for manual installation:
source ~/.zsh/gradle-completion-VERSION/_gradle 1>&2 2>/dev/null; __gradle-completion-init
```

## Installation for Bash 3.2+

This script depends on the `bash-completion` framework, which is not installed on macOS by default.

### Install via [Homebrew](https://brew.sh)

1.  **Install the `bash-completion` framework.** This is a required prerequisite.
    ```
    brew install bash-completion
    ```

2.  **Install `gradle-completion`.**
    ```
    brew install gradle-completion
    ```

3.  **Configure your `.bash_profile`**. The `bash-completion` framework must be sourced in your profile. When you installed it, Homebrew provided the exact line to add. Add this to your `~/.bash_profile`:
    ```bash
    echo '[[ -r "$(brew --prefix)/etc/profile.d/bash_completion.sh" ]] && . "$(brew --prefix)/etc/profile.d/bash_completion.sh"' >> ~/.bash_profile
    ```

4.  **Start a new terminal session** or run `source ~/.bash_profile`.

### Install manually

1.  **Ensure `bash-completion` is installed and configured.** You can install it with your favorite package manager or by following the [official installation instructions](https://github.com/scop/bash-completion/blob/master/README.md#installation). The main `bash_completion.sh` script must be sourced in your `.bash_profile` or `.bashrc` for this to work.

2.  **Download `gradle-completion.bash`** and place it in your `bash_completion.d` folder (e.g., `/usr/local/etc/bash_completion.d` or `$HOME/bash_completion.d`).
    ```
    # Example for Homebrew on Intel Macs
    curl -LA gradle-completion https://edub.me/gradle-completion-bash -o $(brew --prefix)/etc/bash_completion.d/gradle-completion.bash
    ```

3.  **Start a new terminal session.** The `bash-completion` framework will automatically source the script.

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

**Note for contributors:** The completion scripts (`gradle-completion.bash` and `_gradle`) are generated from templates and are not committed to git (they're in `.gitignore`). Run `./gradlew generateCompletionScripts` to regenerate them locally during development. For installation, users should download release tarballs which include the pre-generated completion scripts.

## For Maintainers: Releasing

This project uses template-based generation for completion scripts. The actual `gradle-completion.bash` and `_gradle` files are generated from templates during the release process and are **not committed to git** (they're in `.gitignore`).

### Release Process

1. **Create and push a tag:**
   ```bash
   git tag v1.x.x
   git push origin v1.x.x
   ```

2. **GitHub Actions automatically:**
    - Checks out the code at the tag
    - Sets up Java 17 and Gradle
    - Runs `./gradlew generateCompletionScripts` to generate completion files from templates
    - Creates a release tarball (`gradle-completion-1.x.x.tar.gz`) containing:
        - Generated completion scripts (`gradle-completion.bash`, `_gradle`)
        - Template files (`gradle-completion.bash.template`, `_gradle.template`)
        - Build configuration and other necessary files
    - Creates a GitHub release and uploads the tarball as a release asset

3. **Update Homebrew formula:**
   ```bash
   ./bump-version.sh 1.x.x
   ```
   This script downloads the release asset tarball from GitHub, calculates its SHA256, and creates a Homebrew bump PR.

   **Important:** The script uses the release asset URL (`releases/download/v1.x.x/gradle-completion-1.x.x.tar.gz`), not the automatic source archive, because the generated completion scripts are only available in the release asset.

### Regenerating Completion Scripts Locally

To regenerate completion scripts from templates during development:

```bash
./gradlew generateCompletionScripts
```

This will update `gradle-completion.bash` and `_gradle` based on the current Gradle version's CLI options. These generated files are in `.gitignore` and should not be committed to git.

## Acknowledgements
Bash completion is inspired by [Nolan Lawson's Gradle tab completion for bash](https://gist.github.com/nolanlawson/8694399).

Zsh completion is an improved version of [zsh](https://github.com/zsh-users/zsh)'s built-in Gradle completion.

Current improvements over built-in support:
- Subproject tasks are completed
- Gradle CLI options are current as of Gradle 9.2.0
- Common Gradle properties are completed
- Handles default build file as specified in settings.gradle
- ~20x faster completion speed for medium to large projects
- Completion cache updates in the background after first invocation
