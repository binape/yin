package org.yinwang.yin.parser;


import org.jetbrains.annotations.Nullable;
import org.yinwang.yin.Constants;
import org.yinwang.yin._;
import org.yinwang.yin.ast.*;

import java.util.ArrayList;
import java.util.List;


/**
 * Lexer
 * split text stream into tokens, nubmers, delimeters etc
 */
public class Lexer {

    public String file;
    public String text;

    // current offset indicators
    public int offset;
    public int line;
    public int col;


    public Lexer(String file) {
        this.file = _.unifyPath(file);
        this.text = _.readFile(file);
        this.offset = 0;
        this.line = 0;
        this.col = 0;

        if (text == null) {
            _.abort("failed to read file: " + file);
        }

        Delimeter.addDelimiterPair(Constants.PAREN_BEGIN, Constants.PAREN_END);
        Delimeter.addDelimiterPair(Constants.CURLY_BEGIN, Constants.CURLY_END);
        Delimeter.addDelimiterPair(Constants.SQUARE_BEGIN, Constants.ARRAY_END);

        Delimeter.addDelimiter(Constants.ATTRIBUTE_ACCESS);
    }


    public void forward() {
        if (text.charAt(offset) == '\n') {
            line++;
            col = 0;
            offset++;
        } else {
            col++;
            offset++;
        }
    }


    public void skip(int n) {
        for (int i = 0; i < n; i++) {
            forward();
        }
    }


    public boolean skipSpaces() {
        boolean found = false;

        while (offset < text.length() &&
                Character.isWhitespace(text.charAt(offset)))
        {
            found = true;
            forward();
        }
        return found;
    }


    public boolean skipComments() {
        boolean found = false;

        if (text.startsWith(Constants.LINE_COMMENT, offset)) {
            found = true;

            // skip to line end
            while (offset < text.length() && text.charAt(offset) != '\n') {
                forward();
            }
            if (offset < text.length()) {
                forward();
            }
        }
        return found;
    }


    public void skipSpacesAndComments() {
        while (skipSpaces() || skipComments()) {
            // actions are performed by skipSpaces() and skipComments()
        }
    }


    public Node scanString() throws ParserException {
        int start = offset;
        int startLine = line;
        int startCol = col;
        skip(Constants.STRING_START.length());    // skip quote mark

        while (true) {
            // detect runaway strings at end of file or at newline
            if (offset >= text.length() || text.charAt(offset) == '\n') {
                throw new ParserException("runaway string", startLine, startCol, offset);
            }

            // end of string
            else if (text.startsWith(Constants.STRING_END, offset)) {
                skip(Constants.STRING_END.length());    // skip quote mark
                break;
            }

            // skip any char after STRING_ESCAPE
            else if (text.startsWith(Constants.STRING_ESCAPE, offset) && offset + 1 < text.length()) {
                skip(Constants.STRING_ESCAPE.length() + 1);
            }

            // other characters (string content)
            else {
                forward();
            }
        }

        int end = offset;
        String content = text.substring(
                start + Constants.STRING_START.length(),
                end - Constants.STRING_END.length());

        return new Str(content, file, start, end, startLine, startCol);
    }


    public static boolean isNumberChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '+' || c == '-';
    }


    public Node scanNumber() {
        int start = offset;
        int startLine = line;
        int startCol = col;

        while (offset < text.length() && isNumberChar(text.charAt(offset))) {
            forward();
        }

        String content = text.substring(start, offset);

        IntNum intNum = IntNum.parse(content, file, start, offset, startLine, startCol);
        if (intNum != null) {
            return intNum;
        } else {
            FloatNum floatNum = FloatNum.parse(content, file, start, offset, startLine, startCol);
            if (floatNum != null) {
                return floatNum;
            } else {
                _.abort(file + ":" + startLine + ":" + startCol + " : incorrect number format: " + content);
                return null;
            }
        }
    }


    public Node scanNameOrKeyword() {
        char cur = text.charAt(offset);
        int start = offset;
        int startLine = line;
        int startCol = col;

        while (offset < text.length() &&
                !Character.isWhitespace(cur) &&
                !Delimeter.isDelimiter(cur) &&
                cur != '"')
        {
            forward();
            if (offset < text.length()) {
                cur = text.charAt(offset);
            }
        }

        String content = text.substring(start, offset);
        if (content.startsWith(":")) {
            return new Keyword(content.substring(1), file, start, offset, startLine, startCol);
        } else {
            return new Name(content, file, start, offset, startLine, startCol);
        }
    }


    /**
     * Lexer
     *
     * @return a token or null if file ends
     */
    @Nullable
    public Node nextToken() throws ParserException {

        skipSpacesAndComments();

        // end of file
        if (offset >= text.length()) {
            return null;
        }

        {
            // case 1. delimiters
            char cur = text.charAt(offset);
            if (Delimeter.isDelimiter(cur)) {
                Node ret = new Delimeter(Character.toString(cur), file, offset, offset + 1, line, col);
                forward();
                return ret;
            }
        }

        // case 2. string
        if (text.startsWith(Constants.STRING_START, offset)) {
            return scanString();
        }

        // case 3. number
        if (Character.isDigit(text.charAt(offset)) ||
                ((text.charAt(offset) == '+' || text.charAt(offset) == '-')
                        && offset + 1 < text.length() && Character.isDigit(text.charAt(offset + 1))))
        {
            return scanNumber();
        }

        // case 4. name or keyword
        return scanNameOrKeyword();
    }


    public static void main(String[] args) throws ParserException {
        Lexer lex = new Lexer(args[0]);

        List<Node> tokens = new ArrayList<>();
        Node n = lex.nextToken();
        while (n != null) {
            tokens.add(n);
            n = lex.nextToken();
        }
        _.msg("lexer result: ");
        for (Node node : tokens) {
            _.msg(node.toString());
        }
    }
}
