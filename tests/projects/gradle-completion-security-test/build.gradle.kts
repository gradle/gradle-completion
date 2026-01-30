tasks.register("boom") {
    description = "Something `echo PWNED`"
}
tasks.register("boom1") {
    description = "Something braces $(echo PWNED)"
}
tasks.register("boom2") {
    description = "Something curly \${echo PWNED}"
}

// Task that prints the curly brace attack vector
val printCurlyTask = tasks.register("printCurly") {
    description = "Prints the curly brace expansion string"
    doLast {
        println("madeUpTask - some non existing task \${echo PWNED}")
    }
}

// Make the built-in 'tasks' task depend on printCurly
tasks.named("tasks") {
    dependsOn(printCurlyTask)
}

// Additional attack vectors that could cause issues

// Variable expansion - could leak environment variables
tasks.register("boom3") {
    description = "User is \$USER home is \$HOME"
}

// Command with output redirection - could write to files
tasks.register("boom4") {
    description = "Write output with \$(echo data > /tmp/pwned)"
}

// Command chaining - execute multiple commands
tasks.register("boom5") {
    description = "Chain commands \$(echo step1 && echo step2)"
}

// Complex command - loops and conditionals
tasks.register("boom6") {
    description = "Complex: \$(for i in {1..5}; do echo \$i; done)"
}

// Data exfiltration - read sensitive files
tasks.register("boom7") {
    description = "Read SSH key: \$(cat ~/.ssh/id_rsa)"
}

// Arithmetic expansion - could do calculations
tasks.register("boom8") {
    description = "Math: Result is \$((1337 + 2023))"
}

// Nested command substitution
tasks.register("boom9") {
    description = "Nested: \$(echo \$(whoami))"
}

tasks.register("boom10") {
    description = "Task with \" escaped quote"
}

tasks.register("boom11") {
    description = "Task with both \"double\" and 'single' quotes"
}

tasks.register("boom_ with spaces") {
    description = "Task with both \"double\" and 'single' quotes"
    doLast{
        println("This task has spaces in its name")
    }
}

tasks.register(" boom starting with spaces") {
    description = "Task with both \"double\" and 'single' quotes"
    doLast{
        println("This task has spaces in its name")
    }
}

tasks.register("boom with - - dashes") {
    description = "Task with both \"double\" and 'single' quotes"
    doLast{
        println("This task has spaces in its name")
    }
}

tasks.register("someTask") {
//    description = "Task with both \"double\" and 'single' quotes"
    doLast{
        println("This task has spaces in its name")
    }
}


tasks.register("s\$omeTask") {
//    description = "Task with both \"double\" and 'single' quotes"
    doLast{
        println("This task has spaces in its name")
    }
}
tasks.register("plop \${ echo 'dkdkdk' }") {
//    description = "Task with both \"double\" and 'single' quotes"
    doLast{
        println("This task has spaces in its name")
    }
}

// Additional command substitution patterns from bash manual

// Parameter expansion with command substitution
tasks.register("boom12") {
    description = "Param expansion: \${var:-\$(echo pwned)}"
}

// Conditional execution in command substitution
tasks.register("boom13") {
    description = "Conditional: \$(test -f /etc/passwd && cat /etc/passwd)"
}

// Pipe within command substitution
tasks.register("boom14") {
    description = "Piped: \$(echo test | sed 's/test/pwned/')"
}

// Array subscript with command substitution
tasks.register("boom15") {
    description = "Array subscript: \${array[\$(echo 0)]}"
}

// Subshell with multiple commands
tasks.register("boom16") {
    description = "Subshell: \$( (echo a; echo b) )"
}

tasks.register("boom16 subshell task name \$( (echo a; echo b) )") {
    description = "Subshell: \$( (echo a; echo b) )"
}

// ========================================
// Attack vectors in TASK NAMES
// ========================================

// Backtick command substitution in task name
tasks.register("name_boom `echo PWNED`") {
    description = "Backtick substitution in name"
}

// Braces command substitution in task name
tasks.register("name_boom1 \$(echo PWNED)") {
    description = "Braces substitution in name"
}

// Curly brace variable expansion in task name
tasks.register("name_boom2 \${echo PWNED}") {
    description = "Curly brace expansion in name"
}

// Variable expansion in task name
tasks.register("name_boom3 \$USER \$HOME") {
    description = "Variable expansion in name"
}

// Command chaining in task name
tasks.register("name_boom5 \$(echo step1 && echo step2)") {
    description = "Command chaining in name"
}

// Complex command - loops in task name
tasks.register("name_boom6 \$(for i in {1..5}; do echo \$i; done)") {
    description = "Loop in name"
}

// Arithmetic expansion in task name
tasks.register("name_boom8 \$((1337 + 2023))") {
    description = "Arithmetic expansion in name"
}

// Nested command substitution in task name
tasks.register("name_boom9 \$(echo \$(whoami))") {
    description = "Nested substitution in name"
}

