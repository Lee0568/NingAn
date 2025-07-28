package net.thekingofduck.loki.mapper;

import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.entity.UserInfoEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Project: loki
 * Date:2021/1/9 下午11:12
 * @author CoolCat
 * @version 1.0.0
 * Github:https://github.com/TheKingOfDuck
 * When I wirting my code, only God and I know what it does. After a while, only God knows.
 */
@Mapper
public interface HttpLogMapper {

    /**
     * httplog get
     */
    @Select("select * from httplog order by id desc limit (#{page}-1)*#{limit},#{limit}")
    List<HttpLogEntity> getHttpLog(Integer page, Integer limit);
    /**
     * httplog count get
     */
    @Select("select count(*) from httplog")
    Integer getHttpLogCount();
    /**
     * httplog add
     */
    @Insert("INSERT INTO `main`.`httplog`(`ip`,`method`,`path`,`parameter`,`headers`,`body`,`time`) VALUES (#{ip},#{method},#{path},#{parameter},#{headers},#{body},#{time})")
    Integer addHttpLog(String ip,String method,String path,String parameter,String headers,String body,String time);
    /**
     * httplog delete
     */
    @Delete("DELETE FROM `main`.`httplog` WHERE rowid = #{id}")
    Integer deleteHttpLogById(int id);

    /**
     * 根据分页参数获取用户信息列表
     * SQL 语句使用 SQLite 兼容的 LIMIT count OFFSET offset 语法
     * count = #{limit}
     * offset = #{offset} (这个值需要在 Service 层计算好再传入)
     * @param limit 每页记录数
     * @param offset 跳过的记录数 (从0开始)
     * @param query 可选的搜索关键词
     * @return 用户信息列表
     */
    @Select({
            "<script>",
            "SELECT * FROM userInfo",
            // 如果需要搜索功能，可以加上 WHERE 子句
            "<where>",
            "  <if test='query != null and query != \"\"'>",
            "    AND (username LIKE '%' || #{query} || '%' OR email LIKE '%' || #{query} || '%')", // SQLite 的字符串连接是 ||
            "  </if>",
            "</where>",
            "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}", // <-- 关键修改：直接使用 limit 和 offset 参数
            "</script>"
    })
    // 注意 @Param 注解，它将方法参数名映射到 SQL 中的 #{参数名}
    // 参数顺序可以根据你的Service层调用习惯调整，但需要与@Param保持一致
    List<UserInfoEntity> getUserInfo(@Param("limit") Integer limit, @Param("offset") Integer offset, @Param("query") String query);
    /**
     * 获取用户信息的总记录数
     * @param query 可选的搜索关键词 (用于搜索功能)
     * @return 总记录数
     */
    @Select({
            "<script>",
            "SELECT COUNT(*) FROM userInfo",
            "<where>",
            "  <if test='query != null and query != \"\"'>",
            "    AND (username LIKE '%' || #{query} || '%' OR email LIKE '%' || #{query} || '%')", // SQLite 的字符串连接是 ||
            "  </if>",
            "</where>",
            "</script>"
    })
    // 修正方法名为 getUserInfoCount，并添加 @Param("query") 支持搜索条件下的计数
    Long getUserInfoCount(@Param("query") String query);


    /**
     * 查询所有HttpLog记录
     * Mybatis会自动将查询结果映射到HttpLog对象列表
     */
    @Select("SELECT id, ip, method, path, parameter, body, headers, time FROM httplog ORDER BY ip, time ASC")
    List<HttpLogEntity> selectAllLogs();
}
