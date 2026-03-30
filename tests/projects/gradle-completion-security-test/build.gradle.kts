// ========================================
// Description exploit tests (desc_ prefix)
// Tasks where the DESCRIPTION contains shell metacharacters.
// The completion script must preserve these as literals, never evaluate them.
// ========================================

// Command substitution variants
tasks.register("desc_backtick_cmd") {
    description = "Something `echo PWNED`"
}
tasks.register("desc_dollar_paren_cmd") {
    description = "Something braces $(echo PWNED)"
}
tasks.register("desc_curly_brace") {
    description = "Something curly \${echo PWNED}"
}

// Variable expansion - could leak environment variables
tasks.register("desc_var_expansion") {
    description = "User is \$USER home is \$HOME"
}

// Command with output redirection - could write to files
tasks.register("desc_cmd_with_redirect") {
    description = "Write output with \$(echo data > /tmp/pwned)"
}

// Command chaining - execute multiple commands
tasks.register("desc_cmd_chaining") {
    description = "Chain commands \$(echo step1 && echo step2)"
}

// Complex command - loops and conditionals
tasks.register("desc_cmd_loop") {
    description = "Complex: \$(for i in {1..5}; do echo \$i; done)"
}

// Data exfiltration - read sensitive files
tasks.register("desc_data_exfiltration") {
    description = "Read SSH key: \$(cat ~/.ssh/id_rsa)"
}

// Arithmetic expansion - could do calculations
tasks.register("desc_arithmetic") {
    description = "Math: Result is \$((1337 + 2023))"
}

// Nested command substitution
tasks.register("desc_nested_cmd") {
    description = "Nested: \$(echo \$(whoami))"
}

// Quote handling
tasks.register("desc_escaped_quote") {
    description = "Task with \" escaped quote"
}
tasks.register("desc_mixed_quotes") {
    description = "Task with both \"double\" and 'single' quotes"
}

// Parameter expansion with command substitution
tasks.register("desc_param_default") {
    description = "Param expansion: \${var:-\$(echo pwned)}"
}

// Conditional execution in command substitution
tasks.register("desc_conditional_exec") {
    description = "Conditional: \$(test -f /etc/passwd && cat /etc/passwd)"
}

// Pipe within command substitution
tasks.register("desc_piped_cmd") {
    description = "Piped: \$(echo test | sed 's/test/pwned/')"
}

// Array subscript with command substitution
tasks.register("desc_array_subscript") {
    description = "Array subscript: \${array[\$(echo 0)]}"
}

// Subshell with multiple commands
tasks.register("desc_subshell") {
    description = "Subshell: \$( (echo a; echo b) )"
}

// ========================================
// Process Substitution in descriptions
// ========================================

tasks.register("desc_proc_sub_input") {
    description = "Process sub input: <(echo pwned)"
}
tasks.register("desc_proc_sub_output") {
    description = "Process sub output: >(cat > /tmp/pwned)"
}

// ========================================
// Tilde Expansion in descriptions
// ========================================

tasks.register("desc_tilde_home") {
    description = "Home dir tilde: ~/secret/file"
}
tasks.register("desc_tilde_user") {
    description = "User tilde: ~root/.bashrc"
}

// ========================================
// Advanced Parameter Expansion in descriptions
// ========================================

tasks.register("desc_param_indirect") {
    description = "Indirect var: \${!PATH}"
}
tasks.register("desc_param_substring") {
    description = "Substring: \${PATH:0:10}"
}
tasks.register("desc_case_upper") {
    description = "Uppercase: \${var^^}"
}
tasks.register("desc_case_lower") {
    description = "Lowercase: \${var,,}"
}
tasks.register("desc_pattern_sub") {
    description = "Pattern sub: \${var/foo/bar}"
}
tasks.register("desc_pattern_del") {
    description = "Pattern delete: \${var#prefix} and \${var%suffix}"
}

// ========================================
// Here Strings / Here Documents in descriptions
// ========================================

tasks.register("desc_here_string") {
    description = "Here string: <<< 'pwned data'"
}
tasks.register("desc_here_doc") {
    description = "Heredoc marker: <<EOF"
}

// ========================================
// Shell Operators in descriptions
// ========================================
// Note: Gradle forbids these characters in task names: /, \, :, <, >, ", ?, *, |
// So we can't create tasks with pipes or redirects in names.
// However, we CAN test that these operators in DESCRIPTIONS don't execute.

tasks.register("desc_pipe") {
    description = "Pipe in desc: echo secret | cat"
}
tasks.register("desc_redirect") {
    description = "Redirect in desc: echo pwned > /tmp/file"
}
tasks.register("desc_input_redirect") {
    description = "Input redirect: cat < /etc/passwd"
}
tasks.register("desc_append_redirect") {
    description = "Append redirect: echo data >> /tmp/file"
}
tasks.register("desc_stderr_redirect") {
    description = "Stderr redirect: cmd 2>&1"
}

// ========================================
// Control Characters in descriptions
// ========================================

tasks.register("desc_ctrl_bell") {
    description = "Bell character: \u0007"
}
tasks.register("desc_ansi_color") {
    description = "ANSI color: \u001b[31mRED\u001b[0m"
}
tasks.register("desc_ansi_clear") {
    description = "ANSI clear screen: \u001b[2J"
}
tasks.register("desc_carriage_return") {
    description = "Carriage return: text\roverwrite"
}

