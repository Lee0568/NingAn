package net.thekingofduck.ningan.common;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class AttackPattern {
    private String name;
    private String regex; // 原始正则表达式字符串
    private List<String> keywords;
    private Pattern compiledPattern; // 编译后的Pattern对象

    /**
     * AttackPattern 构造函数
     *
     * @param name 攻击模式的名称
     * @param regex 攻击模式的正则表达式字符串
     * @param keywords 与该攻击模式相关的关键词列表
     */
    public AttackPattern(String name, String regex, List<String> keywords) {
        this.name = name;
        this.regex = regex;
        this.keywords = keywords;
        try {
            // 在构造时编译正则表达式，提高后续匹配的效率
            this.compiledPattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            // 如果正则表达式语法有误，捕获异常并打印错误信息
            System.err.println("正则表达式编译失败，请检查语法错误: " + e.getMessage());
            System.err.println("出错的正则表达式是: " + regex);
            throw e; // 重新抛出异常，阻止程序继续执行，以便快速定位问题
        }
    }

    /**
     * 检查给定的输入字符串是否匹配此攻击模式的正则表达式。
     *
     * @param input 需要检查的字符串
     * @return 如果匹配则返回 true，否则返回 false。
     */
    public boolean matches(String input) {
        if (input == null) {
            return false;
        }
        // 使用预编译的 Pattern 对象进行匹配
        return compiledPattern.matcher(input).find();
    }

    // --- Getter 方法 ---

    public String getName() {
        return name;
    }

    public String getRegex() {
        return regex;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    /**
     * 获取已编译的 Pattern 对象。
     * 在 HttpLogService 中，我们直接调用了 matches 方法，所以这个方法不一定需要直接调用，
     * 但如果将来有需要，可以提供。
     */
    public Pattern getCompiledPattern() {
        return compiledPattern;
    }
}