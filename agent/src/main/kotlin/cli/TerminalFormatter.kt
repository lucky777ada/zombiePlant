package org.besomontro.cli

object TerminalFormatter {
    private const val ANSI_RESET = "\u001B[0m"
    private const val ANSI_BLACK = "\u001B[30m"
    private const val ANSI_RED = "\u001B[31m"
    private const val ANSI_GREEN = "\u001B[32m"
    private const val ANSI_YELLOW = "\u001B[33m"
    private const val ANSI_BLUE = "\u001B[34m"
    private const val ANSI_PURPLE = "\u001B[35m"
    private const val ANSI_CYAN = "\u001B[36m"
    private const val ANSI_WHITE = "\u001B[37m"
    private const val ANSI_BOLD = "\u001B[1m"

    fun String.bold(): String = "$ANSI_BOLD$this$ANSI_RESET"
    fun String.green(): String = "$ANSI_GREEN$this$ANSI_RESET"
    fun String.red(): String = "$ANSI_RED$this$ANSI_RESET"
    fun String.yellow(): String = "$ANSI_YELLOW$this$ANSI_RESET"
    fun String.cyan(): String = "$ANSI_CYAN$this$ANSI_RESET"

    fun printStartupLogo() {
        // ASCII Art for Zombie Plant
        val logo = """
      
      $ANSI_GREEN
                 .   .
              .   :   .
           .   .   .   .
        .    .   :   .    .
         .   .   .   .   .
            .   :   .
              . | .
      $ANSI_PURPLE         _ _ $ANSI_GREEN\/$ANSI_PURPLE _ _$ANSI_GREEN
      $ANSI_PURPLE       _|     |_
      $ANSI_PURPLE      |  $ANSI_RED(o)(o)$ANSI_PURPLE  |   $ANSI_BOLD NEOPLANT AGENT $ANSI_RESET
      $ANSI_PURPLE      |   $ANSI_RED  ^  $ANSI_PURPLE   |   $ANSI_CYAN v2.0 $ANSI_RESET
      $ANSI_PURPLE      |  $ANSI_RED \___/ $ANSI_PURPLE  |
      $ANSI_PURPLE       \_____/
      
      $ANSI_RESET
      """.trimIndent()
        println(logo)
        println("   System Online... Monitoring Biosystems...".cyan())
        println()
    }

    fun formatSummary(rawSummary: String): String {
        // Simple heuristic Markdown parsing for terminal
        // 1. Headers (**TEXT**) -> BOLD YELLOW
        // 2. Bold (**text**) -> WHITE BOLD
        // 3. Status Report -> Boxed

        var formatted = rawSummary
            .replace(Regex("\\*\\*STATUS REPORT\\*\\*"), "\n${"=".repeat(40)}\n${"STATUS REPORT".yellow().bold()}\n${"=".repeat(40)}")
            .replace(Regex("\\*\\*INTERNAL THOUGHTS\\*\\*"), "\n${"-".repeat(20)}\n${"INTERNAL THOUGHTS".cyan().bold()}\n${"-".repeat(20)}")
            .replace(Regex("\\*\\*(.*?)\\*\\*")) { it.groupValues[1].bold() } // Bold other **text**

        // Add a nice footer border
        formatted += "\n" + "=".repeat(40).cyan()

        return formatted
    }
}
