#compdef gradle gradlew

local curcontext="$curcontext" ret=1
local cache_policy
# Invalidate cache after 3 weeks by default
local cache_ttl_mins=${$(echo $GRADLE_CACHE_TTL_MINUTES):-30240}
local -A opt_args
local -a gradle_build_scripts
local -a previous_checksum
local -a gradle_tasks

# The cache key is md5 sum of all gradle scripts, so it's valid if it exists.
_gradle_caching_policy() {
    [[ ! $(find $1 -mmin -$cache_ttl_mins 2>/dev/null) ]]
}

zstyle -s ":completion:*:*:$service:*" cache-policy cache_policy || \
    zstyle ":completion:*:*:$service:*" cache-policy _gradle_caching_policy

_arguments -C \
    '(-)'{-\?,-h,--help}'[Shows a help message.]' \
    {-a,--no-rebuild}'[Do not rebuild project dependencies.]' \
    {-b,--build-file}'[Specifies the build file.]:build file:_files -g "*.gradle(-.)"' \
    {-C,--cache}'[Specifies how compiled build scripts should be cached.]:cache policy:(on rebuild)' \
    {-c,--settings-file}'[Specifies the settings file.]:settings file:_files -g "*.properties(-.)"' \
    '--configure-on-demand[Only relevant projects are configured in this build run.]' \
    '--console[Specifies which type of console output to generate. Values are 'plain', 'auto' (default) or 'rich'.]:console output type:' \
    '--continue[Continues task execution after a task failure.]' \
    '-Dorg.gradle.cache.reserved.mb=[Reserve Gradle Daemon memory for operations.]' \
    '-Dorg.gradle.daemon.debug=[Set true to debug Gradle Daemon.]' \
    '-Dorg.gradle.daemon.idletimeout=[Kill Gradle Daemon after # idle millis.]' \
    '-Dorg.gradle.debug=[Set true to debug Gradle Client.]' \
    '-Dorg.gradle.jvmargs=[Set JVM arguments.]' \
    '-Dorg.gradle.java.home=[Set JDK home dir.]' \
    '-Dorg.gradle.parallel=[Set true to enable parallel project builds.]' \
    '-Dorg.gradle.parallel.intra=[Set true to enable intra-project parallel builds.]' \
    '(-i --info -q --quiet)'{-d,--debug}'[Log in debug mode (includes normal stacktrace).]' \
    '(--no-daemon)--daemon[Uses the Gradle daemon to run the build. Starts the daemon if not running.]' \
    '--foreground[Starts the Gradle daemon in the foreground.]' \
    {-g,--gradle-user-home}'[Specifies the gradle user home directory.]:home directory:_directories' \
    '(-)--gui[Launches the Gradle GUI.]' \
    '--include-build[Includes the specified build in the composite.]:project directory:_directories' \
    {-I,--init-script}'[Specifies an initialization script.]:init script:_files -g "*.gradle(-.)"' \
    '(-d --debug -q --quiet)'{-i,--info}'[Set log level to info.]' \
    '--max-workers[Set the maximum number of concurrent workers that Gradle may use.]:number workers:-># processors' \
    {-m,--dry-run}'[Runs the builds with all task actions disabled.]' \
    '--no-color[Do not use color in the console output. (Removed in Gradle 3.0)]' \
    '(--daemon)--no-daemon[Do not use the Gradle daemon to run the build.]' \
    '--offline[The build should operate without accessing network resources.]' \
    \*{-P+,--project-prop}'[Set project property for the build script (e.g. -Pmyprop=myvalue).]:project property (prop=val):' \
    {-p,--project-dir}'[Specifies the start directory for Gradle.]:start directory:_directories' \
    '--parallel[Build projects in parallel. Gradle will attempt to determine the optimal number of executor threads to use.]' \
    '--profile[Profiles build execution time and generates a report in the <build_dir>/reports/profile directory.]' \
    '--project-cache-dir[Specifies the project-specific cache directory.]:cache directory:_directories' \
    '(-d --debug -i --info)'{-q,--quiet}'[Log errors only.]' \
    '--recompile-scripts[Force build script recompiling.]' \
    '--refresh[Refresh the state of resources of the type(s) specified.]:refresh policy:(dependencies)' \
    '--refresh-dependencies[Refresh the state of dependencies.]' \
    '--rerun-tasks[Ignore previously cached task results.]' \
    '(-S --full-stacktrace)'{-s,--stacktrace}'[Print out the stacktrace for all exceptions.]' \
    '(-s --stacktrace)'{-S,--full-stacktrace}'[Print out the full (very verbose) stacktrace for all exceptions.]' \
    '(-)--status[Shows status of running and recently stopped Gradle Daemons.]' \
    '(-)--stop[Stops all Gradle daemons.]' \
    '--system-prop[system property (prop=val)]' \
    {-t,--continuous}'[Enables continuous build. Gradle does not exit and will re-execute tasks when task file inputs change.]' \
    {-u,--no-search-upward}"[Don't search in parent folders for a settings.gradle file.]" \
    '(-)'{-v,--version}'[Print version info.]' \
    {-x,--exclude-task}'[Specify a task to be excluded from execution.]' \
    && ret=0

