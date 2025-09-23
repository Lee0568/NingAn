package net.thekingofduck.loki.repository;

import net.thekingofduck.loki.model.BlockedIp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlockedIpRepository extends JpaRepository<BlockedIp, String> {}