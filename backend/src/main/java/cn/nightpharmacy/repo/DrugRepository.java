package cn.nightpharmacy.repo;

import cn.nightpharmacy.domain.Drug;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DrugRepository extends JpaRepository<Drug, Long> {
    List<Drug> findByNameContaining(String name);
}
