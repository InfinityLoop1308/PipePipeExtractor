package org.schabi.newpipe.extractor.services.youtube;

import static org.schabi.newpipe.extractor.utils.Parser.matchMultiplePatterns;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.utils.JavaScript;
import org.schabi.newpipe.extractor.utils.Parser;
import org.schabi.newpipe.extractor.utils.jsextractor.JavaScriptExtractor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to get the throttling parameter decryption code and check if a streaming has the
 * throttling parameter.
 */
final class YoutubeThrottlingParameterUtils {

    private static final Pattern THROTTLING_PARAM_PATTERN = Pattern.compile("[&?]n=([^&]+)");

    private static final String SINGLE_CHAR_VARIABLE_REGEX = "[a-zA-Z0-9$_]";

    private static final String MULTIPLE_CHARS_REGEX = SINGLE_CHAR_VARIABLE_REGEX + "+";

    private static final String ARRAY_ACCESS_REGEX = "\\[(\\d+)]";

    // CHECKSTYLE:OFF
    private static final Pattern[] DEOBFUSCATION_FUNCTION_NAME_REGEXES = {
            /*
             * The sixth regex matches the following text, where we want Yva and the array index
             * accessed:
             *
             * b=Yva[0](b)
             */
            Pattern.compile(SINGLE_CHAR_VARIABLE_REGEX
                    + "=(" + MULTIPLE_CHARS_REGEX + ")"  + ARRAY_ACCESS_REGEX + "\\("
                    + SINGLE_CHAR_VARIABLE_REGEX + "\\)"),
            /*
             * The first regex matches the following text, where we want Wma and the array index
             * accessed:
             *
             * a.D&&(b="nn"[+a.D],WL(a),c=a.j[b]||null)&&(c=SDa[0](c),a.set(b,c),SDa.length||Wma("")
             */
            Pattern.compile(SINGLE_CHAR_VARIABLE_REGEX + "=\"nn\"\\[\\+" + MULTIPLE_CHARS_REGEX
                    + "\\." + MULTIPLE_CHARS_REGEX + "]," + MULTIPLE_CHARS_REGEX + "\\("
                    + MULTIPLE_CHARS_REGEX + "\\)," + MULTIPLE_CHARS_REGEX + "="
                    + MULTIPLE_CHARS_REGEX + "\\." + MULTIPLE_CHARS_REGEX + "\\["
                    + MULTIPLE_CHARS_REGEX + "]\\|\\|null\\).+\\|\\|(" + MULTIPLE_CHARS_REGEX
                    + ")\\(\"\"\\)"),

            /*
             * The second regex matches the following text, where we want SDa and the array index
             * accessed:
             *
             * a.D&&(b="nn"[+a.D],WL(a),c=a.j[b]||null)&&(c=SDa[0](c),a.set(b,c),SDa.length||Wma("")
             */
            Pattern.compile(SINGLE_CHAR_VARIABLE_REGEX + "=\"nn\"\\[\\+" + MULTIPLE_CHARS_REGEX
                    + "\\." + MULTIPLE_CHARS_REGEX + "]," + MULTIPLE_CHARS_REGEX + "\\("
                    + MULTIPLE_CHARS_REGEX + "\\)," + MULTIPLE_CHARS_REGEX + "="
                    + MULTIPLE_CHARS_REGEX + "\\." + MULTIPLE_CHARS_REGEX + "\\["
                    + MULTIPLE_CHARS_REGEX + "]\\|\\|null\\)&&\\(" + MULTIPLE_CHARS_REGEX + "=("
                    + MULTIPLE_CHARS_REGEX + ")" + ARRAY_ACCESS_REGEX),

            /*
             * The third regex matches the following text, where we want rma:
             *
             * a.D&&(b="nn"[+a.D],c=a.get(b))&&(c=rDa[0](c),a.set(b,c),rDa.length||rma("")
             */
            Pattern.compile(SINGLE_CHAR_VARIABLE_REGEX + "=\"nn\"\\[\\+" + MULTIPLE_CHARS_REGEX
                    + "\\." + MULTIPLE_CHARS_REGEX + "]," + MULTIPLE_CHARS_REGEX + "="
                    + MULTIPLE_CHARS_REGEX + "\\.get\\(" + MULTIPLE_CHARS_REGEX + "\\)\\).+\\|\\|("
                    + MULTIPLE_CHARS_REGEX + ")\\(\"\"\\)"),

            /*
             * The fourth regex matches the following text, where we want rDa and the array index
             * accessed:
             *
             * a.D&&(b="nn"[+a.D],c=a.get(b))&&(c=rDa[0](c),a.set(b,c),rDa.length||rma("")
             */
            Pattern.compile(SINGLE_CHAR_VARIABLE_REGEX + "=\"nn\"\\[\\+" + MULTIPLE_CHARS_REGEX
                    + "\\." + MULTIPLE_CHARS_REGEX + "]," + MULTIPLE_CHARS_REGEX + "="
                    + MULTIPLE_CHARS_REGEX + "\\.get\\(" + MULTIPLE_CHARS_REGEX + "\\)\\)&&\\("
                    + MULTIPLE_CHARS_REGEX + "=(" + MULTIPLE_CHARS_REGEX + ")\\[(\\d+)]"),

            /*
             * The fifth regex matches the following text, where we want BDa and the array index
             * accessed:
             *
             * (b=String.fromCharCode(110),c=a.get(b))&&(c=BDa[0](c)
             */
            Pattern.compile("\\(" + SINGLE_CHAR_VARIABLE_REGEX + "=String\\.fromCharCode\\(110\\),"
                    + SINGLE_CHAR_VARIABLE_REGEX + "=" + SINGLE_CHAR_VARIABLE_REGEX + "\\.get\\("
                    + SINGLE_CHAR_VARIABLE_REGEX + "\\)\\)" + "&&\\(" + SINGLE_CHAR_VARIABLE_REGEX
                    + "=(" + MULTIPLE_CHARS_REGEX + ")" + "(?:" + ARRAY_ACCESS_REGEX + ")?\\("
                    + SINGLE_CHAR_VARIABLE_REGEX + "\\)")

};
    // CHECKSTYLE:ON


