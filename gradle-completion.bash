__gradle-completion-print-timestamp() {
    echo "$(($(gdate +'%s * 1000 + %-N / 1000000'))) - $1"
}

__gradle-set-project-root-dir() {
    local dir=$(pwd)
    project_root_dir=$(pwd)
    while [[ "$dir" != '/' ]]; do
        if [[ -f "$dir/settings.gradle" || -f "$dir/settings.gradle.kts" || -f "$dir/gradlew" ]]; then
            project_root_dir="$dir"
            return 0
        fi
        dir="$(dirname "$dir")"
    done
    return 1
}

__gradle-init-cache-dir() {
    cache_dir="${GRADLE_USER_HOME:-$HOME/.gradle}/completion"
    mkdir -p "$cache_dir"
}

__gradle-set-settings-file() {
    # In order of precedence: --settings-file=filename, settings.gradle, settings.gradle.kts

    local default_gradle_settings_file="$project_root_dir/settings.gradle"
    if [[ ! -f $default_gradle_settings_file ]]; then
      default_gradle_settings_file="$project_root_dir/settings.gradle.kts"
    fi
    gradle_settings_file=$default_gradle_settings_file
}

__gradle-set-build-file() {
    __gradle-set-settings-file
    # In order of precedence: --build-file=filename, rootProject.buildFileName, build.gradle, build.gradle.kts

    local default_gradle_build_file_name="build.gradle"
    if [[ -f $gradle_settings_file ]]; then
      local build_file_name=$(grep "^rootProject\.buildFileName" $gradle_settings_file | \
          sed -n -e "s/rootProject\.buildFileName = [\'\"]\(.*\)[\'\"]/\1/p")
      default_gradle_build_file_name="${build_file_name:-build.gradle}"
    fi

    gradle_build_file="$project_root_dir/$default_gradle_build_file_name"
    if [[ ! -f $gradle_build_file ]]; then
        gradle_build_file="$project_root_dir/build.gradle.kts"
    fi
}

__gradle-set-cache-name() {
    # Cache name is constructed from the absolute path of the build file.
    cache_name="${gradle_build_file//\//_}"
}

__gradle-set-files-checksum() {
    # Cache MD5 sum of all Gradle scripts and modified timestamps
    if builtin command -v md5 > /dev/null; then
        gradle_files_checksum=$(md5 -q -s "$(cat "$cache_dir/$cache_name" | xargs ls -o 2>/dev/null)")
    elif builtin command -v md5sum > /dev/null; then
        gradle_files_checksum=$(cat "$cache_dir/$cache_name" | xargs ls -o 2>/dev/null | md5sum | awk '{print $1}')
    else
        echo "Cannot generate completions as neither md5 nor md5sum exist on \$PATH"
    fi
}

__gradle-generate-script-cache() {
    # Invalidate cache after 3 weeks by default
    local cache_ttl_mins=${GRADLE_CACHE_TTL_MINUTES:-30240}
    local script_exclude_pattern=${GRADLE_COMPLETION_EXCLUDE_PATTERN:-"/(.git|build|integTest|samples|templates|smokeTest|testFixtures|out)/"}

    if [[ ! $(find "$cache_dir/$cache_name" -mmin "-${cache_ttl_mins}" 2>/dev/null) ]]; then
        # Cache all Gradle scripts
        local gradle_build_scripts=$(find "$project_root_dir" -type f -name "*.gradle" -o -name "*.gradle.kts" 2>/dev/null | grep -E -v "$script_exclude_pattern")
        printf "%s\n" "${gradle_build_scripts[@]}" >| "$cache_dir/$cache_name"
    fi
}

