package net.thekingofduck.ningan.repository;

import net.thekingofduck.ningan.model.BlockedIp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlockedIpRepository extends JpaRepository<BlockedIp, String> {}