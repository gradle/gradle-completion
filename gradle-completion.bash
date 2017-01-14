_gradle()
{
    local cur=${COMP_WORDS[COMP_CWORD]}
    COMPREPLY=()
    local gradle_cmd='gradle'
    if [[ -x ./gradlew ]]; then
        gradle_cmd='./gradlew'
    fi

    local cache_dir="$HOME/.gradle/completion"
    mkdir -p $cache_dir

    # Set bash internal field separator to '\n'
    local OLDIFS="$IFS"
    local IFS=$'\n'

    if [[ ${cur} == -* ]]; then
        local args="-a                      - Do not rebuild project dependencies
-b                      - Specifies the build file
-c                      - Specifies the settings file
-d                      - Log at the debug level
-g                      - Specifies the Gradle user home directory
-h                      - Shows a help message
-i                      - Set log level to INFO
-m                      - Runs the build with all task actions disabled
-p                      - Specifies the start directory for Gradle
-q                      - Log errors only
-s                      - Print out the stacktrace also for user exceptions
-t                      - Continuous mode. Automatically re-run build after changes
-u                      - Do not search in parent directories for a settings.gradle
-v                      - Prints Gradle version info
-x                      - Specify a task to be excluded
-D                      - Set a system property
-I                      - Specifies an initialization script
-P                      - Sets a project property of the root project
-S                      - Print out the full (very verbose) stacktrace
--build-file            - Specifies the build file
--configure-on-demand   - Only relevant projects are configured
--console               - Type of console output to generate (plain auto rich)
--continue              - Continues task execution after a task failure
--continuous            - Continuous mode. Automatically re-run build after changes
--daemon                - Use the Gradle Daemon
--debug                 - Log at the debug level
--dry-run               - Runs the build with all task actions disabled
--exclude-task          - Specify a task to be excluded
--full-stacktrace       - Print out the full (very verbose) stacktrace
--gradle-user-home      - Specifies the Gradle user home directory
--gui                   - Launches the Gradle GUI app (Deprecated)
--help                  - Shows a help message
--include-build         - Run the build as a composite, including the specified build
--info                  - Set log level to INFO
--init-script           - Specifies an initialization script
--max-workers           - Set the maximum number of workers that Gradle may use
--no-daemon             - Do not use the Gradle Daemon
--no-rebuild            - Do not rebuild project dependencies
--no-search-upwards     - Do not search in parent directories for a settings.gradle
--offline               - Build without accessing network resources
--parallel              - Build projects in parallel
--profile               - Profile build time and create report
--project-cache-dir     - Specifies the project-specific cache directory
--project-dir           - Specifies the start directory for Gradle
--project-prop          - Sets a project property of the root project
--quiet                 - Log errors only
--recompile-scripts     - Forces scripts to be recompiled, bypassing caching
--refresh-dependencies  - Refresh the state of dependencies
--rerun-task            - Specifies that any task optimization is ignored
--settings-file         - Specifies the settings file
--stacktrace            - Print out the stacktrace also for user exceptions
--status                - Print Gradle Daemon status
--stop                  - Stop all Gradle Daemons
--system-prop           - Set a system property
--version               - Prints Gradle version info"
        COMPREPLY=( $(compgen -W "$args" -- "$cur") )
    else
        # Look for default build script in the settings file (settings.gradle by default)
        # Otherwise, default is the file 'build.gradle' in the current directory.
        local gradle_buildfile=build.gradle
        if [[ -f settings.gradle ]]; then
            local build_file_name=$(grep "^rootProject\.buildFileName" settings.gradle | sed -n -e "s/rootProject\.buildFileName = [\'\"]\(.*\)[\'\"]/\1/p")
            gradle_buildfile=${build_file_name:-build.gradle}
        fi

        local gradle_files_checksum
        if [[ -f $gradle_buildfile ]]; then
            if [[ -x `which md5 2>/dev/null` ]]; then
                # Hash all build/settings scripts on macOS
                local gradle_build_scripts=$(find . -type f -name "*.gradle" -o -name "*.gradle.kts" 2>/dev/null)
                gradle_files_checksum=$(md5 -q -s "$(md5 -q $gradle_build_scripts)")
            else
                # Hash all build/settings scripts on linux
                gradle_files_checksum=($(find . -type f -name "*.gradle" -o -name "*.gradle.kts" | xargs md5sum | md5sum))
            fi
        else
            gradle_files_checksum='NO_BUILD_SCRIPTS_FOUND'
        fi

        # Check task name cache and regenerate if necessary
        local -a gradle_all_tasks=()
        if [[ -f $cache_dir/$gradle_files_checksum ]]; then
            gradle_all_tasks=( $(cat $cache_dir/$gradle_files_checksum) )
        else
            # Tasks not cached. Run gradle to retrieve possible tasks.
            local outputline
            for outputline in $($gradle_cmd -q tasks --all); do
                if [[ $outputline =~ ([[:alnum:][:punct:]]*)[[:space:]]-[[:space:]]([[:print:]]*) ]]; then
                    gradle_all_tasks+=( "${BASH_REMATCH[1]}  - ${BASH_REMATCH[2]}" )
                fi
            done

            # Cache results
            printf "%s\n" "${gradle_all_tasks[@]}" > $cache_dir/$gradle_files_checksum
        fi
        COMPREPLY=( $(compgen -W "${gradle_all_tasks[*]}" -- "$cur") )
    fi

    IFS="$OLDIFS"

    # Remove description ("[:space:]" and after) if only one possibility
    if [[ ${#COMPREPLY[*]} -eq 1 ]]; then
        COMPREPLY=( ${COMPREPLY[0]%%  *} )
    fi
    return 0
}
complete -o nosort -F _gradle gradle
complete -o nosort -F _gradle gradlew
complete -o nosort -F _gradle ./gradlew
