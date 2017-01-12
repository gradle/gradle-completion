# gradle-completion
Bash and Zsh completion support for [Gradle](https://gradle.org).

This provides tab completion support for completing:

 * Gradle tasks for the current project and subprojects
 * Gradle CLI options

It also handles custom default build files, so `rootProject.defaultBuildFile = 'build.gradle.kts'` is supported.

Performance of Zsh completion is significantly improved.

## Installation for Zsh 5.0+

Download and place `gradle-completion.zsh` on your `$fpath`. I recommend `$HOME/.zsh/completions`:
```
wget -O $HOME/.zsh/completions/_gradle https://raw.githubusercontent.com/eriwen/gradle-completion/master/gradle-completion.zsh
```

## Installation for Bash 3.2+

Download from GitHub:
```
wget -O https://raw.githubusercontent.com/eriwen/gradle-completion/master/gradle-completion.bash
```

Place it in your `bash_completion.d` folder, usually `/etc/bash_completion.d`, `/usr/local/etc/bash_completion.d`, or `$HOME/bash_completion.d`

Alternatively, you can source it manually or through your `.bashrc` file:
```
source /path/to/gradle-completion.bash
```

## Acknowledgements
Bash completion is inspired by [Nolan Lawson's Gradle tab completion for bash](https://gist.github.com/nolanlawson/8694399).

Zsh completion is an improved version of [zsh](https://github.com/zsh-users/zsh)'s built-in Gradle completion. The intent is for mature improvements to be merged back into zsh.

Current improvements over built-in support:
 - Subproject tasks are completed
 - Gradle CLI options are current as of Gradle 3.3
 - Handles default build file as specified in settings.gradle
 - Completion performance is significantly improved
