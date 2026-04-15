// Subproject tasks for testing colon-prefixed completion (e.g. :sub:subTask)

tasks.register("subTask") {
    description = "A simple subproject task"
}

tasks.register("subTask_with_backtick") {
    description = "Subproject injection: `echo PWNED`"
}
