package net.thekingofduck.ningan.config;

import net.thekingofduck.ningan.model.SecuritySetting;
import net.thekingofduck.ningan.repository.SecuritySettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class SecurityPolicyManager {

    public static final String KEY_FREQUENCY = "autoban_frequency";
    public static final String KEY_DURATION = "autoban_duration";

    // 使用 volatile 确保多线程环境下的可见性
    private volatile int frequencyThreshold = 100; // 默认值: 100次/分钟
    private volatile int banDurationMinutes = 60;  // 默认值: 60分钟

    @Autowired
    private SecuritySettingRepository settingRepository;

    // Spring容器初始化该Bean后，自动从数据库加载配置
    @PostConstruct
    public void init() {
        settingRepository.findById(KEY_FREQUENCY)
                .ifPresent(setting -> this.frequencyThreshold = Integer.parseInt(setting.getSettingValue()));
        settingRepository.findById(KEY_DURATION)
                .ifPresent(setting -> this.banDurationMinutes = Integer.parseInt(setting.getSettingValue()));
        System.out.println("Loaded initial security policy: Freq=" + frequencyThreshold + ", Duration=" + banDurationMinutes);
    }

    // 更新策略并持久化到数据库
    public void updatePolicy(int frequency, int duration) {
        this.frequencyThreshold = frequency;
        this.banDurationMinutes = duration;

        SecuritySetting freqSetting = new SecuritySetting();
        freqSetting.setSettingKey(KEY_FREQUENCY);
        freqSetting.setSettingValue(String.valueOf(frequency));
        settingRepository.save(freqSetting);

        SecuritySetting durSetting = new SecuritySetting();
        durSetting.setSettingKey(KEY_DURATION);
        durSetting.setSettingValue(String.valueOf(duration));
        settingRepository.save(durSetting);
        System.out.println("Updated security policy: Freq=" + frequency + ", Duration=" + duration);
    }

    public int getFrequencyThreshold() {
        return frequencyThreshold;
    }

    public int getBanDurationMinutes() {
        return banDurationMinutes;
    }
}