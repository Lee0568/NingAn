package net.thekingofduck.ningan.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用于操作 security_setting 表的 Mapper 接口
 */
@Mapper
public interface SecuritySettingMapper {

    /**
     * 根据配置键（setting_key）查询配置值（setting_value）
     *
     * @param key 要查询的键
     * @return 查询到的字符串值
     */
    @Select("SELECT setting_value FROM security_setting WHERE setting_key = #{key}")
    String findValueByKey(@Param("key") String key);

}