    // Escape the curly end brace to allow compatibility with Android's regex engine
    // See https://stackoverflow.com/q/45074813
    @SuppressWarnings("RegExpRedundantEscape")
    private static final String DEOBFUSCATION_FUNCTION_BODY_REGEX =
            "=\\s*function([\\S\\s]*?\\}\\s*return [\\w$]+?\\.join\\(\"\"\\)\\s*\\};)";

    private static final String DEOBFUSCATION_FUNCTION_ARRAY_OBJECT_TYPE_DECLARATION_REGEX = "var ";

    private static final String FUNCTION_NAMES_IN_DEOBFUSCATION_ARRAY_REGEX =
            "\\s*=\\s*\\[(.+?)][;,]";

    private static final String FUNCTION_ARGUMENTS_REGEX =
            "=\\s*function\\s*\\(\\s*([^)]*)\\s*\\)";

    private static final String EARLY_RETURN_REGEX =
            ";\\s*if\\s*\\(\\s*typeof\\s+" + MULTIPLE_CHARS_REGEX
                    + "+\\s*===?\\s*(?:([\"'])undefined\\1|";

    private static final String EARLY_RETURN_APPEND_REGEX =
            "\\[\\d+\\])\\s*\\)\\s*return\\s+";

    private YoutubeThrottlingParameterUtils() {
    }

    /**
     * Get the throttling parameter deobfuscation function name of YouTube's base JavaScript file.
     *
     * @param javaScriptPlayerCode the complete JavaScript base player code
     * @return the name of the throttling parameter deobfuscation function
     * @throws ParsingException if the name of the throttling parameter deobfuscation function
     * could not be extracted
     */
    @Nonnull
    static String getDeobfuscationFunctionName(@Nonnull final String javaScriptPlayerCode)
            throws ParsingException {
        final Matcher matcher;
        try {
            matcher = matchMultiplePatterns(DEOBFUSCATION_FUNCTION_NAME_REGEXES,
                    javaScriptPlayerCode);
        } catch (Exception e) {
            throw new ParsingException("Could not find deobfuscation function with any of the "
                    + "known patterns in the base JavaScript player code", e);
        }

        final String functionName = matcher.group(1);

        final int arrayNum = Integer.parseInt(matcher.group(2));
        final Pattern arrayPattern = Pattern.compile(
                DEOBFUSCATION_FUNCTION_ARRAY_OBJECT_TYPE_DECLARATION_REGEX
                        + Pattern.quote(functionName)
                        + FUNCTION_NAMES_IN_DEOBFUSCATION_ARRAY_REGEX);
        final String arrayStr = Parser.matchGroup1(arrayPattern, javaScriptPlayerCode);
        final String[] names = arrayStr.split(",");
        return names[arrayNum];
    }

    /**
     * Get the throttling parameter deobfuscation code of YouTube's base JavaScript file.
     *
     * @param javaScriptPlayerCode the complete JavaScript base player code
     * @return the throttling parameter deobfuscation function name
     * @throws ParsingException if the throttling parameter deobfuscation code couldn't be
     * extracted
     */
    @Nonnull
    static String getDeobfuscationFunction(@Nonnull final String javaScriptPlayerCode,
                                           @Nonnull final String functionName)
            throws ParsingException {
        String result;
        try {
            result = parseFunctionWithLexer(javaScriptPlayerCode, functionName);
        } catch (final Exception e) {
            result =  parseFunctionWithRegex(javaScriptPlayerCode, functionName);
        }
        Result temp = extractPlayerJsGlobalVar(javaScriptPlayerCode);
        return fixupFunction(result, temp);
    }