// ========================================
// Combination Attacks in descriptions
// ========================================

tasks.register("desc_combo_multi") {
    // Use unicode \u0060 for backtick since Kotlin doesn't support \` escape
    description = "Combo: \$(echo \$USER) and \u0060whoami\u0060 and \${HOME}"
}
tasks.register("desc_combo_nested_proc") {
    description = "Nested proc: \$(cat <(echo secret))"
}
tasks.register("desc_quote_escape") {
    // In Kotlin, single quotes don't need escaping in double-quoted strings
    description = "Quote escape: \\\" and ' and \\\\"
}

// ========================================
// Task name format tests (fmt_ prefix)
// Tasks with unusual but valid name structures.
// ========================================

tasks.register("fmt_task_with_spaces") {
    description = "Task name contains spaces"
    doLast {
        println("This task has spaces in its name")
    }
}

tasks.register(" fmt_leading_space") {
    description = "Task name starts with a leading space"
    doLast {
        println("This task starts with a space")
    }
}

tasks.register("fmt_task_with_dashes") {
    description = "Task name contains dashes and spaces"
    doLast {
        println("This task has dashes in its name")
    }
}

tasks.register("fmt_no_description") {
//  no description intentionally - tests tasks without descriptions
    doLast {
        println("This task has no description")
    }
}

// ========================================
// Task NAME exploit tests (name_ prefix)
// Tasks where the NAME itself contains shell metacharacters.
// ========================================

// Helper task that prints a fake task line with curly brace expansion
val helperPrintCurly = tasks.register("helperPrintCurly") {
    description = "Prints the curly brace expansion string"
    doLast {
        println("madeUpTask - some non existing task \${echo PWNED}")
    }
}

// Make the built-in 'tasks' task depend on helperPrintCurly
tasks.named("tasks") {
    dependsOn(helperPrintCurly)
}

// Backtick command substitution in task name
tasks.register("name_backtick `echo PWNED`") {
    description = "Backtick substitution in name"
}

// Braces command substitution in task name
tasks.register("name_dollar_cmd \$(echo PWNED)") {
    description = "Braces substitution in name"
}

// Curly brace variable expansion in task name
tasks.register("name_curly_brace \${echo PWNED}") {
    description = "Curly brace expansion in name"
}

// Variable expansion in task name
tasks.register("name_var_expansion \$USER \$HOME") {
    description = "Variable expansion in name"
}

// Command chaining in task name
tasks.register("name_cmd_chaining \$(echo step1 && echo step2)") {
    description = "Command chaining in name"
}

// Complex command - loops in task name
tasks.register("name_cmd_loop \$(for i in {1..5}; do echo \$i; done)") {
    description = "Loop in name"
}

// Arithmetic expansion in task name
tasks.register("name_arithmetic \$((1337 + 2023))") {
    description = "Arithmetic expansion in name"
}

// Nested command substitution in task name
tasks.register("name_nested_cmd \$(echo \$(whoami))") {
    description = "Nested substitution in name"
}

// Both quote types in task name
tasks.register("name_single_quotes 'single' quotes") {
    description = "Both quote types in name"
}

// Array subscript with command substitution in task name
tasks.register("name_array_subscript \${array[\$(echo 0)]}") {
    description = "Array subscript in name"
}

// Subshell with multiple commands in task name
tasks.register("name_subshell \$( (echo a; echo b) )") {
    description = "Subshell in name"
}

// Subshell in both name and description
tasks.register("name_subshell_in_desc \$( (echo a; echo b) )") {
    description = "Subshell: \$( (echo a; echo b) )"
}

// Newline injection in task name
tasks.register("name_newline\necho PWNED") {
    description = "Newline injection in name"
}

// Semicolon command separator in task name
tasks.register("name_semicolon; echo PWNED") {
    description = "Semicolon separator in name"
}

// Background process in task name
tasks.register("name_background & echo PWNED") {
    description = "Background process in name"
}

// Brace expansion in task name
tasks.register("name_brace_expansion {a,b,c}") {
    description = "Brace expansion in name"
}

// History expansion in task name
tasks.register("name_history_expansion !!") {
    description = "History expansion in name"
}

// Escape sequences in task name
tasks.register("name_escape_sequences \t\n\r") {
    description = "Escape sequences in name"
}

// Null byte in task name (if allowed)
// this breaks cache generation
//tasks.register("name_null_byte \u0000 null") {
//    description = "Null byte in name"
//}

// Unicode/special chars that might cause issues
// this messes up output, let's not deal with it for now, it's just rendering.
tasks.register("name_rtl_override \u202E RLO override") {
    description = "Right-to-left override in name"
}

// Dollar sign embedded in otherwise normal name (no description)
tasks.register("name_dollar\$ign") {
//  no description intentionally - tests task name with dollar sign
    doLast {
        println("This task has a dollar sign in its name")
    }
}

// Brace expansion embedded in otherwise normal name (no description)
tasks.register("name_brace_in_name \${ echo 'dkdkdk' }") {
//  no description intentionally - tests task name with brace expansion
    doLast {
        println("This task has brace expansion in its name")
    }
}
