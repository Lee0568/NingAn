package net.thekingofduck.ningan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class HoneypotController { // 建议重命名为 AdminController

    // 主框架页面
    @GetMapping("/index.html")
    public String showIndexPage() {
        return "admin/index"; // 返回主框架页面
    }

    // 基础列表内容片段
    @GetMapping("/dashboard_content.html") // 新增这个映射
    public String showDashboardContent() {
        return "admin/dashboard_content"; // 返回基础列表的内容片段
    }

    // 用户管理内容片段
    @GetMapping("/userlist.html")
    public String showUserListPage() {
        return "admin/userlist"; // 返回用户列表内容片段页面
    }

    // 命令执行内容片段
    @GetMapping("/command.html")
    public String showCommandExecPage() {
        return "admin/command"; // 返回命令执行内容片段页面
    }

    // SQL 查询内容片段
    @GetMapping("/sql_query.html")
    public String showSqlQueryPage() {
        return "admin/sql_query"; // 返回 SQL 查询内容片段页面
    }
    @GetMapping("/hackImg2.html")
    public String showHackImg() {
        return "admin/hackImg2.html"; // 返回 SQL 查询内容片段页面
    }

}