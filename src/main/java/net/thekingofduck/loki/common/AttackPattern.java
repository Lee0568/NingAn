package net.thekingofduck.loki.common;

import java.util.List;
import java.util.regex.Pattern;

public class AttackPattern {
    private String name;
    private Pattern regex;
    private List<String> keywords;

    public AttackPattern(String name, String regexStr, List<String> keywords) {
        this.name = name;
        this.regex = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE | Pattern.DOTALL); // 不区分大小写，DOTALL让.匹配所有字符包括换行
        this.keywords = keywords;
    }

    public String getName() {
        return name;
    }

    public Pattern getRegex() {
        return regex;
    }

    public List<String> getKeywords() {
        return keywords;
    }
}