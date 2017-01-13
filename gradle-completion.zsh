#compdef gradle gradlew

local curcontext="$curcontext" ret=1 state state_descr line
local gradle_inspect=yes cache_policy
local -A opt_args
local -a gradle_tasks

# The cache key is md5 sum of all gradle scripts, so it's valid if it exists.
_gradle_caching_policy() {
    [[ ! -e $1 ]]
}

zstyle -s ":completion:*:*:$service:*" cache-policy cache_policy || \
    zstyle ":completion:*:*:$service:*" cache-policy _gradle_caching_policy

# The completion inspects the current build file to find tasks to complete. Setting
# this style to 'no' or 'false' turns off inspection. In that case only the built-in tasks
# are completed.
zstyle -T ":completion:*:*:$service:*" gradle-inspect || gradle_inspect=no

_arguments -C \
    '(-)'{-\?,-h,--help}'[Shows a help message.]' \
    {-a,--no-rebuild}'[Do not rebuild project dependencies.]' \
    {-b,--build-file}'[Specifies the build file.]:build file:_files -g "*.gradle(-.)"' \
    {-C,--cache}'[Specifies how compiled build scripts should be cached.]:cache policy:(on rebuild)' \
    {-c,--settings-file}'[Specifies the settings file.]:settings file:_files -g "*.properties(-.)"' \
    '--configure-on-demand[Only relevant projects are configured in this build run.]' \
    '--console[Specifies which type of console output to generate. Values are 'plain', 'auto' (default) or 'rich'.]:console output type:' \
    '--continue[Continues task execution after a task failure.]' \
    \*{-D+,--system-prop}'[Set system property of the JVM (e.g. -Dmyprop=myvalue).]:system property (prop=val):' \
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
    {-t,--continuous}'[Enables continuous build. Gradle does not exit and will re-execute tasks when task file inputs change.]' \
    {-u,--no-search-upward}"[Don't search in parent folders for a settings.gradle file.]" \
    '(-)'{-v,--version}'[Print version info.]' \
    {-x,--exclude-task}'[Specify a task to be excluded from execution.]' \
    && ret=0

if [[ $words[CURRENT] != -* ]]; then
    if [[ $gradle_inspect == yes ]]; then
        # Look for default build script in the settings file (settings.gradle by default)
        # Otherwise, default is the file 'build.gradle' in the current directory.
        local gradle_settingsfile=${${(v)opt_args[(i)-c|--settings-file]}:-settings.gradle}
        if [[ -f $gradle_settingsfile ]]; then
            local default_gradle_buildfile=${$(grep "^rootProject\.buildFileName" $gradle_settingsfile | \
                sed -n -e "s/rootProject\.buildFileName = [\'\"]\(.*\)[\'\"]/\1/p"):-build.gradle}
        fi

        # If a build file is specified after '-b' or '--build-file', use this file.
        local gradle_buildfile=${${(v)opt_args[(i)-b|--build-file]}:-$default_gradle_buildfile}
        if [[ -f $gradle_buildfile ]]; then
            # Cache name is constructed from the absolute path of the build file.
            local cache_name=${${gradle_buildfile:a}//[^[:alnum:]]/_}
            # Compute hash of hashes of all build/setting scripts for cache key
            if [[ -x `which md5 2>/dev/null` ]]; then #macOS
                local -a gradle_build_scripts
                gradle_build_scripts=( $(find . -type f -name "*.gradle" -o -name "*.gradle.kts" 2>/dev/null) )
                cache_name="$(md5 -q -s "$(md5 -q ${gradle_build_scripts})")_$cache_name"
            else
                cache_name="$(find . -type f -name "*.gradle" -o -name "*.gradle.kts" | xargs md5sum | md5sum)_$cache_name"
            fi

            if ! _retrieve_cache $cache_name; then
                zle -R "Generating cache from $gradle_buildfile"
                # Use Gradle wrapper when it exists.
                local gradle_cmd='gradle'
                if [[ -x ./gradlew ]]; then
                    gradle_cmd='./gradlew'
                fi
                # Run gradle and retrieve possible tasks.
                local outputline
                local -a match mbegin mend
                for outputline in ${(f)"$($gradle_cmd --build-file $gradle_buildfile -q tasks --all)"}; do
                    if [[ $outputline == (#b)([[:lower:]][[:alnum:][:punct:]]##)(*) ]]; then
                        gradle_tasks+=( "${match[1]//:/\\:}:${match[2]// - /}" )
                    fi
                done
                _store_cache $cache_name gradle_tasks
            fi

            _describe 'all tasks' gradle_tasks && ret=0
        fi
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
