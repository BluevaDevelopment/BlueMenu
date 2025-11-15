package net.blueva.menu.managers;

import net.blueva.menu.utils.MessagesUtil;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ConditionManager - Evaluates display conditions for menu items, buttons, and components
 * Supports both Java and Bedrock editions
 */
public class ConditionManager {

    /**
     * Evaluates a list of conditions where ALL must be true
     * @param player The player to evaluate conditions for
     * @param conditions List of condition strings
     * @return true if all conditions pass, false otherwise
     */
    public static boolean evaluateConditions(Player player, List<String> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true; // No conditions means always visible
        }

        for (String condition : conditions) {
            if (!evaluateCondition(player, condition)) {
                return false; // Any failed condition means the element should be hidden
            }
        }

        return true; // All conditions passed
    }

    /**
     * Evaluates conditions with all/any/none logic from a map
     * @param player The player to evaluate conditions for
     * @param conditionsMap Map containing "all", "any", and/or "none" keys
     * @return true if all condition groups pass, false otherwise
     */
    @SuppressWarnings("unchecked")
    public static boolean evaluateConditionsMap(Player player, Map<String, Object> conditionsMap) {
        if (conditionsMap == null || conditionsMap.isEmpty()) {
            return true;
        }

        // Check "all" conditions - ALL must be true
        if (conditionsMap.containsKey("all")) {
            Object allObj = conditionsMap.get("all");
            List<String> allConditions = new ArrayList<>();

            if (allObj instanceof List<?>) {
                for (Object item : (List<?>) allObj) {
                    if (item instanceof String) {
                        allConditions.add((String) item);
                    }
                }
            }

            if (!evaluateConditions(player, allConditions)) {
                return false; // All conditions failed
            }
        }

        // Check "any" conditions - AT LEAST ONE must be true
        if (conditionsMap.containsKey("any")) {
            Object anyObj = conditionsMap.get("any");
            List<String> anyConditions = new ArrayList<>();

            if (anyObj instanceof List<?>) {
                for (Object item : (List<?>) anyObj) {
                    if (item instanceof String) {
                        anyConditions.add((String) item);
                    }
                }
            }

            if (!anyConditions.isEmpty()) {
                boolean anyPassed = false;
                for (String condition : anyConditions) {
                    if (evaluateCondition(player, condition)) {
                        anyPassed = true;
                        break;
                    }
                }
                if (!anyPassed) {
                    return false; // None of the "any" conditions passed
                }
            }
        }

        // Check "none" conditions - NONE must be true (all must be false)
        if (conditionsMap.containsKey("none")) {
            Object noneObj = conditionsMap.get("none");
            List<String> noneConditions = new ArrayList<>();

            if (noneObj instanceof List<?>) {
                for (Object item : (List<?>) noneObj) {
                    if (item instanceof String) {
                        noneConditions.add((String) item);
                    }
                }
            }

            for (String condition : noneConditions) {
                if (evaluateCondition(player, condition)) {
                    return false; // One of the "none" conditions was true, so fail
                }
            }
        }

        return true; // All condition groups passed
    }

    /**
     * Evaluates a single condition string
     * @param player The player to evaluate the condition for
     * @param condition The condition string to evaluate
     * @return true if the condition passes, false otherwise
     */
    public static boolean evaluateCondition(Player player, String condition) {
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }

        // Replace placeholders first
        condition = MessagesUtil.formatPlaceholders(player, condition.trim());

        try {
            // Handle negation operator (!)
            if (condition.startsWith("!")) {
                String innerCondition = condition.substring(1).trim();
                // Remove parentheses if present
                if (innerCondition.startsWith("(") && innerCondition.endsWith(")")) {
                    innerCondition = innerCondition.substring(1, innerCondition.length() - 1).trim();
                }
                return !evaluateCondition(player, innerCondition);
            }

            // Handle logical OR operator (||) - lower precedence, evaluate first
            if (condition.contains("||")) {
                String[] parts = splitByOperator(condition, "||");
                for (String part : parts) {
                    if (evaluateCondition(player, part.trim())) {
                        return true;
                    }
                }
                return false;
            }

            // Handle logical AND operator (&&) - higher precedence, evaluate after OR
            if (condition.contains("&&")) {
                String[] parts = splitByOperator(condition, "&&");
                for (String part : parts) {
                    if (!evaluateCondition(player, part.trim())) {
                        return false;
                    }
                }
                return true;
            }

            // Remove outer parentheses if present
            if (condition.startsWith("(") && condition.endsWith(")")) {
                condition = condition.substring(1, condition.length() - 1).trim();
            }

            // Text operators (case-sensitive by default)
            if (condition.contains(" contains ")) {
                String[] parts = condition.split(" contains ", 2);
                String left = cleanValue(parts[0].trim());
                String right = cleanValue(parts[1].trim());
                return left.contains(right);
            }

            if (condition.contains(" startsWith ")) {
                String[] parts = condition.split(" startsWith ", 2);
                String left = cleanValue(parts[0].trim());
                String right = cleanValue(parts[1].trim());
                return left.startsWith(right);
            }

            if (condition.contains(" endsWith ")) {
                String[] parts = condition.split(" endsWith ", 2);
                String left = cleanValue(parts[0].trim());
                String right = cleanValue(parts[1].trim());
                return left.endsWith(right);
            }

            if (condition.contains(" matches ")) {
                String[] parts = condition.split(" matches ", 2);
                String left = cleanValue(parts[0].trim());
                String regex = cleanValue(parts[1].trim());
                return left.matches(regex);
            }

            if (condition.contains(" equalsIgnoreCase ")) {
                String[] parts = condition.split(" equalsIgnoreCase ", 2);
                String left = cleanValue(parts[0].trim());
                String right = cleanValue(parts[1].trim());
                return left.equalsIgnoreCase(right);
            }

            if (condition.contains(" equals ")) {
                String[] parts = condition.split(" equals ", 2);
                String left = cleanValue(parts[0].trim());
                String right = cleanValue(parts[1].trim());
                return left.equals(right);
            }

            // Comparison operators (need to check longer operators first)
            if (condition.contains(">=")) {
                String[] parts = condition.split(">=", 2);
                double left = evaluateExpression(parts[0].trim());
                double right = evaluateExpression(parts[1].trim());
                return left >= right;
            }

            if (condition.contains("<=")) {
                String[] parts = condition.split("<=", 2);
                double left = evaluateExpression(parts[0].trim());
                double right = evaluateExpression(parts[1].trim());
                return left <= right;
            }

            if (condition.contains("!=")) {
                String[] parts = condition.split("!=", 2);
                String leftStr = parts[0].trim();
                String rightStr = parts[1].trim();

                // Try numeric comparison first
                try {
                    double left = evaluateExpression(leftStr);
                    double right = evaluateExpression(rightStr);
                    return left != right;
                } catch (NumberFormatException e) {
                    // Fall back to string comparison
                    return !cleanValue(leftStr).equals(cleanValue(rightStr));
                }
            }

            if (condition.contains("==")) {
                String[] parts = condition.split("==", 2);
                String leftStr = parts[0].trim();
                String rightStr = parts[1].trim();

                // Try numeric comparison first
                try {
                    double left = evaluateExpression(leftStr);
                    double right = evaluateExpression(rightStr);
                    return left == right;
                } catch (NumberFormatException e) {
                    // Fall back to string comparison
                    return cleanValue(leftStr).equals(cleanValue(rightStr));
                }
            }

            if (condition.contains(">")) {
                String[] parts = condition.split(">", 2);
                double left = evaluateExpression(parts[0].trim());
                double right = evaluateExpression(parts[1].trim());
                return left > right;
            }

            if (condition.contains("<")) {
                String[] parts = condition.split("<", 2);
                double left = evaluateExpression(parts[0].trim());
                double right = evaluateExpression(parts[1].trim());
                return left < right;
            }

            // If no operator found, try to evaluate as boolean
            String value = cleanValue(condition);
            if (value.equalsIgnoreCase("true")) {
                return true;
            } else if (value.equalsIgnoreCase("false")) {
                return false;
            }

            // Default: condition passes if value is non-empty
            return !value.isEmpty();

        } catch (Exception e) {
            // If evaluation fails, log and return false (safer to hide than show with errors)
            System.err.println("Failed to evaluate condition: " + condition + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Splits a condition by an operator while respecting parentheses
     */
    private static String[] splitByOperator(String condition, String operator) {
        List<String> parts = new ArrayList<>();
        int parenthesesLevel = 0;
        int lastSplit = 0;

        for (int i = 0; i < condition.length() - operator.length() + 1; i++) {
            char c = condition.charAt(i);
            if (c == '(') {
                parenthesesLevel++;
            } else if (c == ')') {
                parenthesesLevel--;
            } else if (parenthesesLevel == 0 && condition.substring(i).startsWith(operator)) {
                parts.add(condition.substring(lastSplit, i));
                lastSplit = i + operator.length();
                i += operator.length() - 1;
            }
        }

        parts.add(condition.substring(lastSplit));
        return parts.toArray(new String[0]);
    }

    /**
     * Evaluates a mathematical expression
     */
    private static double evaluateExpression(String expression) throws NumberFormatException {
        expression = expression.trim();

        // Remove quotes if present
        expression = cleanValue(expression);

        // Handle parentheses - evaluate inner expressions first
        while (expression.contains("(")) {
            int openIndex = expression.lastIndexOf('(');
            int closeIndex = expression.indexOf(')', openIndex);

            if (closeIndex == -1) {
                throw new NumberFormatException("Mismatched parentheses in expression: " + expression);
            }

            String innerExpression = expression.substring(openIndex + 1, closeIndex);
            double innerResult = evaluateExpression(innerExpression);

            expression = expression.substring(0, openIndex) + innerResult + expression.substring(closeIndex + 1);
        }

        // Try direct numeric conversion first
        try {
            return Double.parseDouble(expression);
        } catch (NumberFormatException e) {
            // Continue to expression evaluation
        }

        // Handle addition and subtraction (lower precedence - evaluate last)
        // Find rightmost + or - to split on (left-to-right evaluation)
        int addSubIndex = -1;
        char addSubOp = ' ';
        for (int i = expression.length() - 1; i >= 0; i--) {
            char c = expression.charAt(i);
            if (c == '+' || c == '-') {
                // Make sure it's not a negative sign at the start or after an operator
                if (i > 0) {
                    addSubIndex = i;
                    addSubOp = c;
                    break;
                }
            }
        }

        if (addSubIndex > 0) {
            double left = evaluateExpression(expression.substring(0, addSubIndex));
            double right = evaluateExpression(expression.substring(addSubIndex + 1));

            if (addSubOp == '+') {
                return left + right;
            } else {
                return left - right;
            }
        }

        // Handle multiplication, division, and modulo (higher precedence)
        // Find rightmost *, /, or % to split on
        int mulDivIndex = -1;
        char mulDivOp = ' ';
        for (int i = expression.length() - 1; i >= 0; i--) {
            char c = expression.charAt(i);
            if (c == '*' || c == '/' || c == '%') {
                mulDivIndex = i;
                mulDivOp = c;
                break;
            }
        }

        if (mulDivIndex > 0) {
            double left = evaluateExpression(expression.substring(0, mulDivIndex));
            double right = evaluateExpression(expression.substring(mulDivIndex + 1));

            switch (mulDivOp) {
                case '*':
                    return left * right;
                case '/':
                    if (right == 0) {
                        throw new NumberFormatException("Division by zero");
                    }
                    return left / right;
                case '%':
                    return left % right;
            }
        }

        throw new NumberFormatException("Cannot parse expression: " + expression);
    }

    /**
     * Removes quotes from a value
     */
    private static String cleanValue(String value) {
        value = value.trim();

        // Remove single or double quotes
        if ((value.startsWith("'") && value.endsWith("'")) ||
            (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }
}
