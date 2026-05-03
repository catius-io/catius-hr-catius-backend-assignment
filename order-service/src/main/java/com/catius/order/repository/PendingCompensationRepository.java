package com.catius.order.repository;

import com.catius.order.domain.PendingCompensation;
import com.catius.order.domain.PendingCompensationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PendingCompensationRepository extends JpaRepository<PendingCompensation, String> {

    List<PendingCompensation> findByStatusIn(List<PendingCompensationStatus> statuses);
}
