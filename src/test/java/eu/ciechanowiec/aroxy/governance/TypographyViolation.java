package eu.ciechanowiec.aroxy.governance;

/**
 * A single occurrence of a banned typographic code point, located by its one-based line and column.
 */
record TypographyViolation(int lineNumber, int column, int codePoint) {
}
