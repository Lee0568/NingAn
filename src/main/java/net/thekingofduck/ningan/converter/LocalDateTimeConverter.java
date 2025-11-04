package net.thekingofduck.ningan.converter;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 自定义JPA转换器，用于在 LocalDateTime 对象和数据库中的 TEXT 字符串之间进行转换。
 * @Converter(autoApply = true) 表示自动将此转换器应用于项目中所有的 LocalDateTime 类型的字段。
 */
@Converter(autoApply = true)
public class LocalDateTimeConverter implements AttributeConverter<LocalDateTime, String> {

    // 定义数据库中存储日期时间的字符串格式
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 将实体类中的 LocalDateTime 对象转换为要存入数据库的字符串
     * @param attribute  Java对象 (LocalDateTime)
     * @return 数据库中的值 (String)
     */
    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.format(FORMATTER);
    }

    /**
     * 将数据库中的字符串转换为实体类中的 LocalDateTime 对象
     * @param dbData 数据库中的值 (String)
     * @return Java对象 (LocalDateTime)
     */
    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        // 兼容没有毫秒的情况
        if (!dbData.contains(".")) {
            return LocalDateTime.parse(dbData, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        return LocalDateTime.parse(dbData, FORMATTER);
    }
}