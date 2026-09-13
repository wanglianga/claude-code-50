package cn.nightpharmacy.repo;

import cn.nightpharmacy.domain.Pharmacy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PharmacyRepository extends JpaRepository<Pharmacy, Long> {
    List<Pharmacy> findByOpen24hTrueOrderByDistanceKmAsc();
}