__gradle-long-options() {
    local cur
    _get_comp_words_by_ref -n : cur

    local args="\
    --build-cache                  - Enables the Gradle build cache. Gradle will try to reuse outputs from previous builds. 
    --configuration-cache          - Enables the configuration cache. Gradle will try to reuse the build configuration from previous builds. 
    --configuration-cache-problems - Configures how the configuration cache handles problems (fail or warn). Defaults to fail. 
    --configure-on-demand          - Configure necessary projects only. Gradle will attempt to reduce configuration time for large multi-project builds.  [incubating]
    --console                      - Specifies which type of console output to generate. Values are 'plain', 'colored', 'auto' (default), 'rich' or 'verbose'. 
    --continue                     - Continue task execution after a task failure. 
    --continuous                   - Enables continuous build. Gradle does not exit and will re-execute tasks when task file inputs change. 
    --daemon                       - Uses the Gradle daemon to run the build. Starts the daemon if not running. 
    --debug                        - Log in debug mode (includes normal stacktrace). 
    --dependency-verification      - Configures the dependency verification mode. Values are 'strict', 'lenient' or 'off'. 
    --dry-run                      - Run the builds with all task actions disabled. 
    --exclude-task                 - Specify a task to be excluded from execution. 
    --export-keys                  - Exports the public keys used for dependency verification. 
    --foreground                   - Starts the Gradle daemon in the foreground. 
    --full-stacktrace              - Print out the full (very verbose) stacktrace for all exceptions. 
    --gradle-user-home             - Specifies the Gradle user home directory. Defaults to ~/.gradle 
    --help                         - Shows a help message. 
    --include-build                - Include the specified build in the composite. 
    --info                         - Set log level to info. 
    --init-script                  - Specify an initialization script. 
    --max-workers                  - Configure the number of concurrent workers Gradle is allowed to use. 
    --no-build-cache               - Disables the Gradle build cache. 
    --no-configuration-cache       - Disables the configuration cache. 
    --no-configure-on-demand       - Disables the use of configuration on demand.  [incubating]
    --no-continue                  - Stop task execution after a task failure. 
    --no-daemon                    - Do not use the Gradle daemon to run the build. Useful occasionally if you have configured Gradle to always run with the daemon by default. 
    --no-parallel                  - Disables parallel execution to build projects. 
    --no-problems-report           - (Experimental) disables HTML problems report 
    --no-rebuild                   - Do not rebuild project dependencies. 
    --no-scan                      - Disables the creation of a Build Scan. 
    --no-watch-fs                  - Disables watching the file system. 
    --offline                      - Execute the build without accessing network resources. 
    --parallel                     - Build projects in parallel. Gradle will attempt to determine the optimal number of executor threads to use. 
    --priority                     - Specifies the scheduling priority for the Gradle daemon and all processes launched by it. Values are 'normal' (default) or 'low' 
    --problems-report              - (Experimental) enables HTML problems report 
    --profile                      - Profile build execution time and generates a report in the <build_dir>/reports/profile directory. 
    --project-cache-dir            - Specify the project-specific cache directory. Defaults to .gradle in the root project directory. 
    --project-dir                  - Specifies the start directory for Gradle. Defaults to current directory. 
    --property-upgrade-report      - (Experimental) Runs build with experimental property upgrade report. 
    --quiet                        - Log errors only. 
    --refresh-dependencies         - Refresh the state of dependencies. 
    --refresh-keys                 - Refresh the public keys used for dependency verification. 
    --rerun-tasks                  - Ignore previously cached task results. 
    --scan                         - Generate a Build Scan (powered by Develocity). 
    --stacktrace                   - Print out the stacktrace for all exceptions. 
    --status                       - Shows status of running and recently stopped Gradle daemon(s). 
    --stop                         - Stops the Gradle daemon if it is running. 
    --task-graph                   - (Experimental) Print task graph instead of executing tasks. 
    --update-locks                 - Perform a partial update of the dependency lock, letting passed in module notations change version.  [incubating]
    --warn                         - Set log level to warn. 
    --warning-mode                 - Specifies which mode of warnings to generate. Values are 'all', 'fail', 'summary'(default) or 'none' 
    --watch-fs                     - Enables watching the file system for changes, allowing data about the file system to be re-used for the next build. 
    --write-locks                  - Persists dependency resolution for locked configurations, ignoring existing locking information if it exists 
    --write-verification-metadata  - Generates checksums for dependencies used in the project (comma-separated list)\
"

    COMPREPLY=( $(compgen -W "$args" -- "$cur") )
}

