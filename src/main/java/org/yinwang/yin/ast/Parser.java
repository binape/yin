package org.yinwang.yin.ast;


import org.jetbrains.annotations.Nullable;
import org.yinwang.yin._;

import java.util.*;


public class Parser {

    public String file;
    public String text;
    public int position;
    public int line;
    public int col;
    public final Set<String> allDelims = new HashSet<>();
    public final Map<String, String> match = new HashMap<>();


    public Parser(String file) {
        this.file = file;
        this.text = _.readFile(file);
        this.position = 0;
        this.line = 0;
        this.col = 0;

        if (text == null) {
            _.abort("failed to read file: " + file);
        }

        addDelimiterPair("(", ")");
        addDelimiterPair("{", "}");
        addDelimiterPair("[", "]");
        addDelimiter(".");
    }


    public void forward() {
        if (text.charAt(position) == '\n') {
            line++;
            col = 0;
            position++;
        } else {
            col++;
            position++;
        }
    }


    public void addDelimiterPair(String open, String close) {
        allDelims.add(open);
        allDelims.add(close);
        match.put(open, close);
    }


    public void addDelimiter(String delim) {
        allDelims.add(delim);
    }


    public boolean isDelimiter(String c) {
        return allDelims.contains(c);
    }


    public boolean isDelimiter(char c) {
        return allDelims.contains(Character.toString(c));
    }


    public boolean isOpen(String c) {
        return match.keySet().contains(c);
    }


    public boolean isClose(String c) {
        return match.values().contains(c);
    }


    public String matchDelim(String open) {
        return match.get(open);
    }


    public boolean matchString(String open, String close) {
        String matched = match.get(open);
        if (matched != null && matched.equals(close)) {
            return true;
        } else {
            return false;
        }
    }


    public boolean matchDelim(Sexp open, Sexp close) {
        return (open instanceof Token &&
                close instanceof Token &&
                matchString(((Token) open).content, ((Token) close).content));
    }


    /**
     * lexer
     *
     * @return a token or null if file ends
     */
    @Nullable
    private Token nextToken() {
        // skip spaces
        while (position < text.length() &&
                Character.isWhitespace(text.charAt(position)))
        {
            forward();
        }

        // end of file
        if (position >= text.length()) {
            return null;
        }

        char cur = text.charAt(position);

        // delimiters
        if (isDelimiter(cur)) {
            Token ret = new Token(Token.TokenType.DELIMITER, Character.toString(cur), file, position, position + 1,
                    line, col);
            forward();
            return ret;
        }

        // string
        if (text.charAt(position) == '"') {
            forward();   // skip "
            int start = position;
            int startLine = line;
            int startCol = col;

            while (position < text.length() &&
                    !(text.charAt(position) == '"' && text.charAt(position - 1) != '\\'))
            {
                forward();
            }

            if (position >= text.length()) {
                _.abort("runaway string from: " + start);
            }

            int end = position;
            forward(); // skip "

            String content = text.substring(start, end);
            return new Token(Token.TokenType.STRING, content, file, start, end, startLine, startCol);
        }


        // find consequtive token
        int start = position;
        int startLine = line;
        int startCol = col;

        while (position < text.length() &&
                !Character.isWhitespace(cur) &&
                !isDelimiter(cur))
        {
            forward();
            if (position < text.length()) {
                cur = text.charAt(position);
            }
        }

        String content = text.substring(start, position);
        return new Token(Token.TokenType.IDENT, content, file, start, position, startLine, startCol);
    }


    /**
     * parser
     *
     * @return a Sexp or null if file ends
     */
    public Sexp nextSexp() {
        Token begin = nextToken();

        // end of file
        if (begin == null) {
            return null;
        }

        // try to get matched (...)
        if (isOpen(begin.content)) {
            List<Sexp> tokens = new ArrayList<>();
            Sexp iter = nextSexp();

            while (!matchDelim(begin, iter)) {
                if (iter == null) {
                    _.abort("unclosed delimeter " + begin.content + " at: " + begin.start);
                    return null;
                } else {
                    tokens.add(iter);
                    iter = nextSexp();
                }
            }
            return new Tuple(tokens, begin.content, ((Token) iter).content, begin.file, begin.start, iter.end,
                    begin.line, begin.col);
        } else {
            return begin;
        }
    }


    public Sexp parse() {
        List<Sexp> elements = new ArrayList<>();
        Sexp s = nextSexp();
        while (s != null) {
            elements.add(s);
            s = nextSexp();
        }
        return new Tuple(elements, "[", "]", file, 0, text.length(), 0, 0);
    }


    public static void main(String[] args) {
        Parser p = new Parser(args[0]);
        _.msg("tree: " + p.parse());
    }
}
