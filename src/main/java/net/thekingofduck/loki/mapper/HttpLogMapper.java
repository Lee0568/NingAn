package net.thekingofduck.loki.mapper;

import net.thekingofduck.loki.entity.CanvasEnity;
import net.thekingofduck.loki.entity.HttpLogEntity;
import net.thekingofduck.loki.entity.UserInfoEntity;
import org.apache.ibatis.annotations.*;
import org.springframework.http.HttpEntity;

import java.util.Date;
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
     * 插入canvas指纹到最新的日志记录中
     */
    @Update("UPDATE `main`.`httplog` SET `canvasId` = #{canvasId} WHERE id = (SELECT MAX(id) FROM `main`.`httplog`)")
    Integer updateCanvasId(String canvasId);
    /**
     * 插入canvasId(外键)到canvas表中
     */
    @Insert("Insert INTO `main`.`canvas`(`canvasId`) values (#{canvasId})")
    Integer addCanvasId(String canvasId);

    /**
     * httplog delete
     * @param id
     * @return
     */
    @Delete("DELETE FROM `main`.`httplog` WHERE rowid = #{id}")
    Integer deleteHttpLog(Integer id);
    
    /**
     * 安全删除 httplog 记录（先删除关联的 canvas 记录）
     * @param id httplog 记录的 ID
     * @return 删除的记录数
     */
    @Delete({
        "DELETE FROM `main`.`canvas` WHERE canvasId IN (SELECT canvasId FROM `main`.`httplog` WHERE rowid = #{id});",
        "DELETE FROM `main`.`httplog` WHERE rowid = #{id}"
    })
    Integer safeDeleteHttpLog(Integer id);

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
    /**
     * 返回canvas表中的值
     */
    @Select("select * from canvas order by number desc limit (#{page}-1)*#{limit},#{limit}")
    List<CanvasEnity> selectAllCanvaslogs(Integer page,Integer limit);
    @Select("SELECT COUNT(*) FROM canvas")
    int countTotalCanvasLogs();

    /**
     * 根据canvasId查询number(对应序号)
     */
    @Select("select number from canvas where canvasId = #{canvasId}")
    Integer getCanvasNumber(String canvasId);
    /**
     * 根据canvasId从关联表httplog中查询出所有iP，body字段信息
     */
    @Select("SELECT t2.iP, t2.body FROM canvas AS t1 JOIN httplog AS t2 ON t1.canvasId = t2.canvasId WHERE t1.canvasId = #{canvasId}")
    public List<HttpLogEntity> findIpsAndBodiesByCanvasId(String canvasId);
    /**
     * 插入canvasId到canvas表中时防止重复
     */
    @Select("select count(*) from canvas where canvasId = #{canvasId}")
    Integer getCanvasIdCount(String canvasId);

    /**
     * 从httplog表中拿出所有对于canvasId的数据并交给AI分析
     */
    @Select("select * from httplog where canvasId = #{canvasId}")
    public List<HttpLogEntity> getAllInfoByCanvasId(String canvasId);

    /**
     * 获取最近的日志记录
     * @param limit 限制返回的记录数
     * @return 最近的日志记录列表
     */
    @Select("SELECT * FROM httplog ORDER BY id DESC LIMIT #{limit}")
    List<HttpLogEntity> getRecentLogs(@Param("limit") Integer limit);

    /**
     * 更新 httplog 表中最新一条记录的 canvasId 字段。
     * @param canvasId 要设置的 canvasId 值
     * @return 返回受影响的行数，成功则为 1
     */
    @Update("UPDATE `main`.`httplog` SET `canvasId` = #{canvasId} WHERE id = (SELECT MAX(id) FROM `main`.`httplog`)")
    Integer updateCanvasIdForLastHttpLog(@Param("canvasId") String canvasId);


    /**
     * 根据起始和结束时间查询日志记录
     *
     * @param startTime 起始时间，格式为 'yyyy-MM-dd HH:mm:ss'
     * @param endTime   结束时间，格式为 'yyyy-MM-dd HH:mm:ss'
     * @return 日志实体列表
     */
    @Select("SELECT * FROM httplog WHERE datetime(time) BETWEEN datetime(#{startTime}) AND datetime(#{endTime})")
    List<HttpLogEntity> findLogsBetweenDates(@Param("startTime") String startTime, @Param("endTime") String endTime);
}
