# gradle-completion
Bash and Zsh completion support for [Gradle](https://gradle.org).

This provides tab completion support for completing:

 * Gradle tasks for the current project and subprojects
 * Gradle CLI options

It also handles custom default build files, so `rootProject.defaultBuildFile = 'build.gradle.kts'` is supported.

Performance of Zsh completion is significantly improved.

## Installation for Zsh 5.0+

Download and place `gradle-completion.zsh` on your `$fpath`. I recommend `$HOME/.zsh/gradle-completion`:
```
git clone git://github.com/eriwen/gradle-completion ~/.zsh/gradle-completion
```

Add the following do your '.zshrc' file:
```
echo "\nfpath=($HOME/.zsh/gradle-completion \$fpath)" >> ~/.zshrc
```

Start a new terminal session.

## Installation for Bash 3.2+

Download and place `gradle-completion.bash` in your `bash_completion.d` folder, usually `/etc/bash_completion.d`, `/usr/local/etc/bash_completion.d`, or `$HOME/bash_completion.d`:
```
mkdir $HOME/bash_completion.d
wget -O $HOME/bash_completion.d/gradle-completion.bash https://raw.githubusercontent.com/eriwen/gradle-completion/master/gradle-completion.bash
```

Add the following to your `.bashrc` file:
```
source $HOME/bash_completion.d/gradle-completion.bash
```

Start a new terminal session.

## Additional Configuration

#### Cache config
The build script cache is invalidated if any *.gradle or *.gradle.kts files change. 
However, these completion scripts do not search for new build scripts every time completion is invoked, because
that would make completion ~20x slower (unless you have so really good ideas on this).

By default, the build script cache is invalidated every 3 weeks (30240 minutes). 
You can configure this value by exporting a new value for `$GRADLE_CACHE_TTL_MINUTES`:
```
export GRADLE_CACHE_TTL_MINUTES=$(expr 1440 \* number_of_days_you_want)
```

## Acknowledgements
Bash completion is inspired by [Nolan Lawson's Gradle tab completion for bash](https://gist.github.com/nolanlawson/8694399).

Zsh completion is an improved version of [zsh](https://github.com/zsh-users/zsh)'s built-in Gradle completion. The intent is for mature improvements to be merged back into zsh.

Current improvements over built-in support:
 - Subproject tasks are completed
 - Gradle CLI options are current as of Gradle 3.3
 - Handles default build file as specified in settings.gradle
 - Completion performance is significantly improved