// Both quote types in task name
tasks.register("name_boom11 'single' quotes") {
    description = "Both quote types in name"
}

// Array subscript with command substitution in task name
tasks.register("name_boom15 \${array[\$(echo 0)]}") {
    description = "Array subscript in name"
}

// Subshell with multiple commands in task name
tasks.register("name_boom16 \$( (echo a; echo b) )") {
    description = "Subshell in name"
}

// Newline injection in task name
tasks.register("name_boom17\necho PWNED") {
    description = "Newline injection in name"
}

// Semicolon command separator in task name
tasks.register("name_boom18; echo PWNED") {
    description = "Semicolon separator in name"
}

// Background process in task name
tasks.register("name_boom20 & echo PWNED") {
    description = "Background process in name"
}

// Brace expansion in task name
tasks.register("name_boom27 {a,b,c}") {
    description = "Brace expansion in name"
}

// History expansion in task name
tasks.register("name_boom29 !!") {
    description = "History expansion in name"
}

// Escape sequences in task name
tasks.register("name_boom30 \t\n\r") {
    description = "Escape sequences in name"
}

// Null byte in task name (if allowed)
// this breaks cache generation
//tasks.register("name_boom31 \u0000 null") {
//    description = "Null byte in name"
//}

// Unicode/special chars that might cause issues
// this messes up output, let's not deal with it for now, it's just rendering.
tasks.register("name_boom32 \u202E RLO override") {
    description = "Right-to-left override in name"
}

// ========================================
// Additional attack vectors - Process Substitution
// ========================================

// Process substitution (different from command substitution)
tasks.register("boom_proc1") {
    description = "Process sub input: <(echo pwned)"
}

tasks.register("boom_proc2") {
    description = "Process sub output: >(cat > /tmp/pwned)"
}

// ========================================
// Tilde Expansion
// ========================================

tasks.register("boom_tilde1") {
    description = "Home dir tilde: ~/secret/file"
}

tasks.register("boom_tilde2") {
    description = "User tilde: ~root/.bashrc"
}

// ========================================
// Advanced Parameter Expansion
// ========================================

// Indirect expansion
tasks.register("boom_indirect") {
    description = "Indirect var: \${!PATH}"
}

// Substring expansion
tasks.register("boom_substring") {
    description = "Substring: \${PATH:0:10}"
}

// Case modification (bash 4+)
tasks.register("boom_case_upper") {
    description = "Uppercase: \${var^^}"
}

tasks.register("boom_case_lower") {
    description = "Lowercase: \${var,,}"
}

// Pattern substitution
tasks.register("boom_pattern_sub") {
    description = "Pattern sub: \${var/foo/bar}"
}

// Pattern deletion
tasks.register("boom_pattern_del") {
    description = "Pattern delete: \${var#prefix} and \${var%suffix}"
}

// ========================================
// Here Strings / Here Documents
// ========================================

tasks.register("boom_herestring") {
    description = "Here string: <<< 'pwned data'"
}

tasks.register("boom_heredoc") {
    description = "Heredoc marker: <<EOF"
}

// ========================================
// Standalone Operators in Task Names
// ========================================
// Note: Gradle forbids these characters in task names: /, \, :, <, >, ", ?, *, |
// So we can't create tasks with pipes or redirects in names.
// However, we CAN test that these operators in DESCRIPTIONS don't execute.

tasks.register("desc_with_pipe") {
    description = "Pipe in desc: echo secret | cat"
}

tasks.register("desc_with_redirect") {
    description = "Redirect in desc: echo pwned > /tmp/file"
}

tasks.register("desc_with_input_redirect") {
    description = "Input redirect: cat < /etc/passwd"
}

tasks.register("desc_with_append") {
    description = "Append redirect: echo data >> /tmp/file"
}

tasks.register("desc_with_stderr") {
    description = "Stderr redirect: cmd 2>&1"
}

// ========================================
// Control Characters
// ========================================

tasks.register("boom_bell") {
    description = "Bell character: \u0007"
}

tasks.register("boom_ansi_color") {
    description = "ANSI color: \u001b[31mRED\u001b[0m"
}

tasks.register("boom_ansi_clear") {
    description = "ANSI clear screen: \u001b[2J"
}

tasks.register("boom_carriage") {
    description = "Carriage return: text\roverwrite"
}

// ========================================
// Combination Attacks
// ========================================

// Multiple expansion types combined
tasks.register("boom_combo1") {
    // Use unicode \u0060 for backtick since Kotlin doesn't support \` escape
    description = "Combo: \$(echo \$USER) and \u0060whoami\u0060 and \${HOME}"
}

// Nested with process substitution
tasks.register("boom_combo2") {
    description = "Nested proc: \$(cat <(echo secret))"
}

// Quote escaping attempts
tasks.register("boom_quote_escape") {
    // In Kotlin, single quotes don't need escaping in double-quoted strings
    description = "Quote escape: \\\" and ' and \\\\"
}
