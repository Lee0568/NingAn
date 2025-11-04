package net.thekingofduck.ningan.entity;

import lombok.Data;

/**
 * 蜜罐服务实体，前端列表展示与后端管理用
 */
@Data
public class HoneypotService {
    private Long id;
    private Integer port;
    private String type;       // Web服务/SSH服务/FTP等
    private String template;   // 模板名称 default/admin/bee/ssh/ftp/custom
    private String status;     // running/stopped
    private String uptime;     // 运行时间文本
    private Integer requests;  // 模拟或统计请求数

    private String name;       // 展示名称
    private String desc;       // 描述
}