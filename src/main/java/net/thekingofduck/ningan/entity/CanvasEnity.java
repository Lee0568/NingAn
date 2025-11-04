package net.thekingofduck.ningan.entity;

import java.util.Set; // 1. 确保导入 Set

public class CanvasEnity {
    private Integer number;
    private String canvasId;
    private Set<String> attackTypes; // 2. 新增 attackTypes 字段

    // --- Getters and Setters ---
    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public String getCanvasId() {
        return canvasId;
    }

    public void setCanvasId(String canvasId) {
        this.canvasId = canvasId;
    }

    // 3. 为新字段添加 getter 和 setter
    public Set<String> getAttackTypes() {
        return attackTypes;
    }

    public void setAttackTypes(Set<String> attackTypes) {
        this.attackTypes = attackTypes;
    }
}