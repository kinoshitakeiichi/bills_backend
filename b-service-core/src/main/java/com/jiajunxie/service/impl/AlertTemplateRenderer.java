package com.jiajunxie.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AlertTemplateRenderer {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
  private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]*");
  private static final Pattern TOP_LEVEL_ROUND =
      Pattern.compile("^round\\(.*,(\\s*\\d+)\\)$");

  public String render(String template, Map<String, String> textVariables, Map<String, BigDecimal> numericVariables) {
    if (template == null) return "";

    // UI/API may submit either real line breaks or the explicit two-character sequence "\n".
    String normalized = template.replace("\\n", "\n");
    Matcher matcher = PLACEHOLDER.matcher(normalized);
    StringBuffer result = new StringBuffer();

    while (matcher.find()) {
      String token = matcher.group(1).trim();
      String replacement = resolve(token, textVariables, numericVariables);
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private String resolve(
      String token,
      Map<String, String> textVariables,
      Map<String, BigDecimal> numericVariables) {
    if (SIMPLE_NAME.matcher(token).matches()) {
      if (textVariables.containsKey(token)) {
        return safe(textVariables.get(token));
      }
      if (numericVariables.containsKey(token)) {
        BigDecimal value = numericVariables.get(token);
        return value == null ? "-" : display(value);
      }
      return "{" + token + "}";
    }

    try {
      BigDecimal value = new Parser(token, numericVariables).parse();
      Matcher roundMatcher = TOP_LEVEL_ROUND.matcher(token);
      if (roundMatcher.matches()) {
        int scale = Integer.parseInt(roundMatcher.group(1).trim());
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
      }
      return display(value);
    } catch (RuntimeException ex) {
      // Template errors never break the alert scheduler.
      return "-";
    }
  }

  private String display(BigDecimal value) {
    if (value == null) return "-";
    return value.stripTrailingZeros().toPlainString();
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  /**
   * Deliberately tiny arithmetic grammar:
   *
   * expression := term (('+' | '-') term)*
   * term       := factor (('*' | '/') factor)*
   * factor     := ('+' | '-') factor | number | variable | '(' expression ')' | function
   * function   := abs(expression) | round(expression, integer)
   */
  private static final class Parser {
    private final String input;
    private final Map<String, BigDecimal> variables;
    private int pos;

    private Parser(String input, Map<String, BigDecimal> variables) {
      this.input = input;
      this.variables = variables;
    }

    BigDecimal parse() {
      BigDecimal value = expression();
      skipSpace();
      if (pos != input.length()) throw new IllegalArgumentException("Unexpected token");
      return value;
    }

    private BigDecimal expression() {
      BigDecimal value = term();
      while (true) {
        skipSpace();
        if (consume('+')) value = value.add(term());
        else if (consume('-')) value = value.subtract(term());
        else return value;
      }
    }

    private BigDecimal term() {
      BigDecimal value = factor();
      while (true) {
        skipSpace();
        if (consume('*')) value = value.multiply(factor());
        else if (consume('/')) {
          BigDecimal divisor = factor();
          if (divisor.signum() == 0) throw new ArithmeticException("Division by zero");
          value = value.divide(divisor, 12, RoundingMode.HALF_UP);
        } else return value;
      }
    }

    private BigDecimal factor() {
      skipSpace();
      if (consume('+')) return factor();
      if (consume('-')) return factor().negate();

      if (consume('(')) {
        BigDecimal value = expression();
        expect(')');
        return value;
      }

      if (peekLetter()) {
        String name = identifier();
        skipSpace();
        if (consume('(')) return function(name);

        if (!variables.containsKey(name) || variables.get(name) == null) {
          throw new IllegalArgumentException("Variable unavailable: " + name);
        }
        return variables.get(name);
      }

      return number();
    }

    private BigDecimal function(String name) {
      if ("abs".equals(name)) {
        BigDecimal value = expression();
        expect(')');
        return value.abs();
      }
      if ("round".equals(name)) {
        BigDecimal value = expression();
        expect(',');
        int scale = integer();
        if (scale < 0 || scale > 12) throw new IllegalArgumentException("Invalid scale");
        expect(')');
        return value.setScale(scale, RoundingMode.HALF_UP);
      }
      throw new IllegalArgumentException("Function not allowed: " + name);
    }

    private BigDecimal number() {
      skipSpace();
      int start = pos;
      boolean dot = false;
      while (pos < input.length()) {
        char c = input.charAt(pos);
        if (Character.isDigit(c)) {
          pos++;
        } else if (c == '.' && !dot) {
          dot = true;
          pos++;
        } else {
          break;
        }
      }
      if (start == pos) throw new IllegalArgumentException("Number expected");
      return new BigDecimal(input.substring(start, pos));
    }

    private int integer() {
      skipSpace();
      int start = pos;
      while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
      if (start == pos) throw new IllegalArgumentException("Integer expected");
      return Integer.parseInt(input.substring(start, pos));
    }

    private String identifier() {
      int start = pos;
      while (pos < input.length()) {
        char c = input.charAt(pos);
        if (Character.isLetterOrDigit(c) || c == '_') pos++;
        else break;
      }
      return input.substring(start, pos);
    }

    private boolean peekLetter() {
      return pos < input.length() && Character.isLetter(input.charAt(pos));
    }

    private void expect(char c) {
      skipSpace();
      if (!consume(c)) throw new IllegalArgumentException("Expected " + c);
    }

    private boolean consume(char c) {
      if (pos < input.length() && input.charAt(pos) == c) {
        pos++;
        return true;
      }
      return false;
    }

    private void skipSpace() {
      while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }
  }
}