__gradle-properties() {
    local cur
    _get_comp_words_by_ref -n : cur

    local args="\
-Dgradle.user.home=                       - Specifies the Gradle user home directory. Defaults to ~/.gradle
-Dorg.gradle.projectcachedir=             - Specify the project-specific cache directory. Defaults to .gradle in the root project directory.
-Dorg.gradle.continue=                    - Continue task execution after a task failure.
-Dorg.gradle.continuous.quietperiod=      - 
-Dorg.gradle.configureondemand=           - Configure necessary projects only. Gradle will attempt to reduce configuration time for large multi-project builds.
-Dorg.gradle.caching=                     - Enables the Gradle build cache. Gradle will try to reuse outputs from previous builds.
-Dorg.gradle.caching.debug=               - 
-Dorg.gradle.vfs.watch=                   - Enables watching the file system for changes, allowing data about the file system to be re-used for the next build.
-Dorg.gradle.vfs.verbose=                 - 
-Dorg.gradle.dependency.verification=     - Configures the dependency verification mode. Values are 'strict', 'lenient' or 'off'.
-Dorg.gradle.configuration-cache.problems= - Configures how the configuration cache handles problems (fail or warn). Defaults to fail.
-Dorg.gradle.configuration-cache=         - Enables the configuration cache. Gradle will try to reuse the build configuration from previous builds.
-Dorg.gradle.configuration-cache.inputs.unsafe.ignore.in-serialization= - 
-Dorg.gradle.configuration-cache.unsafe.ignore.unsupported-build-events-listeners= - 
-Dorg.gradle.configuration-cache.max-problems= - 
-Dorg.gradle.configuration-cache.inputs.unsafe.ignore.file-system-checks= - 
-Dorg.gradle.configuration-cache.parallel= - 
-Dorg.gradle.configuration-cache.read-only= - 
-Dorg.gradle.configuration-cache.integrity-check= - 
-Dorg.gradle.configuration-cache.entries-per-key= - 
-Dorg.gradle.configuration-cache.heap-dump-dir= - 
-Dorg.gradle.configuration-cache.fine-grained-property-tracking= - 
-Dorg.gradle.unsafe.isolated-projects=    - 
-Dorg.gradle.problems.report=             - (Experimental) enables HTML problems report
-Dorg.gradle.logging.level=               - 
-Dorg.gradle.logging.stacktrace=          - 
-Dorg.gradle.console=                     - Specifies which type of console output to generate. Values are 'plain', 'colored', 'auto' (default), 'rich' or 'verbose'.
-Dorg.gradle.warning.mode=                - Specifies which mode of warnings to generate. Values are 'all', 'fail', 'summary'(default) or 'none'
-Dorg.gradle.welcome=                     - 
-Dorg.gradle.daemon.idletimeout=          - 
-Dorg.gradle.daemon.healthcheckinterval=  - 
-Dorg.gradle.daemon.registry.base=        - 
-Dorg.gradle.jvmargs=                     - 
-Dorg.gradle.java.home=                   - 
-Dorg.gradle.debug=                       - 
-Dorg.gradle.debug.host=                  - 
-Dorg.gradle.debug.port=                  - 
-Dorg.gradle.debug.server=                - 
-Dorg.gradle.debug.suspend=               - 
-Dorg.gradle.daemon=                      - Uses the Gradle daemon to run the build. Starts the daemon if not running.
-Dorg.gradle.priority=                    - Specifies the scheduling priority for the Gradle daemon and all processes launched by it. Values are 'normal' (default) or 'low'
-Dorg.gradle.native=                      - 
-Dorg.gradle.parallel=                    - Build projects in parallel. Gradle will attempt to determine the optimal number of executor threads to use.
-Dorg.gradle.workers.max=                 - Configure the number of concurrent workers Gradle is allowed to use.
-Dorg.gradle.java.installations.paths=    - 
-Dorg.gradle.java.installations.fromEnv=  - 
-Dorg.gradle.java.installations.auto-detect= - 
-Dorg.gradle.java.installations.auto-download= - 
-Dorg.gradle.java.installations.idea-jdks-directory= -\
"
    COMPREPLY=( $(compgen -W "$args" -- "$cur") )
    return 0
}