    /**
     * Removes an early return statement from the code of the throttling parameter deobfuscation
     * function.
     *
     * <p>In newer version of the player code the function contains a check for something defined
     * outside of the function. If that was not found it will return early.
     *
     * <p>The check can look like this (JS):<br>
     * if(typeof RUQ==="undefined")return p;
     *
     * <p>In this example RUQ will always be undefined when running the function as standalone.
     * If the check is kept it would just return p which is the input parameter and would be wrong.
     * For that reason this check and return statement needs to be removed.
     *
     * @param function the original throttling parameter deobfuscation function code
     * @return the throttling parameter deobfuscation function code with the early return statement
     * removed
     */
    @Nonnull
    private static String fixupFunction(@Nonnull final String function, final Result varData)
            throws Parser.RegexException {
        final String globalVar = varData.code;
        final String varName = varData.name;
        final String firstArgName = Parser
                .matchGroup1(FUNCTION_ARGUMENTS_REGEX, function)
                .split(",")[0].trim();
        final Pattern earlyReturnPattern = Pattern.compile(
                EARLY_RETURN_REGEX + Pattern.quote(varName) + EARLY_RETURN_APPEND_REGEX + Pattern.quote(firstArgName) + ";",
                Pattern.DOTALL);
        final Matcher earlyReturnCodeMatcher = earlyReturnPattern.matcher(function);
        return globalVar + "; " + earlyReturnCodeMatcher.replaceFirst(";");
    }


    /**
     * Get the throttling parameter of a streaming URL if it exists.
     *
     * @param streamingUrl a streaming URL
     * @return the throttling parameter of the streaming URL or {@code null} if no parameter has
     * been found
     */
    @Nullable
    static String getThrottlingParameterFromStreamingUrl(@Nonnull final String streamingUrl) {
        try {
            return Parser.matchGroup1(THROTTLING_PARAM_PATTERN, streamingUrl);
        } catch (final Parser.RegexException e) {
            // If the throttling parameter could not be parsed from the URL, it means that there is
            // no throttling parameter
            // Return null in this case
            return null;
        }
    }

    @Nonnull
    private static String parseFunctionWithLexer(@Nonnull final String javaScriptPlayerCode,
                                                 @Nonnull final String functionName)
            throws ParsingException {
        final String functionBase = functionName + "=function";
        return functionBase + JavaScriptExtractor.matchToClosingBrace(
                javaScriptPlayerCode, functionBase) + ";";
    }

    @Nonnull
    private static String parseFunctionWithRegex(@Nonnull final String javaScriptPlayerCode,
                                                 @Nonnull final String functionName)
            throws Parser.RegexException {
        // Quote the function name, as it may contain special regex characters such as dollar
        final Pattern functionPattern = Pattern.compile(
                Pattern.quote(functionName) + DEOBFUSCATION_FUNCTION_BODY_REGEX,
                Pattern.DOTALL);
        return validateFunction("function " + functionName
                + Parser.matchGroup1(functionPattern, javaScriptPlayerCode));
    }

    @Nonnull
    private static String validateFunction(@Nonnull final String function) {
        JavaScript.compileOrThrow(function);
        return function;
    }

    static class Result {
        public final String code;
        public final String name;
        public final String value;

        public Result(String code, String name, String value) {
            this.code = code;
            this.name = name;
            this.value = value;
        }
    }

    static Result extractPlayerJsGlobalVar(String jsCode) {
        // Pattern explanation:
        // "use strict" in quotes followed by semicolon and optional whitespace
        // var keyword, variable name (alphanumeric + $_), equals sign
        // quoted string followed by .split() with quoted delimiter
        String pattern = "([\"'])use\\s+strict\\1;\\s*(var\\s+([a-zA-Z0-9_$]+)\\s*=\\s*" +
                "(" +
                "([\"'])((?:(?!\\5).|\\\\.)+)\\5\\.split\\(([\"'])[^\"']+\\7\\)" +
                "|\\[\\s*(?:([\"'])((?:(?!\\8).|\\\\.)*?)\\8\\s*,?\\s*)+\\]" +
                "))[;,]";




        Pattern regex = Pattern.compile(pattern);
        Matcher matcher = regex.matcher(jsCode);

        if (matcher.find()) {
            // Group 2: full assignment code
            // Group 3: variable name
            // Group 4-6: value portion including quotes and split
            String fullCode = matcher.group(2);
            String varName = matcher.group(3);
            String valueCode = matcher.group(4);
            return new Result(fullCode, varName, valueCode);
        }

        // Return null values if no match found (similar to Python's default=(None, None, None))
        return new Result(null, null, null);
    }

}
