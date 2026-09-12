package cn.nightpharmacy.repo;

import cn.nightpharmacy.domain.OrderEvent;
import cn.nightpharmacy.domain.DispenseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    List<OrderEvent> findByOrderOrderByCreatedAtAscIdAsc(DispenseOrder order);
}