__gradle-short-options() {
    local cur
    _get_comp_words_by_ref -n : cur

    local args="\
    -a                             - Do not rebuild project dependencies. 
    -d                             - Log in debug mode (includes normal stacktrace). 
    -F                             - Configures the dependency verification mode. Values are 'strict', 'lenient' or 'off'. 
    -g                             - Specifies the Gradle user home directory. Defaults to ~/.gradle 
    -h                             - Shows a help message. 
    -I                             - Specify an initialization script. 
    -i                             - Set log level to info. 
    -m                             - Run the builds with all task actions disabled. 
    -M                             - Generates checksums for dependencies used in the project (comma-separated list) 
    -p                             - Specifies the start directory for Gradle. Defaults to current directory. 
    -q                             - Log errors only. 
    -s                             - Print out the stacktrace for all exceptions. 
    -S                             - Print out the full (very verbose) stacktrace for all exceptions. 
    -t                             - Enables continuous build. Gradle does not exit and will re-execute tasks when task file inputs change. 
    -U                             - Refresh the state of dependencies. 
    -w                             - Set log level to warn. 
    -x                             - Specify a task to be excluded from execution.\
"
    COMPREPLY=( $(compgen -W "$args" -- "$cur") )
}

__gradle-tasks() {
    local cur
    _get_comp_words_by_ref -n : cur

    __gradle-init-cache-dir
    __gradle-set-project-root-dir
    __gradle-set-build-file
    if [[ -f "$gradle_build_file" ]]; then
        __gradle-set-cache-name
        __gradle-generate-script-cache
        __gradle-set-files-checksum

        # The cache key is md5 sum of all gradle scripts, so it's valid if it exists.
        if [[ -f "$cache_dir/$cache_name.md5" ]]; then
            local cached_checksum="$(cat "$cache_dir/$cache_name.md5")"
            local -a cached_tasks
            if [[ -z "$cur" ]]; then
                cached_tasks=( $(grep -v "^:" "$cache_dir/$cached_checksum") )
            else
                cached_tasks=( $(grep "^$cur" "$cache_dir/$cached_checksum") )
            fi
            COMPREPLY=( $(compgen -W "${cached_tasks[*]}" -- "$cur") )
        else
            __gradle-notify-tasks-cache-build
        fi

        # Regenerate tasks cache in the background
        if [[ "$gradle_files_checksum" != "$(cat "$cache_dir/$cache_name.md5")" || ! -f "$cache_dir/$gradle_files_checksum" || $(wc -c < $cache_dir/$gradle_files_checksum) -le 1 ]]; then
            $(__gradle-generate-tasks-cache 1>&2 2>/dev/null &)
        fi
    else
        # Default tasks available outside Gradle projects
        local args="buildEnvironment     - Displays all buildscript dependencies declared in root project.
components           - Displays the components produced by root project.
dependencies         - Displays all dependencies declared in root project.
dependencyInsight    - Displays the insight into a specific dependency in root project.
dependentComponents  - Displays the dependent components of components in root project.
help                 - Displays a help message.
init                 - Initializes a new Gradle build.
model                - Displays the configuration model of root project.
projects             - Displays the sub-projects of root project.
properties           - Displays the properties of root project.
tasks                - Displays the tasks runnable from root project.
wrapper              - Generates Gradle wrapper files."
        COMPREPLY=( $(compgen -W "$args" -- "$cur") )
    fi

    # Prevent colons from messing up completion
    [[ -n "$(type -t __ltrim_colon_completions)" ]] && __ltrim_colon_completions "$cur"
}

__gradle-options-arguments() {
    local cur prev
    _get_comp_words_by_ref -n : cur
    _get_comp_words_by_ref -n : -p prev

    case "$prev" in
        -b|--build-file|-c|--settings-file|-I|--init-script)
            COMPREPLY=( $(compgen -f -A file -o filenames -X '!*.gradle*' "$cur") )
            return 0
            ;;
        -g|--gradle-user-home|--include-build|--project-cache-dir|--project-dir)
            COMPREPLY=( $(compgen -d "$cur") )
            return 0
            ;;
        *)
            __gradle-tasks
            return 0
            ;;
    esac
}


__gradle-notify-tasks-cache-build() {
    # Notify user of cache rebuild
    echo -e " (Building completion cache. Please wait)\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\c"
    __gradle-generate-tasks-cache
    # Remove "please wait" message by writing a bunch of spaces then moving back to the left
    echo -e "                                         \b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\c"
}

