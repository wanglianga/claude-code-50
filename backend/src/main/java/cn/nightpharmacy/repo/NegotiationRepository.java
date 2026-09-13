package cn.nightpharmacy.repo;

import cn.nightpharmacy.domain.SubstitutionNegotiation;
import cn.nightpharmacy.domain.DispenseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NegotiationRepository extends JpaRepository<SubstitutionNegotiation, Long> {
    List<SubstitutionNegotiation> findByOrderOrderByCreatedAtAsc(DispenseOrder order);
    List<SubstitutionNegotiation> findByStatus(String status);
}
