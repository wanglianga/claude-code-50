package cn.nightpharmacy.repo;

import cn.nightpharmacy.domain.OrderItem;
import cn.nightpharmacy.domain.DispenseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderOrderByIdAsc(DispenseOrder order);
}