__gradle-generate-tasks-cache() {
    __gradle-set-files-checksum

    # Use Gradle wrapper when it exists.
    local gradle_cmd="gradle"
    if [[ -x "$project_root_dir/gradlew" ]]; then
        gradle_cmd="$project_root_dir/gradlew"
    fi

    # Run gradle to retrieve possible tasks and cache.
    # Reuse Gradle Daemon if IDLE but don't start a new one.
    local gradle_tasks_output
    if [[ ! -z "$("$gradle_cmd" --status 2>/dev/null | grep IDLE)" ]]; then
        gradle_tasks_output="$("$gradle_cmd" -b "$gradle_build_file" --daemon --no-scan --console=plain -q tasks --all)"
    else
        gradle_tasks_output="$("$gradle_cmd" -b "$gradle_build_file" --no-daemon --no-scan --console=plain -q tasks --all)"
    fi
    local output_line
    local task_description
    local -a gradle_all_tasks=()
    local -a root_tasks=()
    local -a subproject_tasks=()
    for output_line in ${gradle_tasks_output}; do
        if [[ "$output_line" =~ ^([[:alpha:]][[:alnum:][:punct:]]*)([[:space:]]-[[:space:]]([[:print:]]*))? ]]; then
            task_name="${BASH_REMATCH[1]}"
            task_description="${BASH_REMATCH[3]}"
            gradle_all_tasks+=( "$task_name  - $task_description" )
            # Completion for subproject tasks with ':' prefix
            if [[ "$task_name" =~ ^([[:alnum:][:punct:]]+):([[:alnum:]]+) ]]; then
                gradle_all_tasks+=( ":$task_name  - $task_description" )
                subproject_tasks+=( "${BASH_REMATCH[2]}" )
            else
                root_tasks+=( "$task_name" )
            fi
        fi
    done

    # subproject tasks can be referenced implicitly from root project
    if [[ "$GRADLE_COMPLETION_UNQUALIFIED_TASKS" == "true" ]]; then
        local -a implicit_tasks=()
        implicit_tasks=( $(comm -23 <(printf "%s\n" "${subproject_tasks[@]}" | sort) <(printf "%s\n" "${root_tasks[@]}" | sort)) )
        for task in $(printf "%s\n" "${implicit_tasks[@]}"); do
            gradle_all_tasks+=( "$task" )
        done
    fi

    printf "%s\n" "${gradle_all_tasks[@]}" >| "$cache_dir/$gradle_files_checksum"
    echo "$gradle_files_checksum" >| "$cache_dir/$cache_name.md5"
}

__gradle-completion-init() {
    local cache_dir cache_name gradle_build_file gradle_files_checksum project_root_dir

    local OLDIFS="$IFS"
    local IFS=$'\n'

    __gradle-init-cache-dir
    __gradle-set-project-root-dir
    __gradle-set-build-file
    if [[ -f "$gradle_build_file" ]]; then
        __gradle-set-cache-name
        __gradle-generate-script-cache
        __gradle-set-files-checksum
        __gradle-notify-tasks-cache-build
    fi

    IFS="$OLDIFS"

    return 0
}

_gradle() {
    local cache_dir cache_name gradle_build_file gradle_files_checksum project_root_dir
    local cur prev
    _get_comp_words_by_ref -n : cur
    _get_comp_words_by_ref -n : -p prev

    # Set bash internal field separator to '\n'
    # This allows us to provide descriptions for options and tasks
    local OLDIFS="$IFS"
    local IFS=$'\n'

    if [[ ${cur} == --* ]]; then
        __gradle-long-options
    elif [[ ${cur} == -D* ]]; then
        __gradle-properties
    elif [[ ${cur} == -* ]]; then
        __gradle-short-options
    elif [[ ${prev} == -* ]]; then
        __gradle-options-arguments
    else
        __gradle-tasks
    fi

    IFS="$OLDIFS"

    # Remove description ("[:space:]" and after) if only one possibility
    if [[ ${#COMPREPLY[*]} -eq 1 ]]; then
        COMPREPLY=( ${COMPREPLY[0]%%  *} )
    fi

    return 0
}
complete -F _gradle gradle
complete -F _gradle gradle.bat
complete -F _gradle gradlew
complete -F _gradle gradlew.bat
complete -F _gradle ./gradlew
complete -F _gradle ./gradlew.bat

if hash gw 2>/dev/null || alias gw >/dev/null 2>&1; then
    complete -F _gradle gw
fi
