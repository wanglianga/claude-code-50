package cn.nightpharmacy.repo;

import cn.nightpharmacy.domain.DispenseOrder;
import cn.nightpharmacy.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<DispenseOrder, Long> {
    Optional<DispenseOrder> findByOrderNo(String orderNo);
    List<DispenseOrder> findAllByOrderByCreatedAtDesc();
    List<DispenseOrder> findByPatientOrderByCreatedAtDesc(User patient);
    long countByPharmacistAndStatus(User pharmacist, String status);
}