if [[ $words[CURRENT] != -* ]]; then
    # Use Gradle wrapper when it exists.
    local gradle_cmd='gradle'
    if [[ -x ./gradlew ]]; then
        gradle_cmd='./gradlew'
    fi

    # Look for default build script in the settings file (settings.gradle by default)
    # Otherwise, default is the file 'build.gradle' in the current directory.
    local gradle_settingsfile=${${(v)opt_args[(i)-c|--settings-file]}:-settings.gradle}
    local default_gradle_buildfile="build.gradle"
    if [[ -f $gradle_settingsfile ]]; then
        default_gradle_buildfile=${$(grep "^rootProject\.buildFileName" $gradle_settingsfile | \
            sed -n -e "s/rootProject\.buildFileName = [\'\"]\(.*\)[\'\"]/\1/p"):-build.gradle}
    fi

    # If a build file is specified after '-b' or '--build-file', use this file.
    local gradle_buildfile=${${(v)opt_args[(i)-b|--build-file]}:-$default_gradle_buildfile}
    if [[ -f $gradle_buildfile ]]; then
        # Cache name is constructed from the absolute path of the build file.
        local script_path_cache=${${gradle_buildfile:a}//[^[:alnum:]]/_}
        if ! _retrieve_cache $script_path_cache; then
            zle -R "Generating Gradle script cache"
            # Cache all Gradle scripts
            gradle_build_scripts=( $(find . -type f -name "*.gradle" -o -name "*.gradle.kts" 2>/dev/null | egrep -v "/(build|integTest|samples)/") )
            _store_cache $script_path_cache gradle_build_scripts
        fi

        local current_checksum
        # Cache MD5 sum of all Gradle scripts and modified timestamps
        if builtin command -v md5 > /dev/null; then
            current_checksum=( $(md5 -q -s "$(printf "%s\n" "$gradle_build_scripts[@]" | xargs ls -o 2>/dev/null)") )
        elif builtin command -v md5sum > /dev/null; then
            current_checksum=( $(printf "%s\n" "$gradle_build_scripts[@]" | xargs ls -o 2>/dev/null | md5sum) )
        else
            _message 'Cannot generate completions as neither md5 nor md5sum exist on \$PATH'
            return 1
        fi

        # This is confusing due to the way caching/retrieval works.
        # _retrieve_cache "$script_path_cache.md5" sets previous_checksum to an array wrapping the previous checksum
        # If the current and previous checksums match, try to load the tasks cache
        if ! _retrieve_cache "$script_path_cache.md5" || [[ $current_checksum != "${previous_checksum[1]}" ]] || ! _retrieve_cache "${previous_checksum[1]}"; then
            zle -R "Generating Gradle task cache using $gradle_buildfile"
            # Run gradle to retrieve possible tasks and cache.
            # Reuse Gradle Daemon if IDLE but don't start a new one.
            local gradle_tasks_output
            if [[ ! -z "$($gradle_cmd --status 2>/dev/null | grep IDLE)" ]]; then
                gradle_tasks_output="$($gradle_cmd --daemon --build-file $gradle_buildfile -q tasks --all)"
            else
                gradle_tasks_output="$($gradle_cmd --no-daemon --build-file $gradle_buildfile -q tasks --all)"
            fi
            local outputline
            local task_description
            local -a match
            for outputline in ${(f)"$(echo $gradle_tasks_output)"}; do
                if [[ $outputline =~ ([[:alnum:][:punct:]]*)[[:space:]]-[[:space:]]([[:print:]]*) ]]; then
                    task_description="${match[1]//:/\\:}:${match[2]// - /}"
                    gradle_tasks+=( "$task_description" )
                    # Completion for subproject tasks with ':' prefix
                    if [[ $outputline =~ [[:alnum:]]:[[:alnum:]]* ]]; then
                        gradle_tasks+=( "\\:$task_description" )
                    fi
                fi
            done

            _store_cache $current_checksum gradle_tasks
            previous_checksum=( $current_checksum )
            _store_cache "$script_path_cache.md5" previous_checksum
        fi

        _describe 'all tasks' gradle_tasks && ret=0
    else
        _describe 'built-in tasks' '(
            "buildEnvironment:Displays all buildscript dependencies declared in root project."
            "components:Displays the components produced by root project."
            "dependencies:Displays all dependencies declared in root project."
            "dependencyInsight:Displays the insight into a specific dependency in root project."
            "dependentComponents:Displays the dependent components of components in root project."
            "help:Displays a help message."
            "init:Initializes a new Gradle build."
            "model:Displays the configuration model of root project."
            "projects:Displays the sub-projects of root project."
            "properties:Displays the properties of root project."
            "tasks:Displays the tasks runnable from root project."
            "wrapper:Generates Gradle wrapper files."
            )' && ret=0
    fi
fi

return ret
