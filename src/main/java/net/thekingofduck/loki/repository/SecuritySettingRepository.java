package net.thekingofduck.loki.repository;

import net.thekingofduck.loki.model.SecuritySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecuritySettingRepository extends JpaRepository<SecuritySetting, String> {}
