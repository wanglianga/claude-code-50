package cn.nightpharmacy.repo;

import cn.nightpharmacy.domain.PharmacistShift;
import cn.nightpharmacy.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<PharmacistShift, Long> {
    Optional<PharmacistShift> findFirstByPharmacistAndOnDutyTrue(User pharmacist);
    List<PharmacistShift> findByOnDutyTrue();
    List<PharmacistShift> findAllByOrderByStartedAtDesc();
}
